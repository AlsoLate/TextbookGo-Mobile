# Android 脚手架审查与修复报告

> 日期：2026-10-31  
> 范围：`android/` 工程、领域契约、首批测试、Compose 壳与构建配置  
> 结论：**READY —— 可以开始客户端 UI 开发；尚未达到端到端功能完成或可发布状态。**  
> 后续进展：客户端功能闭环已实现，当前功能矩阵与已知缺口见 [STATUS.md](<STATUS.md>)。本报告保留脚手架期的审查与修复记录；最新一轮（功能完整度复审）见下方 §0。

## 0. 本轮功能完整度复审（2026-10-31，交付前）

### 0.1 审查方式

- 按要求再次尝试调用子代理：`subagent` 与 `subagent_fork` 共 6 次均在启动后异常退出、无任何返回内容。**子代理设施在本会话不可用，本节由主代理按同等清单完成，未伪造子代理结论。**
- 对照基线：`internal/auth`、`internal/api`（含真实端点与 `X-ND-AUTH` 格式）、`internal/download`、`docs/android` 需求与验收、`STATUS.md` 的 R01–R21。
- 除静态审查外，额外做了真实网络与模拟器验证：真实目录探针（`RealCatalogProbeTest`，需 `-Dnetwork.probe=true`）、真实登录页加载、真实目录 3565 本与缓存恢复、伪 token 鉴权失败路径。

### 0.2 阻断级问题（P0，已修复）

| # | 问题 | 证据 | 修复 |
|---|---|---|---|
| P0-1 | Release 无法登录任何账号，客户端主流程不可用 | `UnconfiguredAuthRepository` 直接返回失败 | 新增 `WebLoginDialog`（应用内 WebView 读取 `ND_UC_AUTH` 的 `access_token`）、`StoredAuthRepository` + `TokenStore`，并保留手工 token 兜底 |
| P0-2 | 下载请求不带 `X-ND-AUTH`，私有 CDN 必然 401/403 | `HttpTransfer` 仅发 Range/UA | 注入 token provider，按 `UrlPolicy` 只发平台域名；新增请求头测试 |
| P0-3 | 真实 `ti_md5` 形如 `xxxx-3`，整文件比对必然误判“完整性校验失败” | `internal` 的真实详情样例、模拟器实测 | 新增 `IntegrityPolicy`：仅 32 位十六进制才比对；带后缀跳过；有单测 |
| P0-4 | `ti_storages` 备用链接指向 `r2/r3-ndr-private`，白名单只有 `r1`，备用链接必失败 | 真实详情 JSON | 白名单补齐 r1/r2/r3；重定向逐跳校验、跨域拒绝 |
| P0-5 | 进程在下载中被杀后，任务停留在“下载中”无法恢复 | `TaskStore`/恢复逻辑 | 中间态恢复为 QUEUED 并重新入队；新增进程重启单测 |
| P0-6 | 下载地址 `c1` 在某些网络不可用且无 token 时被选用 | 真实网络探测 | 登录后统一走私有大 token 路径；无凭据不允许下载 |

### 0.3 重要问题（P1，已修复）

| # | 问题 | 修复 |
|---|---|---|
| P1-1 | 目录无缓存，重启/断网即空白 | `CatalogCache` + `CatalogCacheJson`，启动后台加载，完整刷新后覆盖 |
| P1-2 | 目录刷新失败被吞，界面只显示“暂无教材” | `CatalogStatus`（Loading/Loaded/Failed），错误横幅 + 重试 |
| P1-3 | 详情接口 403 只显示“HTTP 403” | 改为“平台拒绝访问（HTTP 403），请重新登录或确认账号权限”；401→需登录 |
| P1-4 | 登录页在 WebView 窄视口下表单被顶出屏幕 | 打开后注入布局修正脚本，把登录框移回视口（已在模拟器验证完整表单可见） |
| P1-5 | 后台下载无保活、无通知 | 新增 `DownloadService` 前台服务 + 进度通知 + Android 13 通知权限请求 |
| P1-6 | 任务恢复时目录可能尚未加载，`findBook` 为空导致“教材不存在” | 入队时记录标题；下载不再以目录存在为前置，改用详情标题/任务标题兜底 |
| P1-7 | 退出登录不停止任务，可能继续用旧凭据请求 | 新增 `cancelActive()`，退出时取消未完成任务并清除凭据 |

### 0.4 验证结果

