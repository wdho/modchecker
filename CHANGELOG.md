# Changelog

本仓库记录 Mod Checker for Minecraft 1.20.1 / Forge 的迭代版本。
每个版本在 `versions/<版本>/` 下有 jar、SHA-256 和该版本变更说明；Git tag 为 `v<版本>`。

| 版本 | 标题 | jar | SHA-256 |
|---|---|---|---|
| 1.0.0 | 初始版本 | dist/modchecker-1.20.1-1.0.0.jar | 40B8B7BC3B9873C709B6432767F8720AD6BF7DF160F3A5206977C3605FA93D1C |
| 1.1.0 | LOGIN 阶段安全迭代 | dist/modchecker-1.20.1-1.1.0.jar | 3ABF33B53158FAC4C06BEB90B62848A861FB9DF1A4BC84D768F6A7E666E610C0 |
| 1.1.1 | 超时、资源与任务生命周期修正 | dist/modchecker-1.20.1-1.1.1.jar | DD566268C5D4C866733E98985DBBF44659C16BA53DBDD19C069EEB73D5130C94 |
| 1.1.2 | GPT 复审返修 | dist/modchecker-1.20.1-1.1.2.jar | 8D6FEB0E2C602F78BC7E2393C2AD73BACA4D80B95D2A183E2EB4AABC598A6C87 |
| 1.1.3 | 登录稳定性改进 | dist/modchecker-1.20.1-1.1.3.jar | EAC4F62C7D8A3BB003579D9BD5F7B6994F93E70DA7620C986BD70B6D9214086A |
| 1.1.4 | 客户端工作线程所有权修复 | dist/modchecker-1.20.1-1.1.4.jar | 61E29E25037F2AC9CA693E4F72A8FEF0E9FE530133548C15672779ED3942CF62 |
| 1.1.5 | 局域网 / 内网穿透准入 | dist/modchecker-1.20.1-1.1.5.jar | 24C47EA9B8AE8C81B011AFE9816AEC633640638060CCE0F7BD2F39C7366F0EF8 |
| 1.1.6 | 主机单人模式保护 | dist/modchecker-1.20.1-1.1.6.jar | B26CBCAFD29C5654FF81E46CBA328630DFBA6357C74AEE396211D8DB2BAD8FE2 |
| 1.1.7 | 白名单添加指令 | dist/modchecker-1.20.1-1.1.7.jar | AD6DEC20F942390D7106A0EA8E36F3B0A3EA79DC19073752447CCE42F4CB1155 |
| 1.1.8 | 白名单 Tab 补齐与名称解析 | dist/modchecker-1.20.1-1.1.8.jar | E1871A2CFB20351C7375AB35E1700516E14B8E2E8879F626E1DF3401C273DEDE |
| 1.1.9 | 中文模组名称输入修复 | dist/modchecker-1.20.1-1.1.9.jar | 36BA719508E439B0601FCFF2DA4BF8D23285FB01FB9B04C7C8F9868AAA62DEDD |
| 1.1.10 | 白名单 Tab 显示全部模组 ID | dist/modchecker-1.20.1-1.1.10.jar | 6C6AFB783FCBE77246BCC0911B23CFEF9A26B600198D6AD76E561F37B13DD3AE |
| 1.2.0 | 服务端/客户端拆分 | dist/modchecker-1.20.1-1.2.0.jar + dist/modchecker-client-1.20.1-1.2.0.jar | server 7F23BEBF... / client 6CC91981... |
| 1.2.1 | 目录 config 哈希排除与升级修复 | dist/modchecker-1.20.1-1.2.1.jar + dist/modchecker-client-1.20.1-1.2.1.jar | server D64349BA... / client B321FD1D... |
| 1.2.2 | 白名单可选模组 | dist/modchecker-1.20.1-1.2.2.jar + dist/modchecker-client-1.20.1-1.2.2.jar | server 4C520044... / client 90FD99E1... |
| 1.2.3 | 白名单可选语义修正 | dist/modchecker-1.20.1-1.2.3.jar + dist/modchecker-client-1.20.1-1.2.3.jar | server D09C3B13... / client CE40C51C... |
| 1.2.4 | CustomSkinLoader 稳定哈希修复 | dist/modchecker-1.20.1-1.2.4.jar + dist/modchecker-client-1.20.1-1.2.4.jar | server 9599D4AD... / client 3FE79E84... |
| 1.2.5 | 通用 JarJar 物理文件哈希 | dist/modchecker-1.20.1-1.2.5.jar + dist/modchecker-client-1.20.1-1.2.5.jar | server F2EC944F... / client F20EDB7E... |

