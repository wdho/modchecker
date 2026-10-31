# Mod Checker 1.2.5 — Forge 1.20.1（通用 JarJar 物理文件哈希）

在 Forge LOGIN 协商阶段检查客户端模组信息，通过后才进入世界。本次安全迭代使用 **协议 2**，客户端与服务端必须一起安装新版；不提供协议 1 或无组件回退。

本模组管理整合包一致性。握手列表和文件报告由客户端提供，不能证明游戏进程没有注入，也不能替代服务端对玩法、物品和自定义报文的权限检查。

## 1.2.5 通用 JarJar 物理文件解析

- 不再只对 CustomSkinLoader 做特殊处理。
- 对任意 mod 的 owning file 做物理文件回溯：如果是运行时生成/解压/虚拟路径，就拿 `mods` 下包含该 mod 的顶层 jar 哈希。
- 顶层 jar 的 Fabric/Forge 元数据、嵌套 JarJar、目录 mod 都会建立 modId → 顶层文件索引。
- CustomSkinLoader 实际路径测试：Common jar 成功解析到外层 `CustomSkinLoader_Universal-15.1.jar`（约 612ms）。
- 旧清单仍记录 Common jar 哈希，必须重新生成。

## 1.2.4 CustomSkinLoader 稳定哈希修复

- CustomSkinLoader 的 Bootstrap 会把嵌套 Common jar 解压/重映射到 `CustomSkinLoader/Core/CustomSkinLoader-Common.jar`。
- ModChecker 之前取的是 Forge 的 owning file，可能正好指向这个运行时生成的 Common jar；同一 CSL 版本在不同启动/机器上字节可能不同，导致误报哈希不一致。
- 1.2.4 对 `customskinloader` 做特殊处理：检测到 Common jar 时，改哈希 `mods` 下的外层 `CustomSkinLoader_Universal-*.jar`。
- 旧清单仍记录 Common jar 哈希，升级后必须重新生成 manifest/hashes。
- 手动测试：CSL_HASH_PATH_TEST PASS。

## 1.2.3 白名单可选模组（缺失放行，存在仍校验）

- 白名单里的 mod 视为“可选”：客户端**缺少**它时不再报 missing。
- 如果客户端**有**这个 mod，仍会正常比较清单、哈希和频道；版本/哈希不同仍会被拒绝。
- 只有“缺少”放行；不是“存在就跳过校验”。
- bannedMods 仍然优先。
- 新增 `whitelistAllowsMissing`（默认 true）；设为 false 恢复旧的“只允许 extra”语义。

## 1.2.1 配置目录排除与升级修复

- 目录形式的模组指纹不再包含其 `config/` / `configs/` 子目录；修改配置不会改变该模组哈希。
- 旧 1.1.x 的 `modchecker-hashes.json` 中仍可能有旧的 modchecker / CustomSkinLoader 哈希；升级 1.2.x 后必须重新生成。
- 旧配置里的 `checkCompanionHash=true` 不会因默认值改变而自动变 false，需手动改为 false（完整服务端 jar 和 client-only jar 字节不同）。
- 常见拒绝：expected modchecker 是旧 1.1.5 jar 的哈希、client 是新 jar；expected customskinloader 是旧皮肤 jar。先对齐/忽略皮肤模组，再重新生成清单。

## 1.2.0 服务端/客户端拆分

- 服务端完整功能：准入、清单/哈希、白名单、命令、LAN/内网穿透策略，全部在 `modchecker-1.20.1-1.2.5.jar`。
- 普通客户端只装 `modchecker-client-1.20.1-1.2.5.jar`：只计算并发送登录报告，不含命令、清单、白名单或服务端策略。
- 完整服务端 jar **也可以安装在客户端**，用于管理员生成清单、开“对局域网开放”/内网穿透主机；此时不要再装 client-only jar（同一个 modId，不能并存）。
- `checkCompanionHash` 默认改为 false：完整服务端 jar 和 client-only jar 字节不同，不能要求两端 jar 哈希一致。
- 普通玩家只需要 client-only jar；管理员/主机客户端可以用完整 jar 代替。

## 1.1.10 白名单 Tab 显示全部模组 ID