- JVM：`:core` 127、`:testing` 6、`:app` 48（含 1 个默认跳过的网络探针），全部通过。
- 设备：API 36 模拟器 3/3（启动、设置、登录→选书→下载→书架）。
- 构建：Debug / verify / Release 全部成功；`lintDebug` 无问题。
- 真实环境：目录 3565 本；重启后凭据与目录缓存恢复；伪造 token 时详情接口 403 且界面可读提示。

### 0.5 本轮未覆盖

- **真实账号登录后的真实 PDF 下载完成**（无凭据，无法验证）：按 [真机验收指南](../docs/android/05-真机验收指南.md) 执行。
- 锁屏/厂商省电下的续传；SAF/分享在真实文件管理器中的差异；像素级视觉与 TalkBack。

---

## 1. 审查方式与限制

- 按要求先尝试调用子代理独立审查。共 4 次尝试（1 次综合审查、2 次拆分审查、1 次最小连通性测试），均异常退出且无任何返回内容。**子代理设施在本会话不可用，本报告由主代理按同等清单完成，未伪造子代理结论。**
- 审查覆盖：现有 Go/网页行为基线、`docs/android` 需求与验收、`core` 全部源码与测试、Gradle/Manifest/资源、README 一致性。
- 实际执行：单元测试、`:testing` 测试、Debug/Release 构建、AndroidTest 编译、Lint、API 36 模拟器上的 `connectedDebugAndroidTest`。

## 2. 结论与门槛

### 客户端开工门槛（全部通过）

| 门槛 | 证据 | 状态 |
|---|---|---|
| 领域规则可执行、非占位 | `:core:test` 111/111 通过 | ✅ |
| 共享假实现可用 | `:testing:test` 6/6 通过，`:app` 已依赖 | ✅ |
| 应用壳可启动、可导航 | API 36 模拟器 2/2 Compose 测试通过 | ✅ |
| Debug 构建 | `app-debug.apk`，约 9.9 MB | ✅ |
| Release 构建 | `app-release-unsigned.apk`，约 6.8 MB | ✅ |
| 静态检查 | `lintDebug`：No issues found | ✅ |
| 客户端契约齐全 | Auth/Catalog/Download/Shelf/Preferences + 结果类型 | ✅ |
| 工程配置 | 图标、备份排除、ProGuard、JVM target、Wrapper | ✅ |
| 文档与代码一致 | 根 README、android/README、测试说明已同步 | ✅ |

### 不在“客户端开工”门槛内、但尚未完成

- 平台授权 PoC、真实 HTTP 数据源、MockWebServer 传输测试。
- Room/DataStore/Keystore、下载引擎、WorkManager/前台服务、通知。
- 真实教材下载、文件打开/导出/分享、后台恢复。
- 视觉像素级对照、完整无障碍评审、CI、Release 签名与商店合规。

因此：**可以开始 Compose 页面与交互开发（依赖仓库契约 + `:testing` 假实现）；不能声称端到端功能已完成或可发布。**

## 3. 主要发现与修复

### P0（阻断客户端开工）

| # | 发现 | 修复 |
|---|---|---|
| P0-1 | `core` 全部业务函数为 `TODO`，117 个测试中 88 个仅验证“未实现” | 实现目录规则、JSON、选择集、状态机、批次统计、续传、文件名、URL 白名单、封面哈希、书签映射；测试全绿 |
| P0-2 | 文档 §3 的仓库契约与客户端假实现完全缺失，UI 无稳定依赖面 | 新增 5 个仓库接口、结果类型、`SessionState`；新增 `:testing` 模块提供内存假实现 |
| P0-3 | 缺少 DTO→领域映射与封面回退，网络数据无法进入规则层 | 新增 `CatalogMapper`（thumbnails→Slide1 回退）及 5 个测试 |
| P0-4 | 缺少资源候选选择，下载引擎无法确定下载地址 | 新增 `ResourceSelector`（源文件→PDF→其他，排除 ebook_mapping）及 7 个测试；测试发现空白 storage 仍被选中，已修复 |
| P0-5 | App 仅单页占位，无底部导航、无双主题切换 | 新增下载/书架/设置三页 NavigationBar、晨光/夜读主题、设置页开关 |
| P0-6 | Manifest 无启动图标；Lint 报 MissingApplicationIcon | 新增自适应图标（含 monochrome）与 roundIcon |
| P0-7 | `proguard-rules.pro` 被引用但不存在；备份规则缺失 | 新增 ProGuard 规则、`data_extraction_rules.xml`、`fullBackupContent=false` |
| P0-8 | `core` 产出 Java 21 字节码、App 为 17，构建报 JVM target 不一致 | `core` 统一为 Java 17（Kotlin `jvmTarget` + Java 兼容性） |
| P0-9 | 无 instrumentation 测试，客户端壳未被设备验证 | 新增 2 个 Compose 冒烟测试，在 Pixel_9_API36 / Android 16 上真实通过 |