## [1.0.0] - 2026-10-25

### 初始版本

- 首个 Forge 1.20.1 模组清单 / 文件哈希 / 白名单检查版本。
- 服务端在玩家进入世界后检查客户端 mods；后续版本改为 Forge LOGIN 阶段准入。
- 包含基础 manifest、hash manifest、白名单和命令。

### 产物

- dist/modchecker-1.20.1-1.0.0.jar
- 大小：56116 bytes
- SHA-256：40B8B7BC3B9873C709B6432767F8720AD6BF7DF160F3A5206977C3605FA93D1C

### 已知限制

- 初始版本的登录时机与安全边界不如后续版本；仅作历史基线。

## [1.1.0] - 2026-10-25

### LOGIN 阶段安全迭代

- 准入从进入世界后检查改为 Forge LOGIN 阶段的门禁；未完成检查不能进入 PLAY。
- 协议从 1 升级为 2；客户端与服务端必须同步更新。
- 白名单只接受精确小写 mod ID，不再接受显示名授权。
- 报告绑定到当前 Connection；断线清理，不按 UUID 继承旧报告；拒绝重复/覆盖报告。
- 增强清单输入校验、通道版本检查、原子写入和加载错误处理。
- 固定 24 项回归测试。

### 产物

- dist/modchecker-1.20.1-1.1.0.jar
- 大小：66665 bytes
- SHA-256：3ABF33B53158FAC4C06BEB90B62848A861FB9DF1A4BC84D768F6A7E666E610C0

### 已知限制

- 真实生产客户端端到端验证仍未完成；Tweakeroo 实际拦截需后续验证。

## [1.1.1] - 2026-10-25

### 超时、资源与任务生命周期修正

- 拆分 handshakeTimeoutTicks / hashTimeoutTicks，避免大整合包在报告前耗尽握手预算。
- 新增 maxPendingLogins，限制同时等待验证的连接数。
- 客户端报告任务单线程化；新请求/断线取消旧任务；哈希读取每 64 KiB 检查取消。
- 补充真实环境尝试记录，真实 Tweakeroo / 整合包验证仍未通过。

### 产物

- dist/modchecker-1.20.1-1.1.1.jar
- 大小：70386 bytes
- SHA-256：DD566268C5D4C866733E98985DBBF44659C16BA53DBDD19C069EEB73D5130C94

### 已知限制

- 生产客户端端到端未完成；仍有超时累积和任务生命周期的复审意见。

## [1.1.2] - 2026-10-25

### GPT 复审返修

- 删除生产类中测试专用三参数构造器；测试改用正式构造器。
- 新增 totalLoginTimeoutTicks（默认 500），两阶段报告截止受总预算截断。
- 客户端无界 executor 改为容量 1 的有界队列；替换/断线取消任务。
- 文件读取和目录枚举增加取消检查；R06 走实际 LoginChecker 准入路径。
- 原有 24 项 + 第二轮 7 项固定测试；真实生产客户端完成正常进服、清单生成、白名单和 Tweakeroo 单独命中记录。

### 产物

- dist/modchecker-1.20.1-1.1.2.jar
- 大小：71019 bytes
- SHA-256：8D6FEB0E2C602F78BC7E2393C2AD73BACA4D80B95D2A183E2EB4AABC598A6C87

### 已知限制

- 完整标准包 + Tweakeroo 的干净对照未完成；在线首连证据不完整。

## [1.1.3] - 2026-10-25

### 登录稳定性改进

- 客户端登录报告回复改到 Netty 连接 event loop。
- 客户端报告线程空闲 30 秒后退出，下次登录再懒启动。
- 新增 logLoginPhases，记录协商、Forge 数据、报告、进服耗时。
- 真实生产客户端连续 5 次冷启动登录 5/5 成功；锁定 1.1.3 稳定性记录。

### 产物

- dist/modchecker-1.20.1-1.1.3.jar
- 大小：72635 bytes
- SHA-256：EAC4F62C7D8A3BB003579D9BD5F7B6994F93E70DA7620C986BD70B6D9214086A

### 已知限制

- 同进程空闲后重连未闭环；完整包 + Tweakeroo 对照仍未完成。

## [1.1.4] - 2026-10-25

