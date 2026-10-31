# TextbookGo-Mobile · Android

`TextbookGo-Mobile` 的 Android 客户端，Kotlin + Jetpack Compose + Material 3，minSdk 26、手机优先。

> 功能/验收矩阵与已知缺口见 [STATUS.md](<STATUS.md>)；脚手架期的独立审查记录见 [REVIEW.md](<REVIEW.md>)；真机验收步骤见 [05-真机验收指南](../docs/android/05-真机验收指南.md)。

## 已实现

| 模块 | 内容 |
|---|---|
| 登录 | 应用内 WebView 打开平台登录页，读取 `ND_UC_AUTH*` 中的 `access_token`；支持手工粘贴 token；记住/忘记登录；401/403 自动标记过期 |
| 选书 | 年级/学科/版本三级筛选（版本固定“全部 → 人教版 → 其余”）、每个学科记住上次版本、双列网格、已选计数、清空、跨分类多选、按 ID 去重；书名去掉“义务教育教科书”前缀，2022 年修订版以标签置顶；未登录时书单遮罩并禁止选书 |
| 目录 | 真实分片并发拉取、部分失败保留上一代、目录缓存（重启/断网可用）、加载/失败/重试状态；年级归一与异常标签清洗 |
| 下载 | 全局最多 3 并发、流式写入、主/备链接回退、暂停/恢复/取消/重试、大小与 MD5 校验、成功入库、任务持久化 |
| 断点续传 | 206 校验起点/总长/验证器、200 自动重下、416 完成判断、不同来源不拼接 |
| 后台 | 前台服务 + 进度通知；无任务自动停止；Android 13+ 首次下载请求通知权限 |
| 书架 | 平台封面（下载时写入书架记录）、PDF 首页缩略图兜底、大小/日期、打开 PDF、分享、导出（SAF）、删除确认 |
| 下载任务 | 书架顶部内联展示：真实进度、状态、暂停/继续/重试/取消；已完成任务自动收起，不占版面 |
| 设置 | 暗色模式（跟随系统/浅色/深色，默认跟随系统）、记住登录、存储路径与卸载说明、登录状态/重新登录、版本/构建类型/作者与原作者 MIT 归属/版权、联系作者（微信/QQ 可复制）、Gitee/GitHub 仓库入口 |
| 更新 | 启动 3 秒后静默检查 Gitee 上的 `latest.json`，24 小时节流；设置页可手动检查；新版本弹窗（版本/大小/说明），浏览器下载安装；支持跳过版本与强制更新 |
| 视觉 | 年级/学科/版本三色标签，选中为实色高亮 + 勾选角标；紧凑筛选行；卡片等宽圆角/阴影；登录页品牌图形；底部图标导航；晨光/暗色两套完整配色 |
| 持久化 | 任务、书架、目录缓存使用原子 JSON；设置使用 DataStore；凭据单独存放并关闭备份 |
| 安全 | HTTPS 主机白名单、凭据只发平台域名、FileProvider、`allowBackup=false`、禁止传输压缩、跨域重定向拒绝 |

## 结构

```text
android/
  core/                 纯 Kotlin/JVM 领域层：目录规则、DTO/映射、选择集、状态机、
                        续传策略、资源选择、URL 白名单、完整性校验、token 解析、
                        目录缓存序列化、封面、书签映射、仓库契约
    src/test/kotlin/    142 个领域测试（独立测试目录）
  testing/              共享内存假实现（JVM），供 UI 测试/预览使用
  app/                  Compose 客户端
    src/main/           ViewModel、页面、数据层、持久化、前台服务、平台动作
    src/debug/          示例授权/目录/内容源（正式包不包含）
    src/verify/         真机验收：真实数据源 + 调试签名
    src/release/        真实数据源（未签名发布包）
    src/test/           51 个数据层/存储/传输测试（含默认跳过的真实网络探针）
    src/androidTest/    3 个设备级 Compose 测试
```

