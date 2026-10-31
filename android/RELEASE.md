# 正式版发布与签名

> **发布前提醒：** 先接入更新检查再发 1.0.0。首个正式版如果不带“检查更新”，以后发新版本时，1.0.0 的用户只能自己去 Gitee 手动下载覆盖安装；带上更新入口后，后续版本才能自动提示。

## 1. 签名已经配好

本机已经生成正式签名，构建 release 时会自动使用：

| 文件 | 作用 |
|---|---|
| `android/keystore/textbookgo-release.jks` | 正式签名证书（RSA 4096，有效期 10000 天） |
| `android/keystore.properties` | 证书密码、别名（与 jks 配套） |

这两个文件已加入 `android/.gitignore`，**不会提交到 git**。

证书指纹（SHA-256）：

```text
72:4b:21:88:e6:eb:5b:47:9f:3b:ba:ae:63:71:69:19
8f:41:c4:5d:69:af:cc:10:4e:27:23:e2:d0:cc:9e:17
```

> **最重要的一件事：备份 `keystore/` 目录和 `keystore.properties`。**
> 把这两个文件复制到密码管理器、网盘或 U 盘。以后所有版本必须用同一份签名；丢了就只能换包名重新发布，老用户无法覆盖升级，只能卸载重装（书架和登录状态都会丢）。

## 2. 打正式包

在 `android/` 目录执行：

```powershell
.\gradlew.bat :app:assembleRelease
```

产物：

```text
android/app/build/outputs/apk/release/app-release.apk
```

当前版本：`1.1.0`（versionCode `4`，加入应用内阅读器），已用正式证书签名。当前 APK 的 SHA-256：

```text
f69fd13ba79f72d1a2263b72ad89075699798b6b5d3f281c87f1e240b6c31098
```

Gitee 仓库：<https://gitee.com/yizhixixi/TextbookGo-Mobile>（线上 Release 仍为 v1.0.0；v1.1.0 待真机测试通过后再发布）

GitHub 镜像（源码 + Release）：<https://github.com/AlsoLate/TextbookGo-Mobile>  
更新检查会先请求 Gitee，失败后自动回退到 GitHub 的 `update/latest.json`。

检查签名：

```powershell
& "D:\Android\Sdk\build-tools\37.0.0\apksigner.bat" verify --print-certs `
  android\app\build\outputs\apk\release\app-release.apk
```

## 3. 发布到 Gitee（国内可直连）

Gitee 的「发行版（Release）」附件是国内用户下载 APK 最省事的方式，公网可直接访问，8 MB 的包没有问题。

### 3.1 建仓库

1. 登录 Gitee，新建一个**公开**仓库，例如 `TextbookGo-Mobile`。
2. 仓库必须公开，否则更新清单和 APK 都需要登录，App 拉不到。

### 3.2 发第一个正式版

1. 进入仓库 → **发行版 / Releases** → **新建发行版**。
2. 标签填 `v1.1.0`，标题填 `TextbookGo-Mobile 1.1.0`。
3. 上传 `app-release.apk`，建议改名为：

   ```text
   TextbookGo-Mobile-1.1.0.apk
   ```

4. 发布后，在附件上右键复制下载地址，形如：

   ```text
   https://gitee.com/yizhixixi/TextbookGo-Mobile/releases/download/v1.1.0/TextbookGo-Mobile-1.1.0.apk
   ```

5. **用手机浏览器（不登录 Gitee）打开这个地址**，确认能直接下载。

### 3.3 放更新清单

在仓库里新建文件 `update/latest.json`：

```json
{
  "versionCode": 4,
  "versionName": "1.1.0",
  "minSupportedVersionCode": 3,
  "apkUrl": "https://gitee.com/yizhixixi/TextbookGo-Mobile/releases/download/v1.1.0/TextbookGo-Mobile-1.1.0.apk",
  "sha256": "f69fd13ba79f72d1a2263b72ad89075699798b6b5d3f281c87f1e240b6c31098",
  "size": 8066301,
  "publishedAt": "2026-10-31T23:30:00+08:00",
  "changelog": "新增应用内 PDF 浏览：书架直接打开、连续滚动、双指缩放、跳页与夜读"
}
```

它的固定访问地址（用 Gitee contents API，不要用 raw）：

```text
https://gitee.com/api/v5/repos/yizhixixi/TextbookGo-Mobile/contents/update/latest.json
```

> **为什么不用 raw：** Gitee 的 `raw` 地址有 CDN 缓存，发版后几分钟内可能仍然返回旧清单；App 若在这时检查会误判“已是最新”并节流 24 小时。contents API 返回实时内容，App 会自动解开它的 base64 包装。
> 同样用手机浏览器（不登录）验证能打开；返回的是带 `content` 字段的 JSON 属于正常。

> Gitee 免费仓库有容量和流量限制，APK 约 8 MB 完全够用；如果以后下载量大，可以把 APK 换到阿里云 OSS / 腾讯云 COS，清单格式不变。

### 3.4 以后每次更新

1. 改 `android/app/build.gradle.kts`：`versionCode` 加 1，`versionName` 改成新版本号（如 `1.0.3`）。
2. 重新执行 `assembleRelease`。
3. 新建 `v1.0.3` 发行版，上传新 APK。
4. 更新 `update/latest.json` 的 `versionCode`、`versionName`、`apkUrl`、`sha256`、`size`、`changelog`。

## 4. 版本号和包名

| 构建 | 包名 | 签名 | 用途 |
|---|---|---|---|
| debug | `com.textbookgo.mobile` | debug | 本机开发、示例数据 |
| verify | `com.textbookgo.mobile.verify` | debug | 真机验收、真实平台 |
| release | `com.textbookgo.mobile` | 正式证书 | 对外发布 |

- release 和 verify 是**两个独立应用**，可以同时安装，数据不共享；装 release 后需要重新登录一次。
- 不要用 verify 包对外发布：它的包名带 `.verify`，签名也是调试证书，以后无法平滑升级到正式版。
- 正式包不要覆盖安装到已经有同名 `com.textbookgo.mobile` 旧包的设备上（如果以前装过 debug 版），签名不同会安装失败，需要先卸载旧 debug 版。

## 5. 更新检查的接入

客户端更新逻辑见 [06-应用内更新机制设计](<../docs/android/06-应用内更新机制设计.md>)，**已经接入并打通**：

- 启动 3 秒后静默检查，24 小时节流；
- 设置页「关于 → 检查更新」手动检查；
- 新版本弹窗 + 浏览器下载；
- `minSupportedVersionCode` 强制更新；
- 清单地址已写入 `BuildConfig.UPDATE_MANIFEST_URL`（`app/build.gradle.kts`）。

当前 Gitee 仓库：<https://gitee.com/yizhixixi/TextbookGo-Mobile>