- `/modchecker whitelist add ` 按 Tab 时，列出服务端/主机已加载的 mod ID 以及清单中的客户端 mod ID，不再只显示“尚未在清单里的额外模组”。
- 仍然过滤掉 bannedMods 和已经写入白名单的 ID。
- 建议列表来自 Forge 已加载的 mods 目录；仅客户端存在、服务端未加载的额外模组不会出现，需直接填 ID。
- 中文显示名在部分客户端输入环境仍不稳定，建议直接用 Tab 选 ID。

## 1.1.9 中文模组名称输入

- `/modchecker whitelist add` 的参数从单个词改为 greedy string，支持中文显示名、空格、括号和带中文的 jar 文件名。
- 解析顺序：合法小写 mod ID → 已加载 jar 文件名 → 唯一显示名；最终写入白名单的仍然是 mod ID。
- Tab 补齐会同时提示 mod ID、jar 文件名和显示名（包括中文）；仅有客户端、服务端未加载的模组仍需直接填 ID。

## 1.1.8 白名单 Tab 补齐与名称解析

- `/modchecker whitelist add ` 后按 Tab，会提示当前可加入的 mod ID 和已加载 jar 文件名。
- 输入已加载 mod 的 jar 文件名或唯一显示名时，自动解析为实际 mod ID，再写入白名单；文件里仍然保存 ID。
- 已存在于清单、bannedMods 或白名单中的条目不会作为建议；没有可用建议时 Tab 为空是正常的。
- 名称解析依赖该 mod 已在服务端/主机加载；仅客户端有的额外模组在独立服务端上无法按文件名解析，请直接填 mod ID。

## 1.1.7 白名单添加指令

- 新增 `/modchecker whitelist add <modid>`：校验小写 mod ID、写入 whitelist 文件并自动重载。
- 已存在的 ID 会提示重复；bannedMods 中的 ID 会被拒绝；jar 文件名、显示名和带引号名称会被拒绝。
- 命令需要权限等级 2；主机单人模式如果没开作弊，可能无法执行，直接编辑 config 文件即可。
- 它只允许“额外客户端模组”，不能绕过缺少必需模组、哈希不一致或 bannedMods。

## 1.1.6 主机单人模式保护

- 修复 1.1.5 在“对局域网开放”时把主机自己的内存连接也纳入 LAN 策略，导致主机被 CustomSkinLoader 等哈希差异挡住、单人模式都进不去的问题。
- 主机本地连接（Connection.isMemoryConnection）现在永远不执行内容策略；远程 LAN/内网穿透玩家仍按 lanEnforcement 检查。
- 这不改变协议、清单格式或远程玩家策略。

## 1.1.5 局域网/内网穿透准入

- 新增 `lanEnforcement`（默认 true）：在 PCL“对局域网开放”、EasyTier 或内网穿透的集成服务器上，也对远程玩家执行模组策略。
- 之前的根因不是穿透地址不匹配，而是集成服务器默认被 `onlyDedicatedServer=true` 跳过；隧道本身是透明转发，不需要地址匹配。
- 主机自己和远程玩家都必须使用 1.1.5；远程玩家不装组件会被拒绝。
- 如果不想在局域网世界强制，设 `lanEnforcement=false`。

## 1.1.4 线程生命周期修复

- 修复客户端报告线程空闲退出时的所有权竞争：使用 workerThread 拥有者并在 finally 中只清理自己；旧线程不能覆盖新线程状态。
- 不改变登录协议、超时或功能；新增验证见稳定性结果。

## 1.1.3 稳定性改进

- **登录回复走连接 event loop**：客户端报告构建完成后，在 Netty 连接线程回复登录包，避免工作线程直接发送。
- **验证后停止**：客户端报告线程空闲 30 秒后自动退出；下次登录请求再懒启动。
- **登录阶段日志**：新增 `logLoginPhases`（默认 false），记录协商开始、Forge 数据就绪、收到报告、通过进服的耗时。
- **只检查新登录/重连**：服务端只维护 PENDING 登录会话；玩家进入世界后没有周期检查。

## 1.1.2 稳定性修复