## 验证状态（2026-10-31）

| 检查 | 结果 |
|---|---|
| `:core:test` | **142 / 142 通过**（含新增更新策略测试） |
| `:testing:test` | **6 / 6 通过**（上一轮） |
| `:app:testDebugUnitTest` | **51 / 51 通过**（上一轮；本轮更新检查改动后未重跑，留给你验证） |
| `:app:connectedDebugAndroidTest` | **3 / 3 通过**（Pixel_9_API36，Android 16 / API 36） |
| `:app:lintDebug` | **No issues found** |
| `:app:assembleDebug` | 成功，约 11.1 MB |
| `:app:assembleVerify` | 成功（调试签名），约 7.6 MB |
| `:app:assembleRelease` | 成功（正式签名），约 8.0 MB |

模拟器上已验证：应用内 WebView 能加载真实登录页并完整显示表单；真实目录加载出 **3565 本教材**与封面；进程重启后凭据与目录缓存均恢复。

## 命令

```powershell
.\gradlew.bat :core:test :testing:test :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug        # 示例数据，演示/自动化用
.\gradlew.bat :app:assembleVerify       # 真机验收：真实平台 + 调试签名
.\gradlew.bat :app:assembleRelease      # 正式包（自动使用 keystore.properties 签名）
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest   # 需设备/模拟器
```

真实网络契约探针（会下载约 10 MB 目录分片）：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*RealCatalogProbeTest" "-Dnetwork.probe=true"
```

SDK 位于 `D:\Android\Sdk`，已写入 `local.properties`（不提交）。Wrapper 首次运行会下载 Gradle 8.13。

## Debug 示例模式

- 登录立即成功；目录为 6 本内置示例教材（七/八/九年级，数学/语文/英语/物理）。
- 下载源在应用缓存中生成合法的两页 PDF，按 16 KiB 分块复制并模拟进度，不访问网络。
- 设置页显示“示例模式”提示；示例类位于 `src/debug`，不会进入 verify/release。
- 用途：UI 开发、端到端测试与演示；不代表真实教材内容。

## Verify / Release 真实链路

- 登录：`WebLoginDialog` 在应用内打开 `https://auth.smartedu.cn/uias/login`，轮询 `ND_UC_AUTH*` 并解析 `access_token`；也可从桌面端 `token.json` 手工粘贴。
- 请求：token 以 `X-ND-AUTH: MAC id="..."` 发往平台 `s-file-*.ykt.cbern.com.cn` 与私有 CDN（与旧版 Go 客户端一致）。
- 目录：`data_version.json` → 分片并发拉取 → 去重 → 缓存到 `filesDir/catalog-cache.json`。
- 下载：详情 → `ti_items` 选源 → 私有 CDN 流式下载 → PDF 魔数/大小/可选 MD5 → 原子改名 → 入库。
- 失败：401/403 标记“需登录”并清除凭据；403 也会提示“可能无该资源权限”；网络错误可重试。

## 已知限制

- **真实账号登录后的完整下载尚未验证**（无凭据环境无法完成）；这是当前唯一需要你本人执行的验收项。
- 部分教材详情接口返回 403，可能是账号权限或地区限制；应用会提示但无法替平台授权。
- 登录页为平台网页，样式依赖 WebView 兼容性；已注入脚本修正窄屏布局，平台改版需同步调整。
- 前台服务已加，但锁屏/厂商省电策略下的续传仍待真机回归。
- 持久化使用原子 JSON 而非 Room；书架封面为几何拼贴；PDF 书签未写入。
- 未配置 CI、Release 签名、商店合规与像素级视觉对照。

## 版本基线

- JDK 21（Zulu 21.0.10）
- Gradle 8.13（Wrapper）
- Android Gradle Plugin 8.13.2
- Kotlin 2.3.21
- compileSdk/targetSdk 36，minSdk 26
- Compose BOM 2024.09.03

升级前需重新跑通全部测试、Lint 与各构建变体。
