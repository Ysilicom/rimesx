# RIMES 1.0.0 发布准备

> **2026-10-04 更新：macOS、Android、Windows 的 1.0.0 已公开发布；iOS 1.0.0（34）已上传并提交外部测试审核。当前结果见 [发布记录](RELEASE-1.0.0-PUBLISHED.md)。下文保留 10 月 3 日的准备阶段记录，签名材料缺失、尚未发布等描述属于历史状态。**

2026-10-03，北京时间。维护者决定：**macOS、iOS、Android、Windows 的下一正式产品版本统一为 1.0.0，Linux 保留现状**。本轮准备构建与发布条件，版本号不再追加 preview；构建编号、源码快照、签名、验收与公开状态分别记录。历史安装包和验收记录不改名。

**Windows 补充决定：允许正式版不签名，以可双击安装的 EXE 交付。** 签名不再是 Windows 1.0.0 的发布门槛；仍需提供哈希、如实标注未签名，并验收最终安装、升级与输入体验。

## 已锚定的版本

| 平台 | 产品版本 | 构建身份 | 配置与当前工作区 |
|---|---|---|---|
| macOS | 1.0.0 | 正式包的 CFBundleVersion 独立生成；开发安装保留 `-dev.<commit>[.dirty]` | 主工作区 `VERSION`、`Info.plist`；正式 tag 必须匹配 |
| iOS | 1.0.0 | 下一本地 build **34**；CI 构建编号独立生成 | 主工作区 `platforms/ios/project.yml` 与生成的 Xcode 工程；App / 扩展一致 |
| Android | 1.0.0 | 下一 `versionCode=9` | `codex/android-chinese-input` 工作区的 `platforms/android/VERSION` 与 Gradle |
| Windows | 1.0.0 | PE FileVersion **1.0.0.0**；源码 snapshot SHA-256 | `codex/windows-daily-buffer` 工作区的 `platforms/windows/native/VERSION`；CMake、Broker、关于、清单与 PE 资源一致 |
| Linux | 不调整 | 原生 0.1.0；旧数据预览独立记账 | 此轮不修改 Linux 源码、包版本或发行目标 |

主工作区包含维护者尚未提交的 macOS/iOS 工作；Android、Windows 的最新实现仍在各自工作区。这里没有把旧主线里的 Windows 实现或不存在的 Android 目录当成待发布源码。发布前需要冻结并保存三个工作区的实际输入，不能仅记录各自 HEAD 就漏掉未提交文件。

跨工作区检查：

```bash
python3 scripts/release/check-product-versions.py \
  --android-root /Users/isaac/.codex/worktrees/windows-android/rimes \
  --windows-root /Users/isaac/.codex/worktrees/windows-daily-buffer/rimes \
  --require-all
```

在合并后的完整源码快照中，可将两个路径均指向该快照根目录。该命令只读、只检查版本，不发布。

## iOS 公开邀请