- **拆分超时**：`handshakeTimeoutTicks` 只等待 Forge 登录数据；`hashTimeoutTicks` 只在数据就绪后开始，避免大整合包在发送报告请求前耗尽报告预算。
- **服务器资源保护**：`maxPendingLogins` 限制同时等待校验的连接数，超限在 LOGIN 阶段直接拒绝。
- **客户端任务保护**：单线程报告任务；断线/新连接取消旧任务；哈希读取每 64 KiB 检查取消标志。
- **测试修复**：隔离服脚本不再写 UTF-8 BOM；安全脚本不再被 javac 的 stderr 注解提示中断。
- 详细复核材料见 [GPT复审-1.1.2.md](GPT复审-1.1.2.md)，开服配置见 [正式开服配置检查.md](正式开服配置检查.md)。

## 登录流程

1. Forge 开始协商，服务端建立绑定当前 Connection 的准入状态和未完成任务。
2. 服务端通过 LOGIN 方向请求一次报告，客户端在后台调用原有报告生成逻辑。
3. 服务端确认报告 ID 集合与当前 Forge 握手集合一致，并执行内容策略。
4. 通过后保存不可变报告快照，确认握手并完成准入任务，再允许进入世界。
5. 非法、重复、超时或失败报告在登录阶段拒绝；PLAY 阶段没有报告接收入口。

等待期间不能先在世界内操作。报告跟随连接保存，重连不会按 UUID 继承旧连接报告。客户端后台任务也只向发起请求的原连接回复。

## 配置与清单

- `config/modchecker-common.toml`：检查开关、提示语、分离的超时和待校验连接上限。
- `config/modchecker-manifest.json`：模组 ID、适用端与频道版本。
- `config/modchecker-hashes.json`：原有文件报告清单。
- `config/modchecker-whitelist.txt`：允许额外安装的 **小写 mod ID**，一行一个。

白名单示例：

```text
# 使用实际 mod ID，不能写显示名
jei
xaerominimap
```

显示名、带引号的名称、非法 ID 和重复 ID 会使整份白名单加载失败。黑名单优先于白名单；白名单不能允许缺少必需模组，也不能跳过组件自身原有检查。

已经存在但损坏的任意清单或白名单都会阻止新的内容校验登录。首次部署尚未创建的两份清单仍由原有 `requireManifest`、`requireHashManifest` 控制，默认仍为 `false`，便于管理员先进入生成。策略豁免仍按配置生效。

JSON 清单需要显式格式编号、正确容器和合法条目。空清单、null 项、重复 ID/JSON 属性、非法端和频道会被拒绝。缺少清单要求的频道也记录异常；`ignoredMods` 仍按原有配置排除对应检查。

- `handshakeTimeoutTicks`（默认 400）：等待 Forge 登录数据就绪的时限；
- `hashTimeoutTicks`（默认 400）：Forge 数据就绪后才开始计时的报告响应时限；
- `logLoginPhases`（默认 false）：记录登录各阶段与耗时，便于诊断握手卡顿；不记录令牌；
- `totalLoginTimeoutTicks`（默认 500）：Mod Checker 协商阶段的共享总预算，留出约 100 tick 给认证和其他登录处理；
- `maxPendingLogins`（默认 64）：同时等待校验的连接上限；
- `lanEnforcement`（默认 true）：对 PCL 对局域网开放、EasyTier、内网穿透的集成服务器也执行内容策略；
- `handshakeTimeoutMessage` / `handshakeBusyMessage`：对应阶段的提示语。

原版登录阶段自身约有 600 tick（30 秒）总超时；报告截止时间会被 `totalLoginTimeoutTicks` 截断，不会超出总预算。清单生成先写同目录临时文件、备份旧文件为 `.bak`，再原子替换；不支持原子替换时明确失败，不降级截断覆盖。

## 升级

- 服务端使用完整版：`modchecker-1.20.1-1.2.5.jar`。
- 普通客户端使用：`modchecker-client-1.20.1-1.2.5.jar`。
- 管理员/主机客户端可以使用完整版代替 client-only jar；**两个 jar 不能同时装**，它们使用同一个 modId/channel。
- 装有本组件的客户端也要求服务端提供协议 2；连接其他未装组件的服务器应使用独立实例。
- 删除原 `requireCompanion=false` 无组件退路；该旧选项不再定义。
- `onlyDedicatedServer`、`exemptOps` 只控制内容策略，不放宽协议要求。豁免玩家也完成报告交换与握手集合匹配。
- 旧白名单显示名须由管理员改成准确 ID，再 reload；不自动猜测授权对象。
- 更新组件也改变原有文件清单中的组件文件。管理员需安排维护流程处理旧基准并重新生成，不能依赖自动忽略差异的兼容路径。
- 旧 `run-server` 的世界、配置和 RCON 设置不会由本次源码迭代代改；正式开服前按 [正式开服配置检查.md](正式开服配置检查.md) 调整。

