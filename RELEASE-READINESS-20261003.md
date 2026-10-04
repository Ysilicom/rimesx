# RIMES 全平台版本与 1.0.0 发布评估

> **本轮决策更新**：macOS、iOS、Android、Windows 下一版均锚定 **1.0.0 正式版**，Linux 排除在本轮之外。版本配置与构建检查已开始实施，详见 [当前发布准备](RELEASE-1.0.0.md)。以下盘点保留原时间点证据；其中继续 preview 的建议已被本次维护者决定取代。TestFlight 公开邀请已确认启用，当前为 0.1.0（32）。

核查日期：2026-10-03（北京时间）。这是版本盘点与发布方案；Android 已补充同日 dev8 本地接入与真机验证，其他平台仍采用最初盘点时的证据。没有新增公开发行。

本次读取了当前工作区、Android / Windows 独立工作树、已有构建产物、验收记录、GitHub Releases / Issues / Actions，以及平台官方分发文档。未重新运行五个平台的完整验收。维护者反馈 macOS、iOS、Android 已有成熟日用体验，Windows / Linux 稍弱；下文把这份体验反馈与工程交付证据分别记录。

**结论**

可以先分发新版本二进制，再公开新增的自有应用源码；需要同时满足第三方许可和各平台分发要求。现有公开源码的许可不因这次分发策略而撤回。macOS / iOS 可以开始收敛 1.0 候选版本；Android 已在 dev8 补上 CometAPI 真实 AI 和句子翻译验证，可以收敛正式签名与兼容性验收；Windows / Linux 仍适合预览阶段。

**1. 当前版本账**

| 平台 | 当前核实的开发 / 产物版本 | 对外分发状态 | 版本来源和注意事项 |
|---|---|---|---|
| macOS | 本机安装包 `0.5.4-64-gb02fe9c-dirty`，build `1790867108` | GitHub 最新公开包为 `0.5.0-preview.3`，2026-09-16 发布，明确标记未签名预览 | 开发版由 Git 身份生成；源码 `Info.plist` 的 `0.0.0-dev` 是占位值，不能当作产品版本。安装包使用 Apple Development 签名，不能当作 Developer ID 正式发行包。 |
| iOS | 当前主工作区配置、设备构建产物与模拟器产物均为 `0.1.0 (33)`；另有 `0.1.0 (32)` 的归档 / 导出 IPA | 本次 App Store Connect 返回登录页，未实时确认当前商店与 TestFlight 状态 | `project.yml` 是本地配置来源。README 仍写 build 22，属于陈旧记录；导出 IPA 不等于上传成功、测试可用或上架。 |
| Android | `0.1.0-dev.8`，`versionCode=8`；已覆盖安装 `org.scholay.rimes.android.debug` / debug | 当前 GitHub Releases 没有 Android 应用包 | 分支 `codex/android-chinese-input`，基于 `0099461` 的未提交改动；dev8 APK 与真机读回字节一致，CometAPI 真实键盘验证通过。 |
| Windows | 原生工程 `0.2.0`；最新已核实包清单为 `0.2.0-preview.8`，源码 `48c435b`，含 x64 / x86 TSF | 当前 GitHub Releases 没有原生 Windows 应用包 | 分支 `codex/windows-daily-buffer`，HEAD `ab92666`。Young 的安装及宿主验证见 10 月 3 日记录，本次未重新连接实机复测。 |
| Linux | 原生 IME 的 CMake 与 `ime/VERSION` 均为 `0.1.0` | 当前 GitHub Releases 没有原生 `fcitx5-rimes` 安装包 | `platforms/linux/VERSION=0.1.0-preview.1` 属于另一套数据预览入口，不应覆盖原生 IME 版本。 |

历史公开的 `platform-preview-v0.1.0` 仅包含 Windows / Linux 的输入方案数据包，不是两个原生客户端的版本。Git tag、CMake 工程版本、手机 build number、APK versionCode、已安装包和公开 Release 必须分别记账。

截至本次读取，主工作区仍为 `b02fe9c`，相对 `origin/main` 领先 15 个提交、落后 README 更新 1 个提交，且有大量未提交工作。Android / Windows 的最新实现位于各自工作树。用公开 main 直接构建，不能得到维护者正在体验的完整本地版本。

