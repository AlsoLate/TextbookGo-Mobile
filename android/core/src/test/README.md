# `core` 测试目录

本目录是领域层的独立测试目录：只放测试、fixtures 与说明，不混入生产实现。共享假实现已移至 [`testing`](../../../../testing) 模块。

## 运行

在 `android/` 下执行：

```powershell
.\gradlew.bat :core:test :testing:test
```

当前状态：**全部通过（core 111 + testing 6）**。

## 测试与需求映射

| 测试类 | 覆盖内容 | 需求/验收 |
|---|---|---|
| `CatalogRulesTest` | 年级顺序、未知年级追加、学科排序、contains 兼容、教材过滤/排序/已下载标记、搜索、LIKE 转义 | R03/R04/R05/R08，A07/A08 |
| `CatalogJsonTest` | `urls` 逗号分隔、`ti_storages` 字符串数组、Long 字段、未知字段、类型错误 | 协议契约（审计 §3） |
| `CatalogMapperTest` | DTO→领域映射、封面 thumbnails→Slide1 回退、空值 | R03/R10，A34 |
| `CatalogRefreshPolicyTest` | 完整/部分/失败目录更新、保留上一完整版本、零教材视为失败 | R16，A35 |
| `BookSelectionTest` | 跨分类选择、ID 去重、计数=提交、快照不可变 | R05，A09 |
| `DownloadStateMachineTest` | 正常链路、跳过校验/提交的拒绝、暂停/恢复、终态、取消、重试 | R06/R18，A22/A44 |
| `BatchStatsTest` | 成功/跳过/失败/取消/待授权/等待分别计数，结束≠全成功 | R06，A36 |
| `ResumePolicyTest` | 206/200/416、Content-Range、ETag 变化、无验证器从头 | R18，A38/A39/A45 |
| `ResourceSelectorTest` | 源文件优先、映射排除、PDF 回退、空白 storage 忽略 | R09，A19 |
| `FilenamePolicyTest` | 内部 ID 文件名、展示名短 ID、非法字符、控制字符、路径穿越、长度上限 | R08，A16 |
| `UrlPolicyTest` | HTTPS、主机白名单、后缀/UserInfo 伪装、鉴权主机、CDN 模板 | 安全契约，A43 |
| `SessionPolicyTest` | 仅 Validated 可发受保护请求、过期/失败需用户操作 | R02/R13，A04 |
| `CoverPaletteTest` | 6 种拼贴的确定性哈希、JS 32 位语义 | R12，A34 |
| `PdfBookmarkRulesTest` | 层级、页码缺失、写入展平、引号转义 | R19，A40（可选增强） |
| `ScaffoldSmokeTest` | JVM 测试与资源加载自检 | 工具链 |

## 尚未覆盖（后续阶段）

- MockWebServer 的下载传输集成测试（200/206/401/403/404/416/429/5xx、截断、重定向）。
- Room schema/迁移、DataStore、Keystore。
- 真实目录仓库、下载引擎、WorkManager/前台服务。
- 真机上的权限、后台、通知与文件访问。

Compose 冒烟测试见 `app/src/androidTest`，已在 API 36 模拟器执行通过。

## Fixtures 规则

- 只使用人工构造、已去敏的数据，不含真实 token、cookie、账号或教材正文。
- 新增样例放在 `src/test/resources/fixtures/`，并在测试中通过 `Fixtures.read` 读取。