## 管理命令

全部要求权限等级 2。控制台在生成/检查命令后指定玩家，玩家执行可省略目标。

| 命令 | 用途 |
|---|---|
| `/modchecker status` | 查看清单、白名单、协议与加载错误 |
| `/modchecker generate [player]` | 从当前握手生成模组清单 |
| `/modchecker generate-hashes [player]` | 从当前连接已验证报告生成原有文件清单 |
| `/modchecker reload` | 重载两份清单和 ID 白名单；失败明确报错 |
| `/modchecker mods [player]` | 查看客户端 ID 和报告状态 |
| `/modchecker check [player]` | 依当前配置诊断当前连接 |
| `/modchecker whitelist` | 查看成功加载的 ID 白名单 |
| `/modchecker whitelist add <名称>` | 接受小写 ID、已加载 jar 文件名或显示名（支持中文/空格）；解析为 ID 后写入；支持 Tab |

## 构建与测试

JDK 17、Gradle 8.8、Forge 1.20.1-47.4.0。已缓存依赖时运行：

```powershell
.\gradlew.bat --offline --no-daemon build --warning-mode all
```

`jar` 后执行 `reobfJar`，交付正式 Forge 可加载的重映射包。原有回归清单固定 **24 项**（19 unit + 5 integration），本轮新增第二轮固定 **7 项**，见 `test/security/TEST-PLAN.md` 与 `test/security/ROUND2-TEST-PLAN.md`。

本次没有新增兼容层、摘要算法或哈希比对功能；原有摘要算法保留，报告生成缓存只在单次调用内使用。该交付不含专用启动器、独立反作弊或通用移动/战斗检测。

## 已知取舍

- 白名单只接受小写 mod ID，不支持直接写显示名（安全上避免客户端伪造显示名）；若需要全称请联系维护者做安全映射。
- 本组件客户端当前只接受协议 2 服务端；连接原版/其他未装组件服务器需使用独立实例，或修改客户端频道谓词。

## 产物

- 服务端完整版：`dist/modchecker-1.20.1-1.2.5.jar`（82918 bytes）
- 服务端 SHA-256：`F2EC944F3C0D0B94255658DFAF0DDFEECCC2E1224E132C5558C06FFA878D0C95`
- 客户端精简版：`dist/modchecker-client-1.20.1-1.2.5.jar`（25745 bytes）
- 客户端 SHA-256：`F20EDB7E75A9ADB7ED96098CE4B6605752F673DB3203634B2F1D4B12F3D0771A`
- 1.2.5 通用 JarJar 物理文件哈希：[最终交付-1.2.5.md](最终交付-1.2.5.md)
- 1.2.4 CSL 稳定哈希修复：[最终交付-1.2.4.md](最终交付-1.2.4.md)
- 1.2.3 白名单语义修正：[最终交付-1.2.3.md](最终交付-1.2.3.md)
- 1.2.2 白名单可选模组：[最终交付-1.2.2.md](最终交付-1.2.2.md)
- 1.2.1 配置排除/升级修复：[最终交付-1.2.1.md](最终交付-1.2.1.md)
- 1.2.0 双端拆分：[最终交付-1.2.0.md](最终交付-1.2.0.md)
- 1.1.10 Tab ID 列表修正：[最终交付-1.1.10.md](最终交付-1.1.10.md)
- 1.1.9 中文名称输入修复：[最终交付-1.1.9.md](最终交付-1.1.9.md)
- 1.1.8 Tab 补齐/名称解析：[最终交付-1.1.8.md](最终交付-1.1.8.md)
- 1.1.7 白名单指令说明：[最终交付-1.1.7.md](最终交付-1.1.7.md)
- 1.1.6 主机单人模式修复：[最终交付-1.1.6.md](最终交付-1.1.6.md)
- 1.1.5 局域网/内网穿透说明：[最终交付-1.1.5.md](最终交付-1.1.5.md)
- 稳定性验证结果（1.1.4 修复记录）：[稳定性验证结果-1.1.4.md](稳定性验证结果-1.1.4.md)
- 1.1.3 五次冷启动记录：[稳定性验证结果-1.1.3.md](稳定性验证结果-1.1.3.md)
- 生产客户端验证结果（含真实整合包/正版账号）：[生产客户端验证结果-1.1.2.md](生产客户端验证结果-1.1.2.md)
- GPT 生产实测复核：[GPT复审-生产客户端实测复核.md](GPT复审-生产客户端实测复核.md)
- 1.1.1 真实环境尝试记录：[test/evidence/REAL-ENV-1.1.2.md](test/evidence/REAL-ENV-1.1.2.md)
- 测试结果：[test/security/results/](test/security/results/)
- 二轮复核材料：[GPT复审-1.1.2.md](GPT复审-1.1.2.md)