### 客户端工作线程所有权修复

- 修复 ClientHashReporter 空闲退出时旧线程 finally 覆盖新线程状态的竞争。
- 使用 workerThread 拥有者并在 finally 中只清理自己。
- 不改变登录协议和正常路径；1.1.4 冷启动与自动测试通过。

### 产物

- dist/modchecker-1.20.1-1.1.4.jar
- 大小：72689 bytes
- SHA-256：61E29E25037F2AC9CA693E4F72A8FEF0E9FE530133548C15672779ED3942CF62

### 已知限制

- 同进程空闲后重连的完整闭环仍未验证。

## [1.1.5] - 2026-10-25

### 局域网 / 内网穿透准入

- 新增 lanEnforcement（默认 true），让 PCL 对局域网开放、EasyTier、内网穿透的集成服务器对远程玩家执行内容策略。
- 远程玩家仍需安装组件；带 Tweakeroo 等被禁 mod 会被拒绝。
- 独立服务端回归通过。

### 产物

- dist/modchecker-1.20.1-1.1.5.jar
- 大小：73009 bytes
- SHA-256：24C47EA9B8AE8C81B011AFE9816AEC633640638060CCE0F7BD2F39C7366F0EF8

### 已知限制

- 误把主机本地连接也纳入策略，导致主机单人模式被 CustomSkinLoader 哈希挡住；1.1.6 修复。

## [1.1.6] - 2026-10-25

### 主机单人模式保护

- 识别主机本地 memory connection；主机自己的单人连接不执行内容策略，远程 LAN/内网穿透玩家仍检查。
- 修复“开对局域网开放后主机连单人模式都进不去”的问题。
- 不改变远程玩家策略和协议。

### 产物

- dist/modchecker-1.20.1-1.1.6.jar
- 大小：73062 bytes
- SHA-256：B26CBCAFD29C5654FF81E46CBA328630DFBA6357C74AEE396211D8DB2BAD8FE2

### 已知限制

- PCL 集成服务器 + 内网穿透的端到端自动化仍未搭建。

## [1.1.7] - 2026-10-25

### 白名单添加指令

- 新增 /modchecker whitelist add <modid>。
- 校验小写 mod ID，写入 whitelist 文件并自动重载；重复和 banned ID 被拒绝。
- 新增白名单持久化手工测试。

### 产物

- dist/modchecker-1.20.1-1.1.7.jar
- 大小：74325 bytes
- SHA-256：AD6DEC20F942390D7106A0EA8E36F3B0A3EA79DC19073752447CCE42F4CB1155

### 已知限制

- 白名单仍然只允许额外客户端模组，不能修复缺少组件、必需模组缺失或已有模组哈希不一致。

## [1.1.8] - 2026-10-25

### 白名单 Tab 补齐与名称解析

- 在 /modchecker whitelist add 后按 Tab，提示可加入的 mod ID / jar 文件名。
- 输入已加载 mod 的 jar 文件名或唯一显示名时，自动解析成实际 mod ID 再写入白名单。
- 白名单文件仍然只保存 mod ID；额外客户端模组若未在服务端/主机加载，则必须直接填 ID。
- 新增 1.1.8 自动测试和手工白名单测试记录。

### 产物

- dist/modchecker-1.20.1-1.1.8.jar
- 大小：76729 bytes
- SHA-256：E1871A2CFB20351C7375AB35E1700516E14B8E2E8879F626E1DF3401C273DEDE

### 已知限制

- 仅客户端存在、服务端未加载的额外模组无法按文件名解析；命令需要权限等级 2。

## [1.1.9] - 2026-10-25

### 中文模组名称输入修复

- /modchecker whitelist add 参数改为 greedy string，可输入中文显示名、空格、括号和带中文的 jar 文件名。
- 解析顺序：合法小写 mod ID → 已加载 jar 文件名 → 唯一显示名；最终仍写入 mod ID。
- Tab 补齐同时提示 mod ID、jar 文件名和显示名（包括中文）。
- 仅有客户端、服务端未加载的额外模组无法按文件名/显示名解析，需要直接填 ID。

### 产物

- dist/modchecker-1.20.1-1.1.9.jar
- 大小：76787 bytes
- SHA-256：36BA719508E439B0601FCFF2DA4BF8D23285FB01FB9B04C7C8F9868AAA62DEDD

### 已知限制

- 依赖服务端/主机已加载该模组；独立服务端的客户端专属模组需要手动填 ID。
- 命令需要权限等级 2。

