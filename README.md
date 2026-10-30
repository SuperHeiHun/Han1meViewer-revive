# Han1meViewer-revive

<p align="center">
  <img src="icon/icon_han1me_viewer-rurires.png" width="120" alt="Han1meViewer-revive icon" />
</p>

<p align="center">
  <a href="https://github.com/SuperHeiHun/Han1meViewer-revive/actions/workflows/ci.yml">
    <img src="https://github.com/SuperHeiHun/Han1meViewer-revive/actions/workflows/ci.yml/badge.svg?branch=feat/new_download" alt="CI" />
  </a>
  <a href="LICENSE">
    <img src="https://img.shields.io/github/license/SuperHeiHun/Han1meViewer-revive" alt="License" />
  </a>
</p>

**Han1meViewer-revive** 是基于 [YenalyLiew/Han1meViewer](https://github.com/YenalyLiew/Han1meViewer) 的社区修复分支。

上游项目已停止维护。本分支针对目标站点的新版页面重构了网页解析、搜索参数和部分接口，使应用可以继续浏览新版页面。

> 本项目仅供学习 Android 开发与网页解析技术使用，不提供任何影片内容、下载源或使用教程。  
> 内容仅面向成年人，请遵守你所在地区的法律法规。

---

## 下载

推荐直接从 [Releases](https://github.com/SuperHeiHun/Han1meViewer-revive/releases/latest) 下载最新 APK：

1. 打开 Releases 页面。
2. 在最新版本的 **Assets** 中下载 `Han1meViewer-revive-v*.apk`。
3. 安装即可；如果已安装原版 Han1meViewer，可能需要先卸载。

CI 构建也会在 Actions 中保留一份构建产物作为备份：

1. 打开 [Actions](https://github.com/SuperHeiHun/Han1meViewer-revive/actions/workflows/ci.yml) 页面。
2. 选择最新一次成功的 `CI` 构建。
3. 在页面底部下载 `Han1meViewer-revive-<commit>` 构建产物（zip）。
4. 解压后得到 `Han1meViewer-revive-v*.apk`，安装即可。

也可以手动触发构建：进入 Actions → `CI` → `Run workflow`。

> 如果你是刚 Fork 本仓库，请先打开 Actions 页面启用工作流；若推送后没有自动运行，可手动执行 `Run workflow`。Fork 仓库只会上传 Actions Artifact，不会自动发布 Release。

## 本次修复内容

- 适配新版首页结构，恢复“最新上市”“最新上传”“裏番”“Motion Anime”“3DCG”“他们正在看”等栏目。
- 新版首页已移除“中文字幕”“本日排行”“本月排行”，改为通过搜索接口并发补齐，每栏显示前 12 项。
- 重写普通搜索、类型（genre）搜索、相关视频和播放列表解析。
- 搜索参数改为新版站点的 `date`、`duration`、`tags[]` 组合，移除已失效的 `year`、`month`、`brands[]`。
- 新增第 7 组搜索标签「情境场所」，并更新全部搜索选项为当前站点数据。
- 修复视频页标题、观看次数、上传日期、点赞数、标签、社团信息和播放源解析。
- 修复评论、回复、子评论 ID 解析，避免未登录状态下回复时崩溃。
- 修复预览页左右切换按钮检测。
- 删除已返回 404 的 `deletePlayitem` 接口，改用 `playlist/delete`。
- 恢复“关于”页面，并修复设置中“关于”无法点击的问题。
- 检测到站点 Cloudflare 人机验证时，应用会弹窗引导手动完成验证；验证页保持 WebView 默认 UA（Turnstile 要求会话内 UA 与浏览器特征一致），完成后同步 `cf_clearance` 等 Cookie 并自动重试原页面。
- 重试时自动切换到验证时使用的 UA，并补齐浏览器请求头（Client Hints、`Sec-Fetch-*`），避免再次被 Cloudflare 拦截。
- 同步过的 Cookie 会本地保存，验证仍有效时应用重启无需再次验证。

## 功能

- 首页栏目、每日/每月排行、中文字幕筛选
- 关键词搜索、类型搜索、标签/日期/片长筛选
- 视频播放、清晰度选择、播放列表、相关推荐
- 评论查看与回复
- 收藏、稍后再看、自定义播放列表、订阅
- 观看历史、下载管理、本地关键帧
- 简繁体与英文界面

## 构建

### 环境要求

- JDK 21
- Android SDK 35
- Windows、macOS 或 Linux

### 本地构建

```bash
git clone https://github.com/SuperHeiHun/Han1meViewer-revive.git
cd Han1meViewer-revive

# 本地没有真实 Firebase 配置时，可以使用仓库里的示例配置
cp app/google-services.json.example app/google-services.json

# Debug 包
./gradlew assembleDebug

# Release 包
./gradlew assembleRelease
```

Windows 下把 `./gradlew` 换成 `gradlew.bat`。

构建产物：

- Debug：`app/build/outputs/apk/debug/Han1meViewer-vdebug+1.apk`
- Release：`app/build/outputs/apk/release/Han1meViewer-revive-v*.apk`

### 签名说明

本分支使用仓库中的公开测试密钥 `app/keystore/Han1meViewer-revive.jks` 为 Release 包签名，方便 Actions 在没有额外配置的情况下直接出包。

- 该密钥仅用于社区测试构建，**不适用于 Google Play 或任何正式分发**。
- 如需使用自己的密钥，请替换 `app/keystore/Han1meViewer-revive.jks`，并修改 `app/build.gradle.kts` 中的 `keyAlias` 和密码。
- 由于签名密钥与上游不同，安装本分支前可能需要先卸载原版 Han1meViewer。

### GitHub Actions

工作流文件：`.github/workflows/ci.yml`

- 推送代码或手动触发时构建 `assembleRelease`。
- 在 `SuperHeiHun/Han1meViewer-revive` 仓库中，`feat/new_download` 分支构建成功后会自动发布 GitHub Release：先创建 draft，上传 APK，再正式发布。
- Release tag 形如 `v0.15.5+261028058`，应用内更新检查会读取 `+` 后的版本号。
- Fork 仓库只上传 Actions Artifact，不会自动发布 Release。
- 默认使用 `app/google-services.json.example` 作为 Firebase 配置占位，因此全新 Fork 也能直接出包。
- 使用占位配置时，构建会自动关闭 Firebase Analytics、Crashlytics 和 Remote Config，避免无效 API Key 导致启动崩溃；应用内更新会直接检查 GitHub Releases。
- 如需真实 Firebase 配置，在仓库 Secrets 中添加 `HA1_GOOGLE_SERVICES_JSON_BASE64`（`google-services.json` 的 Base64 内容）。

## 已知限制

- 登录后的“我的列表”、自定义播放列表、订阅、发表评论/点赞等交互，因缺少测试账号，尚未做完整线上验证。
- 站点 `/previews/202605` 之后的预览页目前返回 500，最新可用月份为 `202604`；新版首页也已注释掉预览入口，属于站点侧停止维护。
- 应用内更新检查默认指向本仓库的 Releases；如果最新 Release 的版本号不高于当前安装版本，则不会提示更新。
- Cloudflare 人机验证依赖站点实际下发的挑战页，无法稳定复现；如验证页空白或验证后仍被拦截，可再次点击“开始验证”（通常会自动通过），或切换网络后重试。
- 验证会绑定当前网络的出口 IP 与应用内验证页的 User-Agent；使用代理或切换网络后可能需要重新验证。
- 本项目未在真机上做完整的运行时冒烟测试，如遇问题请提交 Issue（需先在仓库设置中开启 Issues）。

## 致谢

- 原作者 [Yenaly Liew](https://github.com/YenalyLiew) 及所有上游贡献者。
- 上游项目：[YenalyLiew/Han1meViewer](https://github.com/YenalyLiew/Han1meViewer)
- 技术实现说明：[README_TECH.md](README_TECH.md)

## 许可证

本项目沿用上游的 [Apache License 2.0](LICENSE)。