[加入 RIMES TestFlight](https://testflight.apple.com/join/Kdj9RB4q)

Apple 后台的 RIMES Community 已启用 Public Link，没有设置额外人数上限；未登录的邀请页可打开。当前外部构建为 **0.1.0（32）/ Testing**，并不是本地 1.0.0（34）。App Store 的 0.1.0 仍显示 **Waiting for Review**。本轮没有撤回审核、替换测试构建或改变测试组。

后台为 build 32 显示 **41 次 crashes**，但 Crash Feedback 页面为 **No Crash Feedback**。这两个字段不是一回事，不能用后者推断没有崩溃。尚未取得可符号化堆栈；需在 Xcode Organizer / Apple 崩溃数据或测试者设备日志中归因，再验证对应修复及新构建。没有据此计算崩溃率或猜测根因。

## 当前验证

| 范围 | 本轮结果 | 不代表什么 |
|---|---|---|
| 四端版本 | 产品目标、iOS 工程与 build、Android code、Windows 文件版本规则一致 | 不代表已发布 |
| macOS 发布工具 | 64 项测试通过，版本锚定与 shell 语法检查通过 | 本轮没有重新生成并验收已签名、公证的 pkg |
| iOS | 资源 / 隐私检查通过；未签名 Release 设备构建通过，读回 App 与扩展均为 1.0.0（34）；发布配置测试 4 组 / 15 项断言通过 | 没有上传新 IPA，也没有新的真机回归 |
| Android | 核心测试、Debug / Release APK、Release AAB、Release lint 构建通过；release APK 为正式 applicationId、名称 RIMES、非 debuggable，16 KB 对齐通过 | 当前 release 产物未签名，不能交付；dev8 真机证据不能直接替代最终签名包验收 |
| Windows | Young 上 x64 / x86 Release 均通过，各 16 组 CTest；7 个 EXE/DLL 的产品版本 1.0.0、文件版本 1.0.0.0 已读回。未签名 1.0.0 EXE 已生成，10 项归档测试、安装结果接口回归、100 个内置文件校验通过，指定签名时的验证仍有效 | EXE 的只读校验未执行真实安装；最终新装、真实宿主和升级仍需验收 |

[机器可读验证记录](RELEASE-1.0.0-VALIDATION.json)包含版本、源码快照与产物哈希。本地完整日志在 `.build/release-1.0.0-prep/`，默认不入库。Android 实际产物留在其 Gradle build 目录；Windows 使用 Young 上独立目录 `D:\AI\rimes-release-1.0.0-20261003`，不覆盖正在使用的 preview.8。

Windows EXE 准备产物：`.build/windows-unsigned-setup/RIMES-Windows-1.0.0-Setup.exe`（48,264,704 字节），SHA-256 为 `37c81abd8a36aa987f549a179ea29f8fd21faa9c1d32016a4fdd2769a83f5f1e`，旁边有 `.sha256` 文件。构建记录在 `.build/windows-unsigned-setup/`；Young 上为 `D:\AI\rimes-unsigned-setup-20261003\output-r3\`。安装器复用已验证版本的 x64/x86 二进制，另行记录安装器源码哈希；内置从微软官网下载并验证签名的两种架构运行库。此产物尚未发布，也未替换现用安装。

## 正式 release 尚需完成

| 平台 | 具体剩余条件 |
|---|---|
| macOS | `macos-release` / `macos-publish` 的 reviewer 与禁止管理员绕过配置已存在，8 个签名 / 公证 Secret 名称齐全；其内容和有效期尚未通过新包签名验证。本机仅见 Apple Development 与 Developer ID Application 身份，未见 Installer。还需处理 `yoyo-SOURCE.md` 中未记录授权的随包内容、冻结源码，完成最终 pkg 签名、公证及同包新装 / 升级验收。 |
| iOS | 当前 `ios-app-store` CI 环境没有签名 / API Secrets。交互登录 Apple 后台不等于 CI 已获得签名材料。补齐正式签名链，核查 build 32 崩溃；新包验证宿主、首次高度、Buffer / Return、方案导入和隐私。现有 0.1.0 审核另行处理，现有流程不会自动撤回它。 |
| Android | 选定并备份长期正式 keystore；当前 4 个签名环境变量均未配置。`.debug` 与正式 applicationId 不同，需验收新装与数据迁移；最终签名包重跑输入 / AI / 隐私检查并补最低系统、多厂商覆盖。详见 Android 工作区的 `platforms/android/RELEASE.md`。 |
| Windows | 关闭冷 Broker 原始字母泄漏、Edge 普通组字切框残留；补真实服务、常用宿主、多屏 / DPI、锁屏及日用。使用未签名 EXE 安装入口，验证新装、升级后 DLL 退出、回退与卸载。签名为可选项；本轮无需办理代码签名证书。 |

## 私有源码与公开二进制

先保存私有、可追溯源码快照，再构建并按各平台要求完成签名和最终包验收；Windows 本轮允许未签名。公开包附版本、构建号、文件哈希、实际签名状态、功能边界及第三方许可材料。自有新增源码的公开时间不因本轮版本修改而提前。

**现有 macOS / iOS tag 工作流仍要求公开 main 中的提交**；向公开仓库推送该源码与 tag 会公开新源码。若继续采用“先二进制、后新应用源码”，正式发行前必须使用私有构建入口及单独的公开发行索引，不能直接运行旧 tag 发布命令，也不能把旧源码 tag 冒充新包的构建来源。macOS / iOS 的签名、审核和发布保护保留；Windows 按维护者决定调整签名规则。

各平台完成本端条件后可分别发布正式 1.0.0；无需等待 Linux，也不要求不同平台同一天发行。公开链接已补入本地 README，其他准备修改仍在本地，尚未提交、推送、创建 tag 或公开 release。