## [1.1.10] - 2026-10-26

### 白名单 Tab 显示全部模组 ID

- /modchecker whitelist add 的 Tab 补齐现在包含服务端/主机已加载的 mod ID 和清单中的客户端 mod ID，不再只列尚未在清单中的额外模组。
- 仍然过滤 bannedMods 和已存在白名单条目。
- 建议列表来自 Forge 已加载 mods 目录；仅客户端有、服务端未加载的模组不会出现。
- 中文显示名在部分输入环境不稳定，建议直接用 Tab 选 ID。

### 产物

- dist/modchecker-1.20.1-1.1.10.jar
- 大小：76744 bytes
- SHA-256：6C6AFB783FCBE77246BCC0911B23CFEF9A26B600198D6AD76E561F37B13DD3AE

### 代码保密

- 仓库为私有；LICENSE 改为 All rights reserved，不再使用 MIT 开源许可。
- 未经作者书面许可，禁止复制、发布或再分发。

## [1.2.0] - 2026-10-26

### 服务端/客户端拆分

- 服务端完整 jar：modchecker-1.20.1-1.2.0.jar，包含准入、清单/哈希、白名单、命令、LAN/内网穿透策略。
- 客户端精简 jar：modchecker-client-1.20.1-1.2.0.jar，只包含登录请求处理、报告计算和发送，不含服务端命令/清单/白名单逻辑。
- 完整服务端 jar 也可以安装在客户端，用于生成清单或开内网穿透主机；client-only jar 与完整 jar 不能同时装（同一个 modId/channel）。
- checkCompanionHash 默认关闭：两种 jar 字节不同，不能要求客户端 jar 哈希与服务端完整 jar 一致。
- 协议仍为 2；服务端/客户端 build 成功；服务端自动测试 19+7 通过；双 jar 端到端仍未实测。

### 产物

- dist/modchecker-1.20.1-1.2.0.jar
  - 大小：76746 bytes
  - SHA-256：7F23BEBF14B191D3E5C7639A718E09A7C955075FB91EDE2EB5F9D7B050D76BB9
- dist/modchecker-client-1.20.1-1.2.0.jar
  - 大小：19863 bytes
  - SHA-256：6CC91981A2310FF72A4306D33E4360FCBF7CBCFBA17EB693B65398F4543C58EC

### 已知限制

- 完整服务端 jar 与 client-only jar 不能同时安装。
- 双 jar 真实客户端/服务端联机尚未完成端到端验证；命令和报告代码已分别构建。

## [1.2.1] - 2026-10-26

### 目录 config 哈希排除与升级修复

- HashReportFactory 计算目录模组指纹时排除 config/configs 子目录；修改设置文件不再改变模组哈希。
- R04 增加“config 内容变化不影响目录指纹”的断言。
- 旧 1.1.x 清单和 checkCompanionHash=true 需要手动升级：旧哈希会与新 jar 不一致。
- 双 jar 拆分保持；服务端/客户端 build 成功，19+7 自动测试通过。

### 产物

- dist/modchecker-1.20.1-1.2.1.jar：77047 bytes，SHA-256 D64349BAB6D495DE8767BE21CE136B5763F857E166E27B6569D2B3716C59FC35
- dist/modchecker-client-1.20.1-1.2.1.jar：20165 bytes，SHA-256 B321FD1D7C38B4710C13F816415F28C78690066C651806D3C547883FDAC54CCB

### 已知限制

- 仅排除目录中的 config/configs；jar 内文件仍按 jar 字节计算。
- 旧清单必须重新生成；CustomSkinLoader 等客户端外观模组可加入 ignoredMods 或统一版本。

## [1.2.2] - 2026-10-26

### 白名单可选模组

- /modchecker whitelist add 现在表示“可选模组”：客户端缺少该 mod 也允许进入。
- 白名单条目同时跳过 missing、hash、channel 比较；bannedMods 仍优先。
- 新增 whitelistAllowsMissing（默认 true）；设为 false 可恢复 1.2.1 的额外-only 语义。
- 手工测试：WHITELIST_OPTIONAL_TEST PASS。
- 服务端/客户端 build 成功；19+7 自动测试通过。

### 产物