## 验证状态

- 原有 24 项：19/19 unit + 5/5 integration 通过；
- 第二轮 7 项：7/7 通过；
- 真实开发客户端断线重连：连续两次 `Login report accepted` → `joined the game`；
- 真实生产客户端（超现代优化 + 正版账号）正常进服、生成清单和带清单重连：见 [生产客户端验证结果-1.1.2.md](生产客户端验证结果-1.1.2.md)（保留日志不覆盖在线首连，证据不完整）；
- 实际 Tweakeroo ID 在最小客户端隔离配置中**单独**命中 `banned=[tweakeroo]` 并被断开；完整标准包 + 仅加 Tweakeroo/MaLiLib 的干净对照仍未完成；
- 真实生产客户端白名单额外 mod 放行：通过；
- 1.2.5 通用 JarJar/运行时文件回溯到 `mods` 顶层 jar；实际 CSL 路径测试通过；19+7 通过；
- 1.2.4 CSL：改用外层 Universal jar 哈希，避免 Bootstrap Common jar 误报；19+7 + 手工 CSL 测试通过；
- 1.2.3 白名单：缺少放行，存在仍校验哈希/频道；自动测试 19+7 + 手工测试通过；
- 1.2.2 白名单可选模组：客户端缺少白名单 mod 也允许进服；自动测试 19+7 通过；
- 1.2.1 目录模组哈希排除 config/configs；R04 增加配置变更不改变指纹断言；19+7 通过；
- 1.2.0 服务端完整 jar + 客户端精简 jar 拆分；两端 build 成功，服务端 19+7 自动测试通过；
- 1.1.10 白名单 Tab 显示全部已加载/清单模组 ID；自动测试 19+7 通过；
- 1.1.9 白名单 add 支持中文显示名/空格/括号和中文 jar 文件名；自动测试 19+7 通过；
- 1.1.8 新增白名单 Tab 补齐和已加载 jar 文件名解析；自动测试 19+5+7 通过；
- 1.1.7 新增白名单 add 指令，手工持久化测试通过；自动测试 19+5+7 通过；
- 1.1.6 修复主机本地单人连接被 LAN 策略误拦；自动测试 19+5+7 通过；PCL 集成服务器端到端仍未实测；
- 1.1.5 新增集成服务器/LAN 准入（lanEnforcement），自动测试 19+5+7 通过；PCL 局域网/内网穿透端到端尚未实测；
- 1.1.4 修复 GPT 指出的工作线程退出竞争；1.1.4 一次真实客户端冷启动通过；同进程空闲后重连仍未完成；
- 1.1.3 连续 5 次真实生产客户端冷启动登录：5/5 成功，登录阶段约 1.1 秒；客户端验证线程空闲后退出；服务端进服后无 ModChecker 周期检查；
- 真实并发连接上限、完整标准包 + Tweakeroo 的干净对照仍未验收。

---

> 本仓库为私有仓库。未经作者书面许可，禁止复制、发布、再分发或公开源码。


---

> 本仓库为私有仓库。未经作者书面许可，禁止复制、发布、再分发或公开源码。

## AI 声明 / AI Notice

本项目的代码、文档与测试由 AI 辅助生成/修改（AI-assisted）。发布者已进行构建与实机验证；内容按“现状”提供，不保证适用性，使用前请自行评估。

This project was built with AI assistance (AI-assisted). Code, docs and tests may include AI-generated content. Provided "as is"; evaluate before use.

GitHub topics: `ai-assisted`.