### P1（影响可维护性/正确性）

| # | 发现 | 修复 |
|---|---|---|
| P1-1 | 目录分片部分失败没有结果模型，容易静默替换完整缓存 | 新增 `CatalogRefreshPolicy`：完整/部分/失败，部分失败保留旧版本，零教材视为失败 |
| P1-2 | 会话只有“是否有 token”概念，无法表达过期/校验失败 | 新增 `SessionState` 与 `SessionPolicy`，仅 `Validated` 可发受保护请求 |
| P1-3 | 续传计划未携带总长度，206 总量不一致无法校验 | `ResumeRequestPlan.Resume` 增加 `totalBytes`，响应校验覆盖 206/200/416 |
| P1-4 | 假实现放在 `core/src/test`，App 无法复用 | 拆出 `:testing` JVM 模块，`app` 以 `testImplementation`/`androidTestImplementation` 依赖 |
| P1-5 | `core` 以 `implementation` 引入 OkHttp，却在公开 API 暴露 `HttpUrl` | 改为 `api(libs.okhttp)`，避免消费方编译期缺类型 |
| P1-6 | `BatchStats.finished` 与 `isTerminal(FAILED)=false` 语义看似矛盾 | 明确区分“批次无待办”（finished）与“单任务可重试”（failed 非终态），测试分别覆盖 |

### P2（后续改进，不阻塞）

- `ObsoleteSdkInt` 在 `mipmap-anydpi-v26` 上是误报，已用带注释的 `app/lint.xml` 仅忽略该条；其余 Lint 规则保持开启。
- 包名 `com.textbookgo.mobile` 为占位，发布前替换为受控反向域名。
- Release 目前未签名；签名、AAB、商店合规留待发布阶段。
- 尚未接入 CI；当前依赖本机命令验证。
- 视觉仅迁移了色彩/结构 token，尚未做像素级对照与 TalkBack 全量走查。

## 4. 实际验证命令与结果

```powershell
.\gradlew.bat :core:test :testing:test `
  :app:assembleDebug :app:assembleRelease `
  :app:assembleDebugAndroidTest :app:lintDebug
# BUILD SUCCESSFUL，core 111/111，testing 6/6，Lint 0 issues

.\gradlew.bat :app:connectedDebugAndroidTest
# Starting 2 tests on Pixel_9_API36(AVD) - 16
# Finished 2 tests ... BUILD SUCCESSFUL
```

产物：

- `app/build/outputs/apk/debug/app-debug.apk`（约 9.9 MB）
- `app/build/outputs/apk/release/app-release-unsigned.apk`（约 6.8 MB）
- `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`（约 1.0 MB）

## 5. 剩余风险

1. **授权可行性仍未验证**：登录是原项目最关键的平台依赖，必须在 M0 PoC 中确认；在此之前不承诺端到端可用。
2. **上游协议非稳定契约**：已有 DTO/类型错误测试，但真实响应仍需录制去敏 fixtures。
3. **后台/续传受平台与服务器约束**：策略与状态机已就绪，实际 Range 行为需 MockWebServer 与真实网络验证。
4. **文件语义未落地**：App 内保存、导出、卸载删除提示目前只有契约与文案，未接 SAF/MediaStore/FileProvider。
5. **版本地化与无障碍**：仅有基础中文文案，未做多语言、字体缩放与 TalkBack 全量验证。

## 6. 建议的下一阶段顺序

1. 基于 `AuthRepository`/`CatalogRepository` 与 `:testing` 实现登录页、选书页、书架页、任务面板。
2. 并行实现 data 层：OkHttp 客户端 + MockWebServer 测试 + Room + DataStore + Keystore。
3. 授权 PoC 通过后接入真实目录与下载；随后做文件打开/导出/删除。
4. 最后做视觉对照、无障碍、后台与发布准备。
