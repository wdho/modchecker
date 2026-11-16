package com.li.modchecker;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.netty.util.AttributeKey;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraftforge.event.entity.player.PlayerNegotiationEvent;
import net.minecraftforge.network.ConnectionData;
import net.minecraftforge.network.HandshakeHandler;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** A connection-bound Forge negotiation gate. No player can enter the world while it is pending. */
public final class HashVerificationManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AttributeKey<LoginSession> SESSION = AttributeKey.valueOf("modchecker:login_session");
    private static final Set<Connection> PENDING = ConcurrentHashMap.newKeySet();
    private static volatile long currentTick;

    private HashVerificationManager() {
    }

    private enum Phase { WAITING, PROCESSING, VERIFIED, CLOSED }

    private static final class LoginSession {
        final GameProfile profile;
        final long handshakeDeadline;
        final int reportTimeoutTicks;
        final long totalDeadline;
        final boolean enforce;
        final CompletableFuture<Void> gate = new CompletableFuture<>();
        private final long createdNanos = System.nanoTime();
        private long forgeDataReadyNanos;
        private long reportReceivedNanos;
        private long reportDeadline;
        private Phase phase = Phase.WAITING;
        private HashReportMessage acceptedReport;
        private volatile HashReportMessage candidate;
        private volatile Supplier<NetworkEvent.Context> responseContext;

        LoginSession(GameProfile profile, long handshakeDeadline, int reportTimeoutTicks,
                    long totalDeadline, boolean enforce) {
            this.profile = profile;
            this.handshakeDeadline = handshakeDeadline;
            this.reportTimeoutTicks = Math.max(20, reportTimeoutTicks);
            this.totalDeadline = totalDeadline;
            this.enforce = enforce;
        }

        /** The report clock starts only after the Forge mod list is available. */
        synchronized void markForgeDataReady(long tick) {
            if (reportDeadline == 0L) {
                // Never let the report phase outlive the overall LOGIN budget.
                reportDeadline = Math.min(tick + reportTimeoutTicks, totalDeadline);
                forgeDataReadyNanos = System.nanoTime();
                if (CheckerConfig.logLoginPhases()) {
                    LOGGER.info("[ModChecker] Forge login data ready for {} after {} ms",
                            profile.getName(), elapsedMillis());
                }
            }
        }

        synchronized void markReportReceived() {
            if (reportReceivedNanos == 0L) {
                reportReceivedNanos = System.nanoTime();
            }
        }

        long elapsedMillis() {
            return (System.nanoTime() - createdNanos) / 1_000_000L;
        }

        synchronized boolean handshakeExpired(long tick) {
            return reportDeadline == 0L && tick >= handshakeDeadline;
        }

        synchronized boolean reportExpired(long tick) {
            return reportDeadline != 0L && tick >= reportDeadline;
        }

        synchronized boolean beginResponse(long tick) {
            if (phase != Phase.WAITING || tick >= totalDeadline) {
                return false;
            }
            long deadline = reportDeadline == 0L ? handshakeDeadline : reportDeadline;
            if (tick >= deadline) {
                return false;
            }
            phase = Phase.PROCESSING;
            return true;
        }

        synchronized boolean accept(HashReportMessage report) {
            if (phase != Phase.PROCESSING) {
                return false;
            }
            acceptedReport = report;
            candidate = null;
            responseContext = null;
            phase = Phase.VERIFIED;
            gate.complete(null);
            return true;
        }

        synchronized HashReportMessage report() {
            return phase == Phase.VERIFIED ? acceptedReport : null;
        }

        synchronized void reject() {
            phase = Phase.CLOSED;
            acceptedReport = null;
            candidate = null;
            responseContext = null;
        }

        synchronized void close() {
            reject();
            gate.complete(null);
        }
    }

    public static void onNegotiation(PlayerNegotiationEvent event) {
        Connection connection = event.getConnection();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            reject(connection, "[ModChecker] Server is not ready for login verification");
            return;
        }
        GameProfile profile = event.getProfile();
        if (profile.getId() == null) {
            profile = new GameProfile(UUIDUtil.getOrCreatePlayerUUID(profile), profile.getName());
        }
        boolean dedicated = server.isDedicatedServer();
        // The host's own singleplayer connection is a memory connection and must never be
        // blocked by LAN policy, otherwise opening the world would lock the host out.
        boolean localHost = !dedicated && connection.isMemoryConnection();
        // Integrated/LAN worlds (PCL LAN, EasyTier, tunneled LAN) must enforce for remote players.
        // The tunnel is transport-transparent; this only changes the policy target.
        boolean lanEnforced = !dedicated && !localHost && CheckerConfig.lanEnforcement();
        boolean enforce = !localHost
                && (!(CheckerConfig.onlyDedicatedServer() && !dedicated) || lanEnforced)
                && !(CheckerConfig.exemptOps() && server.getProfilePermissions(profile) >= 2);
        if (pendingLimitReached(PENDING.size())) {
            reject(connection, CheckerConfig.handshakeBusyMessage());
            return;
        }
        long totalDeadline = currentTick + Math.max(100, CheckerConfig.totalLoginTimeoutTicks());
        long handshakeDeadline = Math.min(
                currentTick + Math.max(20, CheckerConfig.handshakeTimeoutTicks()),
                totalDeadline);
        LoginSession session = new LoginSession(profile, handshakeDeadline,
                CheckerConfig.hashTimeoutTicks(), totalDeadline, enforce);
        if (!connection.channel().attr(SESSION).compareAndSet(null, session)) {
            reject(connection, "[ModChecker] Duplicate login negotiation");
            return;
        }
        PENDING.add(connection);
        if (CheckerConfig.logLoginPhases()) {
            LOGGER.info("[ModChecker] Login negotiation started for {} (pending={})",
                    profile.getName(), PENDING.size());
        }
        // Forge cannot complete login even if another handshake acknowledgement arrives first.
        event.enqueueWork(session.gate);
        connection.channel().closeFuture().addListener(ignored -> clear(connection));
        LOGGER.debug("[ModChecker] Waiting for login report from {}", event.getProfile().getName());
    }

    /** Package-private boundary helper used by the fixed round-2 tests. */
    static boolean pendingLimitReached(int pendingCount) {
        return pendingCount >= Math.max(1, CheckerConfig.maxPendingLogins());
    }

    public static HashReportMessage report(ServerPlayer player) {
        if (player == null || player.connection == null) {
            return null;
        }
        LoginSession session = player.connection.connection.channel().attr(SESSION).get();
        // In offline login Forge may not assign a UUID until negotiation has finished.
        // The live Connection is the identity boundary; never fall back to a UUID-keyed cache.
        return session == null ? null : session.report();
    }

    /** Called on the server thread. Acknowledge Forge only after all checks have succeeded. */
    public static void onReport(HashReportMessage report, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        Connection connection = context.getNetworkManager();
        LoginSession session = connection.channel().attr(SESSION).get();
        if (session != null && CheckerConfig.logLoginPhases()) {
            session.markReportReceived();
            LOGGER.info("[ModChecker] Login report received for {} after {} ms",
                    session.profile.getName(), session.elapsedMillis());
        }
        if (!(connection.getPacketListener() instanceof ServerLoginPacketListenerImpl)
                || connection.channel().attr(Connection.ATTRIBUTE_PROTOCOL).get() != ConnectionProtocol.LOGIN
                || session == null || !session.beginResponse(currentTick)) {
            reject(connection, "[ModChecker] Unexpected, expired or duplicate login report");
            return;
        }
        try {
            if (report.getAsInt() < 0 || !HandshakeHandler.packetNeedsResponse(connection, report.getAsInt())) {
                reject(connection, "[ModChecker] Report does not answer a pending login request");
                return;
            }
            session.candidate = report;
            session.responseContext = supplier;
            verifyWhenForgeDataIsReady(connection, session);
        } catch (RuntimeException exception) {
            LOGGER.warn("[ModChecker] Rejecting malformed login report for {}", session.profile.getName(), exception);
            reject(connection, "[ModChecker] Invalid login report or server manifest");
        }
    }

    private static void verifyWhenForgeDataIsReady(Connection connection, LoginSession session) {
        HashReportMessage report = session.candidate;
        try {
            ConnectionData data = NetworkHooks.getConnectionData(connection);
            if (data == null) {
                // Channel gathering order is unspecified. Keep the gate closed until FML supplies its mod list.
                return;
            }
            // The report-response clock starts when the Forge handshake data is ready,
            // even if the client's report has not arrived yet.
            session.markForgeDataReady(currentTick);
            if (report == null) {
                return;
            }
            if (!HandshakeHandler.packetNeedsResponse(connection, report.getAsInt())) {
                reject(connection, "[ModChecker] Login request was acknowledged without verification");
                return;
            }
            if (!new LinkedHashSet<>(data.getModList()).equals(report.mods.keySet())) {
                reject(connection, "[ModChecker] Report mod list differs from the Forge login data");
                return;
            }
            if (session.enforce) {
                LoginChecker.CheckResult result = LoginChecker.checkWithReport(data, report);
                if (result.hasFailure()) {
                    if (CheckerConfig.logRejections()) {
                        LOGGER.warn("[ModChecker] Login rejected for {}: configuration={} banned={} missing={} extra={} versions={} existingFingerprintMismatches={}",
                                session.profile.getName(), result.configurationErrors, result.banned,
                                result.missing, result.extra, result.versionMismatches, result.hashMismatches);
                    }
                    reject(connection, LoginChecker.formatFailure(result, session.profile.getName()));
                    return;
                }
            }
            HandshakeHandler.<HashReportMessage>indexFirst((handler, message, contextSupplier) ->
                    contextSupplier.get().setPacketHandled(true)).accept(report, session.responseContext);
            if (!session.accept(report)) {
                reject(connection, "[ModChecker] Login session closed during verification");
                return;
            }
            PENDING.remove(connection);
            LOGGER.info("[ModChecker] Login report accepted for {}: {} mods ({} ms)",
                    session.profile.getName(), report.mods.size(), session.elapsedMillis());
        } catch (RuntimeException exception) {
            LOGGER.warn("[ModChecker] Rejecting malformed login report for {}", session.profile.getName(), exception);
            reject(connection, "[ModChecker] Invalid login report or server manifest");
        }
    }

    public static void tick(MinecraftServer server) {
        currentTick++;
        for (Connection connection : PENDING) {
            LoginSession session = connection.channel().attr(SESSION).get();
            if (session == null || !connection.isConnected()) {
                clear(connection);
            } else if (session.reportExpired(currentTick)) {
                reject(connection, CheckerConfig.hashTimeoutMessage().replace("%player%", session.profile.getName()));
            } else if (session.handshakeExpired(currentTick)) {
                reject(connection, CheckerConfig.handshakeTimeoutMessage().replace("%player%", session.profile.getName()));
            } else {
                verifyWhenForgeDataIsReady(connection, session);
            }
        }
    }

    public static void onLogout(ServerPlayer player) {
        if (player != null && player.connection != null) {
            clear(player.connection.connection);
        }
    }

    static void reject(Connection connection, String message) {
        // Send the LOGIN disconnect before resolving the gate; never allow a failed connection into PLAY.
        LoginSession session = connection.channel().attr(SESSION).get();
        if (session != null) {
            session.reject();
        }
        if (connection.isConnected()) {
            Component reason = Component.literal(message);
            if (connection.getPacketListener() instanceof ServerLoginPacketListenerImpl login) {
                login.disconnect(reason);
            } else {
                connection.disconnect(reason);
            }
        }
        if (!connection.isConnected()) {
            clear(connection);
        }
    }

    private static void clear(Connection connection) {
        PENDING.remove(connection);
        LoginSession session = connection.channel().attr(SESSION).getAndSet(null);
        if (session != null) {
            session.close();
        }
    }

    public static void clearAll() {
        for (Connection connection : PENDING) {
            clear(connection);
        }
        PENDING.clear();
        currentTick = 0;
    }
}