- dist/modchecker-1.20.1-1.2.2.jar：77353 bytes，SHA-256 4C5200447FC57D9F43E62A42974731D7A8BDE28DBC82CAA48F3925E3BE083109
- dist/modchecker-client-1.20.1-1.2.2.jar：20166 bytes，SHA-256 90FD99E1FB717D5A9F0BA067CEE67E67199A97D1F434CB006221CCE987BA9E03

### 安全提示

- 只把确实可选的模组加入白名单；白名单不再阻止客户端缺少这些模组。
## [1.2.3] - 2026-10-26

### 白名单可选语义修正

- 客户端缺少白名单 mod：仍然允许进入。
- 客户端存在白名单 mod：恢复正常的清单/版本/哈希/频道检查，不再跳过。
- 修正 1.2.2 中“存在也不同哈希也放行”的过宽行为。
- 手工测试：WHITELIST_OPTIONAL_TEST PASS、WHITELIST_PRESENT_HASH_TEST PASS。
- 服务端/客户端 build 成功；19+7 自动测试通过。

### 产物

- dist/modchecker-1.20.1-1.2.3.jar：77340 bytes，SHA-256 D09C3B135FAC9761AF1E91401AE0B16F601110110D944E81E7C2FC5A6BF099C6
- dist/modchecker-client-1.20.1-1.2.3.jar：20167 bytes，SHA-256 CE40C51CAD2D37033ECEB0EFA19164C6953AFAB0D8EDB8A558B127D1A37A2B9D

### 安全提示

- 白名单只放行“缺失”；存在时仍校验。
- 想让存在的不同版本也跳过，使用 ignoredMods，而不是白名单。
## [1.2.4] - 2026-10-27

### CustomSkinLoader 稳定哈希修复

- CSL Bootstrap 会把嵌套 Common jar 解压/重映射到 `CustomSkinLoader/Core/CustomSkinLoader-Common.jar`。
- Forge 的 owning file 有时指向这个运行时生成文件，导致同版本 CSL 的哈希在不同启动/机器上不一致。
- HashReportFactory 现在对 `customskinloader` 检测 Common jar，改为哈希 `mods` 下的外层 Universal jar。
- 旧清单仍记录 Common jar 哈希，必须重新生成 manifest/hashes。
- 手工测试：CSL_HASH_PATH_TEST PASS；UNIT_TOTAL=19 PASS=19；ROUND2_TOTAL=7 PASS=7。

### 产物

- dist/modchecker-1.20.1-1.2.4.jar：78969 bytes，SHA-256 9599D4AD75A2FEF0FB387415BE3A7C5DBD4761F62639D5C7206DA31A808997C6
- dist/modchecker-client-1.20.1-1.2.4.jar：21796 bytes，SHA-256 3FE79E84484D888BE2C14441F1883E31A5A192876D7D5656AB8565B278B50827

## [1.2.5] - 2026-10-27

### 通用 JarJar 物理文件哈希

- HashReportFactory 对所有 mod 做顶层物理文件解析：路径不在 mods 或为运行时生成文件时，回溯到包含该 mod 的 mods 顶层 jar。
- 支持 Forge `mods.toml`、Fabric `fabric.mod.json`、目录模组和嵌套 JarJar 的 modId 索引。
- 实际 CSL 测试：`CustomSkinLoader/Core/CustomSkinLoader-Common.jar` 解析到 `mods/CustomSkinLoader_Universal-15.1.jar`，约 612ms。
- 旧清单仍记录 Common jar 哈希，必须重新生成。
- 手工测试：GENERIC_JARJAR_HASH_TEST PASS、CSL_ACTUAL_PATH_TEST PASS；UNIT_TOTAL=19 PASS=19；ROUND2_TOTAL=7 PASS=7。

### 产物

- dist/modchecker-1.20.1-1.2.5.jar：82918 bytes，SHA-256 F2EC944F3C0D0B94255658DFAF0DDFEECCC2E1224E132C5558C06FFA878D0C95
- dist/modchecker-client-1.20.1-1.2.5.jar：25745 bytes，SHA-256 F20EDB7E75A9ADB7ED96098CE4B6605752F673DB3203634B2F1D4B12F3D0771A
## 版本文件说明

- `dist/`：当前工作区全部历史 jar。
- `versions/<版本>/`：该版本 jar、SHA-256 和变更说明；1.0.0 / 1.1.0 有可用的源包备份。
- 1.1.1–1.1.7 没有完整历史源码快照，保留 jar 和文档；当前源码为 1.1.8。
- 测试证据和运行日志未上传，避免包含本机路径、玩家名和测试配置。
