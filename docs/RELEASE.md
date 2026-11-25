# 发布说明（Release）

## 1. 百度地图 AK 的泄露问题

Android 应用中的百度地图 AK **无法真正隐藏**。无论使用 `local.properties` 还是 GitHub Secrets，AK 最终都会通过 `BuildConfig.MAPS_API_KEY` 编译进 `classes.dex`，发布 APK 后可以用反编译工具直接提取。因此：

- 不要把 AK 写进源码并提交；
- 不要把“AK 没出现在仓库里”当作安全措施；
- 正确的做法是在百度地图开放平台给 AK 设置 **应用包名 + SHA1** 限制。

本项目发布配置：

| 项目 | 值 |
| --- | --- |
| applicationId | `com.github.yingsuopro` |
| Release keystore | `keystore/GoGoGo.jks` |
| Keystore SHA1 | `32:2F:E6:05:6C:2F:11:6D:CD:ED:AC:FF:23:02:5A:29:68:FD:45:D2` |
| 百度地图应用 | `YingSuoPro`（应用编号 `124758582`） |

在百度地图开放平台的控制台中，把该 AK 的应用包名设置为 `com.github.yingsuopro`，并把上面的 SHA1 填入发布版 SHA1。这样即使 AK 被提取，其他包名/签名的应用也无法直接使用。

> 如果之后更换签名证书，必须同步更新百度控制台中的 SHA1，并更新 `MAPS_SAFE_CODE`。

## 2. GitHub Actions 发布配置

`.github/workflows/build-release.yml` 会在以下场景运行：

- 手动触发 `workflow_dispatch`；
- 推送 `v*` 标签（例如 `v1.0`）。

需要在仓库 **Settings → Secrets and variables → Actions** 中添加：

| Secret | 内容 |
| --- | --- |
| `MAPS_API_KEY` | 百度地图 Android SDK 的 AK |
| `MAPS_SAFE_CODE` | `32:2F:E6:05:6C:2F:11:6D:CD:ED:AC:FF:23:02:5A:29:68:FD:45:D2;com.github.yingsuopro` |

Release 构建使用仓库内的 `keystore/GoGoGo.jks` 签名（与本地 debug 构建同一证书），因此不需要额外配置签名 secrets。该 keystore 已随仓库提供，仅适合个人/内部发布；如果公开发布并担心被冒名签名，建议另外生成新 keystore，并把 base64 内容与密码放入 GitHub Secrets，同时更新百度控制台的 SHA1。

## 3. 本地发布命令

```bash
# 需要 JDK 17 和 Android SDK
./gradlew :app:assembleRelease
```

输出文件：

```text
app/build/outputs/apk/release/YingSuoPro_V1.0_arm64-v8a_release.apk
```

## 4. 发布前检查

- [ ] `local.properties` 中的 `MAPS_API_KEY` / `MAPS_SAFE_CODE` 为真实值（不要提交该文件）；
- [ ] 百度控制台的 AK 已绑定 `com.github.yingsuopro` 和 release SHA1；
- [ ] `versionName` 与 Release 标题一致；
- [ ] 已在一台真机上安装 release APK，确认地图、搜索、模拟定位和通知正常；
- [ ] 已确认 APK 中不含无关的个人信息或测试数据。