证据：[公开 Releases](https://github.com/scholay/rimes/releases)、[iOS 项目配置](/Users/isaac/Documents/05-dev/apps/rimes/platforms/ios/project.yml)、[Android APK 配置](/Users/isaac/.codex/worktrees/windows-android/rimes/platforms/android/app/build.gradle.kts)、[Windows 包验收](/Users/isaac/.codex/worktrees/windows-daily-buffer/rimes/platforms/windows/native/BUFFER-BINDING-ACCEPTANCE-20261003.md)、[Linux 原生版本](/Users/isaac/Documents/05-dev/apps/rimes/platforms/linux/ime/VERSION)。

**2. 统一朝 1.0.0 收敛的版本方案**

建议把 1.0.0 定义为每个平台明确承诺的功能已经稳定、可安装、可升级和可追溯。五个平台使用相同目标版本，分别记录构建与验收，不要求相同界面、所有插件同时移植或同一天发布。

| 平台 | 建议候选标识 | 正式产品版本 / tag 约定 | 单独递增的构建身份 |
|---|---|---|---|
| macOS | `1.0.0-preview.N` | `1.0.0` / `v1.0.0`，保留现有 macOS tag 兼容性 | CFBundleVersion、准确源码快照 |
| iOS | TestFlight 中的 `1.0.0 (build)`；版本字段只填数字，候选身份由渠道标识 | `1.0.0` / `ios-v1.0.0` 用于正式发行流程 | CFBundleVersion，先检查 App Store Connect 已用值 |
| Android | `1.0.0-preview.N` | `1.0.0` / 拟用 `android-v1.0.0` | 单调递增 versionCode、正式 applicationId 和签名身份 |
| Windows | `1.0.0-preview.N` | `1.0.0` / 拟用 `windows-v1.0.0` | 包清单版本、文件版本和源码身份一致 |
| Linux | `1.0.0-preview.N`；Debian 包用 `1.0.0~preview.N` 保证低于正式版排序 | `1.0.0` / 拟用 `linux-v1.0.0` | Debian 包修订号、架构和源码身份 |

这些是待实施约定。Android / Windows / Linux 的新 tag 名称尚无对应公开发行流程；现有 iOS tag 流程会提交 App Review 并在通过后自动发布，不能用它来表示“只做一次 TestFlight 测试”。一端修复不要求其他端同步增加版本。

每个公开包应带有发行清单：平台、支持系统 / 架构、产品版本、构建编号、私有源码快照 ID、构建配置、包 SHA-256、签名身份、已执行验收及已知限制。私有快照必须保存所有实际构建输入，包括目前尚未提交的文件。

**3. 哪些地方尚未达到正式 1.0**

| 平台 | 已有基础 | 正式 1.0 前的关键工作 | 判断 |
|---|---|---|---|
| macOS | 维护者报告日用成熟，当前开发安装可运行 | 冻结包含本地修改的源码快照；生成 Developer ID 签名、公证的确切 pkg；验证新装、保留数据升级与输入源迁移；处理下述 yoyo 授权边界；将 Typinator 共用问题列明支持范围 | 可进入 1.0 候选收敛；当前开发安装不能直接代表正式发行包 |
| iOS | 维护者报告日用成熟；最新构建为 build 33，已有多轮键盘、导入、布局与统计验证记录 | 选定最终 build，更新陈旧版本说明；在该 build 确认键盘首次高度、微信 / 浏览器宿主、Return / Buffer、导入方案与隐私行为；核对 TestFlight / 商店实际状态及 App / 键盘扩展签名与版本 | 可进入 1.0 候选收敛；本次没有发现必须追齐桌面功能才可发布的理由 |
| Android | dev7 输入与设置基线；dev8 新增真实 CometAPI 快问、润色、翻译和 202 项真机流程检查 | 从 debug 包切到正式包身份及长期签名，验证数据迁移；补最低 Android 8.0 与其他厂商覆盖；确认服务可用地区与功能承诺；最终版持续试用 | 已补齐真实文字 AI 的主要缺口，可以收敛候选；图片生成和未验收模型等不应纳入已完成范围 |
| Windows | preview.8 已有双架构各 16 组 CTest，以及真实 x64 / x86 宿主和 Edge 的 Buffer 捕获、暂停、重绑、投递证据 | 冷启动 Broker 未就绪时原始字母进入宿主；Edge 普通组字切框残留；真实 API、常用宿主、缩放 / 多屏、锁屏恢复、升级后旧 DLL 退出及一天试用；完善面向普通用户的安装与签名 | 暂不建议正式 1.0，继续预览 |
| Linux | 原生 Fcitx5 输入、基础 Buffer、Capsule 笔记已实现；相关公开源码 CI 有成功记录 | 修复或收敛 #44 的拖动后切输入框捕获问题；处理 #43 的部署期间退出体验；为声明支持的 X11 / Wayland、发行版和宿主完成确切包验收；明确 AI / 翻译未移植 | 暂不建议正式 1.0，继续预览 |

Android 补充：dev7 的 AI 仍是本机 Mock；本次 dev8 已接入 CometAPI、加密密钥和真实流式回复，真机通过快问、润色与中英句子翻译，以及手动发送、取消、换框和隐私保护检查。未启用 AI 翻译时仍是 CC-CEDICT 词 / 短语查找。手机原网络曾被服务以 `region_restricted` 拒绝，维护者改变网络后成功；可用地区必须由服务商确认。画画目前仍只输出文字提示词，其他模型与真实作诗未在此次验收。配色、动画、统计卡片、Mailbox / Capsule 的跨平台完整对齐，只有被纳入该端 1.0 承诺时才构成阻断。

相关证据：[Android 当前说明](/Users/isaac/.codex/worktrees/windows-android/rimes/platforms/android/README.md)、[dev8 CometAPI 验收](/Users/isaac/.codex/worktrees/windows-android/rimes/platforms/android/validation/2026-10-03-dev8.md)、[Windows 日用缺口](/Users/isaac/.codex/worktrees/windows-daily-buffer/rimes/platforms/windows/native/DAILY-PREVIEW.md)、[Linux 当前能力](/Users/isaac/Documents/05-dev/apps/rimes/platforms/linux/ime/README.md)、[Linux #43](https://github.com/scholay/rimes/issues/43)、[Linux #44](https://github.com/scholay/rimes/issues/44)。

本报告不会用历史未填的验收表直接否定维护者今天的使用反馈，也不会把一次新宿主成功当成所有系统版本、安装路径与旧问题都已通过。

**4. 先发二进制、后公开新增源码：许可边界**

MIT 本身没有要求分发二进制时同时提供源代码，要求保留相应版权与许可声明。因此自有应用代码可以暂缓公开；仍须核对实际包含的第三方组件。[MIT 原文](https://opensource.org/license/mit)

仓库声明包含 GPL / LGPL 的方案、词库、Lua 和语言模型。对于受对应源码义务约束的分发，应提供与实际分发版本对应的材料，不能以“以后再放应用源码”代替。如果它们构成独立聚合，不必当然把全部自有应用代码都变成 GPL；若存在紧密链接 / 集成，边界需要按实际组合审查。建议随包附完整第三方许可，并提供经核对的第三方源材料包及修改记录；专项许可审查应确认这足以满足具体组合的义务。[GNU FAQ](https://www.gnu.org/licenses/gpl-faq.en.html#MereAggregation)、[对应源码说明](https://www.gnu.org/licenses/gpl-faq.en.html#DistributeExtendedBinary)

Android CC-CEDICT 及其派生索引使用 CC BY-SA 4.0；现有实现已经记录来源、转换及署名。正式包继续保留这些材料。iOS 最小词库与运行库的许可清单和桌面并不完全相同，应按各平台实际包逐项检查，不能整包仅写 MIT。

发现一项具体待处理内容：`yoyo-SOURCE.md` 明确写有尚未记录作者的许可 / 再分发授权。当前已安装 macOS 应用的 `Contents/SharedSupport` 中实际包含该组方案和词库；GitHub 当前返回上游 `Rayalizing/yoyo` 的 license 为 null。发布新包前应取得并保存授权依据，或在发行包中排除该组内容。仅署名和“作者提出再删除”不能替代事先获得的分发权限。[本地来源记录](/Users/isaac/Documents/05-dev/apps/rimes/rime-data/licenses/yoyo-SOURCE.md)、[GitHub 的无许可证说明](https://choosealicense.com/no-permission/)

这是基于工程组成和许可文本的发行判断，不代替对 GPL / LGPL 组合边界的法律审查。公开过的 MIT 源码仍按原许可供他人使用。

**5. 不公开新应用源码时，各平台怎么分发**

| 平台 | 可用路径 | 当前需要处理的事项 |
|---|---|---|
| macOS | GitHub Release 分发 pkg，或其他公开下载页 | 面向正式 1.0 使用 Developer ID 签名、公证和同包验收；当前 Apple Development 安装包不作为通用正式包 |
| iOS | TestFlight 邀请 / 公共链接；正式版走 App Store | GitHub 可以托管 IPA 文件，但普通用户不能因此直接安装；Ad Hoc 仅面向登记设备。TestFlight 构建有 90 天有效期，外部测试可能需要审核 |
| Android | GitHub / 网站分发 release APK；应用商店可另行接入 | 使用正式 applicationId、长期签名及递增 versionCode；当前 `.debug` 包身份变化要处理用户词库 / 设置迁移，不能假设覆盖安装 |
| Windows | 分发原生安装器，预览阶段也可以提供清楚标注的 ZIP 包 | 当前包为未签名内部预览和 PowerShell 安装流程；正式版补适合普通用户的安装、更新、回退、卸载及签名 |
| Linux | 按发行版 / 架构提供 `.deb` 等软件包 | 公布支持矩阵、依赖、数据目录、校验值与升级 / 卸载行为；当前 `.deb` 脚本已有原生 IME、Buffer、Capsule 的打包范围 |

依据：[Apple 分发方式](https://developer.apple.com/documentation/xcode/distributing-your-app-for-beta-testing-and-releases)、[TestFlight](https://developer.apple.com/help/app-store-connect/test-a-beta-version/testflight-overview/)、[Apple 已登记设备分发](https://developer.apple.com/documentation/xcode/distributing-your-app-to-registered-devices)、[Android 直接发布](https://developer.android.com/studio/publish)、[Android 签名](https://developer.android.com/studio/publish/app-signing)。

截至 2026-10-03，Android 官方说明 2026-09-30 起的首轮开发者验证覆盖部分地区的参与商店；直接侧载暂不受该轮要求影响，2027 年计划扩大。该规则不要求公开应用源码；发行前仍应按目标地区重新确认。[当前官方 FAQ](https://developer.android.com/developer-verification/guides/faq)

**6. 推荐的工程实施顺序**

1. 确定每个平台 1.0 的功能承诺，保留 macOS / iOS / Android 的已成熟输入体验，继续收敛 Android 的正式发行与兼容性，并解决 Windows / Linux 的输入上下文问题。
2. 将实际待发布源码冻结成可追溯的私有快照，保存工具链、依赖、配置和验收证据。保留现有工作区与平台分支；源码冻结不等于公开 push。
3. 建立“私有源码构建 → 平台签名 / 验收 → 公开二进制分发”的流程。公共发行信息只包含说明、校验值、许可、对应第三方源材料和必要的构建身份。
4. GitHub 可以附加独立二进制资产，但 Release 仍关联 tag，且自动提供该 tag 的源码 ZIP / TAR。把新源码 commit 的 tag 推到公开仓库会公开该源码，不能只因未合入 main 就认为它仍私有。发行用索引 / 仓库必须明确自动源码压缩包与新二进制源码的关系。[GitHub Releases](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases)、[上传二进制资产](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository)
5. 现有 macOS 流程要求正式 tag 对应公开 main，iOS 流程要求 tag 在公开 main 历史中，并会执行提交审核。要实施私有构建，需要增加独立流程与清晰的源码 / 产物身份约定，继续保留签名、同包验收和发布门禁。采用单独的公开发行仓库时，还需迁移 macOS 更新源和下载入口；不能直接给旧源码打 1.0 tag 来冒充新包来源。
6. 先完成 macOS / iOS 的 1.0 候选包；Android 在正式签名与功能范围处理完成后加入。Windows / Linux 保持 `1.0.0-preview.N` 的目标线，关闭核心输入问题后再转正式 1.0.0。各端达标即可分别发布，全部达标后再宣告“全平台 1.0.0”。

最初盘点仅新增本报告；后续版本配置、工作流与本地构建准备见上述更新。历史发行与验收记录不改写，远端安装包尚未新增。
