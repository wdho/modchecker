package com.li.modchecker;

import com.mojang.logging.LogUtils;
import io.netty.util.AttributeKey;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Builds the report for the exact login connection with a bounded, latest-wins work queue.
 * Only one {@link ReportJob} can run and at most one can wait; replaced jobs are cancelled.
 */
public final class ClientHashReporter {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AttributeKey<Boolean> REQUESTED = AttributeKey.valueOf("modchecker:report_requested");
    private static final BlockingQueue<ReportJob> QUEUE = new ArrayBlockingQueue<>(1);
    private static final AtomicReference<ReportJob> RUNNING = new AtomicReference<>();
    private static final Object WORKER_LOCK = new Object();
    private static final long WORKER_IDLE_SECONDS = 30L;
    private static final boolean LOG_LOGIN_PHASES = false;
    private static Thread workerThread;

    private ClientHashReporter() {
    }

    public static void onRequest(LoginRequestMessage request, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.setPacketHandled(true);
        Connection connection = context.getNetworkManager();
        if (connection == null) {
            return;
        }
        if (connection.channel().attr(REQUESTED).getAndSet(Boolean.TRUE) != null) {
            connection.disconnect(Component.literal("[ModChecker] Duplicate login report request"));
            return;
        }

        ReportJob job = new ReportJob(connection, context);
        ReportJob pending = QUEUE.poll();
        if (pending != null) {
            pending.cancel();
        }
        ReportJob running = RUNNING.get();
        if (running != null) {
            running.cancel();
        }
        if (!QUEUE.offer(job)) {
            job.cancel();
            connection.disconnect(Component.literal("[ModChecker] Report queue rejected the login report"));
            return;
        }
        if (LOG_LOGIN_PHASES) {
            LOGGER.info("[ModChecker] Login report request received; queueing report");
        }
        connection.channel().closeFuture().addListener(ignored -> {
            job.cancel();
            QUEUE.remove(job);
        });
        startWorker();
    }

    private static void startWorker() {
        synchronized (WORKER_LOCK) {
            if (workerThread != null) {
                return;
            }
            Thread worker = new Thread(ClientHashReporter::workerLoop, "ModChecker-HashReporter");
            worker.setDaemon(true);
            workerThread = worker;
            worker.start();
        }
    }

    private static void workerLoop() {
        Thread self = Thread.currentThread();
        try {
            while (true) {
                ReportJob job;
                try {
                    // Verification is login-only: stop the worker once the queue has been idle.
                    job = QUEUE.poll(WORKER_IDLE_SECONDS, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (job == null) {
                    synchronized (WORKER_LOCK) {
                        if (workerThread == self && QUEUE.isEmpty()) {
                            workerThread = null;
                            return;
                        }
                    }
                    continue;
                }
                RUNNING.set(job);
                try {
                    process(job);
                } catch (Throwable throwable) {
                    LOGGER.error("[ModChecker] Unexpected login report failure", throwable);
                    if (!job.isCancelled() && isLoginConnection(job.connection)) {
                        job.connection.disconnect(Component.literal("[ModChecker] Failed to build the login report"));
                    }
                } finally {
                    RUNNING.compareAndSet(job, null);
                }
            }
        } finally {
            // Only the current owner may clear the slot; a new worker may already own it.
            synchronized (WORKER_LOCK) {
                if (workerThread == self) {
                    workerThread = null;
                }
            }
        }
    }

    private static void process(ReportJob job) {
        if (job.isCancelled() || !isLoginConnection(job.connection)) {
            return;
        }
        if (LOG_LOGIN_PHASES) {
            LOGGER.info("[ModChecker] Building login report on worker thread");
        }
        HashReportMessage report;
        try {
            report = HashReportFactory.create(job::isCancelled);
        } catch (CancellationException cancelled) {
            return;
        } catch (Throwable failure) {
            LOGGER.error("[ModChecker] Failed to build the login report", failure);
            if (!job.isCancelled() && isLoginConnection(job.connection)) {
                job.connection.disconnect(Component.literal("[ModChecker] Failed to build the login report"));
            }
            return;
        }
        if (job.isCancelled() || !isLoginConnection(job.connection)) {
            return;
        }
        final HashReportMessage readyReport = report;
        // Login packets should be sent from the connection event loop, not the hashing thread.
        try {
            job.connection.channel().eventLoop().execute(() -> {
                if (job.isCancelled() || !isLoginConnection(job.connection)) {
                    return;
                }
                try {
                    ModCheckerNetwork.CHANNEL.reply(readyReport, job.context);
                    LOGGER.info("[ModChecker] Sent login report for {} mods", readyReport.mods.size());
                } catch (RuntimeException exception) {
                    LOGGER.error("[ModChecker] Failed to send the login report", exception);
                    if (isLoginConnection(job.connection)) {
                        job.connection.disconnect(Component.literal("[ModChecker] Failed to send the login report"));
                    }
                }
            });
        } catch (RuntimeException exception) {
            LOGGER.error("[ModChecker] Failed to schedule the login report", exception);
            if (isLoginConnection(job.connection)) {
                job.connection.disconnect(Component.literal("[ModChecker] Failed to send the login report"));
            }
        }
    }

    private static boolean isLoginConnection(Connection connection) {
        return connection.isConnected()
                && connection.channel().attr(Connection.ATTRIBUTE_PROTOCOL).get() == ConnectionProtocol.LOGIN;
    }

    private static final class ReportJob {
        private final Connection connection;
        private final NetworkEvent.Context context;
        private final AtomicBoolean cancelled = new AtomicBoolean();

        private ReportJob(Connection connection, NetworkEvent.Context context) {
            this.connection = connection;
            this.context = context;
        }

        private void cancel() {
            cancelled.set(true);
        }

        private boolean isCancelled() {
            return cancelled.get();
        }
    }
}
