# Han1meViewer-revive

<p align="center">
  <img src="icon/icon_han1me_viewer-rurires.png" width="100" alt="Han1meViewer-revive icon" />
</p>

[![CI](https://github.com/SuperHeiHun/Han1meViewer-revive/actions/workflows/ci.yml/badge.svg?branch=feat/new_download)](https://github.com/SuperHeiHun/Han1meViewer-revive/actions/workflows/ci.yml)
[![License](https://img.shields.io/github/license/SuperHeiHun/Han1meViewer-revive)](LICENSE)

基于 [YenalyLiew/Han1meViewer](https://github.com/YenalyLiew/Han1meViewer) 的社区维护分支，适配新版站点的浏览、搜索、播放与评论。

> 仅供学习 Android 开发与网页解析，不提供影片内容或下载源。内容仅面向成年人，请遵守所在地法律。

## 下载

- 从 [Releases](https://github.com/SuperHeiHun/Han1meViewer-revive/releases/latest) 的 **Assets** 下载 APK。
- 也可在 [Actions](https://github.com/SuperHeiHun/Han1meViewer-revive/actions/workflows/ci.yml) 的成功构建中下载 Artifact 并解压。Fork 需先启用 Actions；Fork 仅生成构建产物，不自动发布。
- 本分支与上游签名不同，可能无法直接覆盖安装；卸载前请先备份数据。

## 主要功能与用法

### 播放

- 支持清晰度切换、在线／离线播放、关键帧、系列播放清单与正序／倒序。
- 齿轮打开底部半屏设置：**播放模式、比例、自动旋转**。播放模式含列表循环、单片循环、播完暂停；默认播完暂停。无系列或离线时列表循环视为单片循环。
- 自动旋转默认关闭，仅在全屏中跟随当前／下一视频的实际横竖方向，不主动进入全屏。“设置 → 播放器设置 → 自动全屏”为独立开关，默认关闭，同次播放手动退出后不反复进入。
- 中间上划沿用原全屏方式；竖屏普通播放器中间下滑进入竖屏全屏。单击显隐控制栏，横滑调进度，左右调亮度／音量，长按临时倍速；可在播放器设置中配置分区双击、步长与独立手势。默认仍为双击播放／暂停、长按按原倍率加速，松手恢复原速。
- 系列选片与列表循环复用当前播放页；**相关推荐新开播放页**，返回可回到原页。竖屏全屏清单采用底部半屏，横屏采用右侧面板。

### 搜索、历史与本地列表

- 首页搜索入口与侧边栏打开统一搜索历史页，输入可筛选历史，提交才发起搜索；**留空搜索可查看最新上传**，结果页支持高级筛选。
- 支持本地标签补全、多标签与标题混合搜索，例如 `1080p 60FPS 作品名`。`"ASMR"` 强制按标题搜索，未知标签用 `#标签`，含空格用 `#"Custom Tag"`。历史保留搜索文字，不恢复另选的高级筛选条件。
- 本地收藏／待播放无需登录，与网站列表独立；待播放按加入顺序保存，不自动移除或下载。长按视频卡片可复制、加入待播放或屏蔽标签／作者。
- 本地屏蔽按完整名称匹配，忽略大小写与全半角，但不转换简繁；仅过滤首页、搜索和相关推荐，不改动已保存列表、系列、历史或下载。资料不足时会额外读取详情，失败可刷新重试。

### 下载、备份与隐私

- “设置 → 下载设置 → 下载路径”通过系统选择器更改文件夹。**只影响新任务**，旧任务保留原位置且不迁移文件；请保留原目录及授权。目录需支持随机读写，下载路径与授权不纳入备份。
- “设置 → 备份与隐私”支持本地文件／WebDAV，按需选择观看历史、搜索历史、应用设置、本地收藏与待播放。恢复合并去重，空类别不清空已有数据；同名设置覆盖，部分设置需重启，恢复域名可能退出登录。
- 备份上限 **20 MiB／100,000 项**，不含登录凭据、Cookie、下载文件／路径、WebDAV 配置或关键帧文件。**备份 JSON 未加密**，请妥善保存；恢复并非跨数据库的全局事务，部分失败可重试。
- WebDAV 使用已有目录下的完整 **HTTPS 文件地址**与 HTTP Basic 认证；上传覆盖前确认，不建目录、不跟随重定向、不自动同步。连接配置在本机加密保存，换设备需重配；尚未完成真实服务器联调。
- 隐私模式仅停止新增搜索／观看历史，不删除旧记录，也不影响手动收藏、下载和显式恢复；**不是网络匿名模式**。
- “设置 → 更新”可禁用自动检查，仍保留手动更新；更新说明支持 Markdown。不含 Analytics、Crashlytics、性能监控或事件埋点，Firebase 仅保留更新配置使用的 Remote Config。

## 构建

需要 **JDK 21、Android SDK 35**。本地没有 Firebase 配置时，可复制示例文件：

```bash
cp app/google-services.json.example app/google-services.json
./gradlew :app:assembleDebug --max-workers=2 --console=plain
```

Windows 使用 `gradlew.bat`；正式构建将任务替换为 `:app:assembleRelease`。

- Debug：`app/build/outputs/apk/debug/Han1meViewer-revive-v1.0.3-debug.apk`
- Release：`app/build/outputs/apk/release/Han1meViewer-revive-v1.0.3.apk`
- Release 使用仓库公开测试密钥，**不适用于正式分发或 Google Play**；自行分发请更换密钥和签名配置。
- 官方仓库 `feat/new_download` 推送后由 CI 自动发布；说明读取 `changelogs/<版本号>.md`，缺失或为空即停止，已发布版本不会覆盖。新版本单独建日志，不维护根目录索引。
- Fork 默认使用示例 Firebase 配置并关闭 Remote Config，更新直接检查 Releases；真实配置可通过 Secret `HA1_GOOGLE_SERVICES_JSON_BASE64` 提供。

## 验证与限制

- 新增回归用例未运行或单独编译，未启动应用做设备功能验证；构建／安装成功不代表功能验证通过。
- 登录后列表、订阅、评论等账号交互尚未完整线上验证，WebDAV 尚未实机联调。
- Cloudflare 验证需手动完成，依赖当前网络和 User-Agent；更换网络后可能需要重试。站点部分预览页可能不可用。

## 致谢与许可

感谢 [Yenaly Liew](https://github.com/YenalyLiew) 及上游贡献者。技术背景见 [README_TECH.md](README_TECH.md)，项目沿用 [Apache License 2.0](LICENSE)。
