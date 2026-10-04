# Windows macOS 样式还原 · 第二轮 · 2026-10-03

**preview.6 已安装到 Young。Buffer 控件、双轨留白、设置主题详情与原生标题栏完成第二轮细化；双架构各 16 组 CTest 通过，安装版输入、投递和主题保存通过。本记录不代表 Windows 日用验收完成。**

## 源码与产物

- Windows 分支 `codex/windows-daily-buffer`；本轮基线 `92de58f6af7583451abbf465cc965e6c90623903`，实现提交 `6ec52332b304d8a6952e526f96c48d1e3f44c621`。后续文档提交不改变包内源码身份。
- macOS 参考为主工作树的 Swift UI 与主题数据，HEAD `b02fe9ccaae34bef927e8f7e8f78c2441d1e91c3`，包含已有未提交设置改动。只读参考，未改动 macOS / iOS / Android 文件。参考文件哈希见本地 `mac-reference-hashes.json`。
- Mac 图像沿用 [第一轮](STYLE-ACCEPTANCE-20261002.md) 已记录的生产离屏绘制器，SHA-256 `cf7de14e5bdb2093abed1f04d71cf25b5b7225c6647197e52c7023bc6a476ae7`；不是当前 Mac 工作树重新构建的截图。当前按钮归属以 Swift 源码为准，旧图片可能保留较早布局。
- 准确源码 ZIP：66,245,150 字节，核对 1,333 项源码清单记录；SHA-256 `2984d0b89dbd117cfcdc93e52b4eff933120b5ee1fa63b1d8204e09773ffb305`。
- `RIMES-Windows-0.2.0-preview.6.zip`：23,071,419 字节；SHA-256 `6c0c52ba2ab4b8b7bd298082d9da1408e2b56c4876bb25d48abf70f289f183de`。拉回 Mac 后，96 项包内文件的字节数和 SHA-256 全部匹配。

| 二进制 | SHA-256 |
| --- | --- |
| x64 TSF DLL | `6dcf56eab7b25ed8f299cdc9117ac1661b5a315b4f099cf0d6625c52a5a9cd17` |
| x86 TSF DLL | `9a5fcc4bd5eb91e185177ce2f998388966f626d307993118f0882c7558ad9d88` |
| 共用 x64 Broker | `2b9de2e9a839f90ad70a1259bc21dbfc33a94189a0fccb3f82263511cb61c785` |
| x64 样式预览程序 | `12027faed9109a03fe1be5182220a30fe2fb3097d29899d44c6aaac9c18e430b` |

## 本轮变化

- Buffer 粘贴、复制、发送归于主轨；有结果时全部位于结果轨右端。普通输入隐藏复制按钮时不留空位。按钮保留轻背景和细边线，禁用、悬停与按下状态可区分；模式选择增加独立的下拉分隔区。
- 两轨留在外框内，底部保留描边空间；总高度仍为 105 DIP，内部轨高按可用空间缩为 31 DIP。普通输入仍为 73 DIP。文本垂直居中，移除每块多余的 8 DIP 宽度，按实际文字测量及 5 DIP 间距排列；普通块下划线使用边界语义颜色。
- 设置侧栏未选中项降低亮度，标题字重和主题类别基线对齐。四张主题卡增加 22 × 22 DIP 详情按钮；详情优先命中、使用独立弹层，点击详情不选择主题。Escape、外部点击、失焦均关闭详情。
- 设置页操作要求同一控件上有效的按下和抬起；跨控件拖动、取消、丢失捕获或布局变化均撤销旧按压。键盘可导航至详情并打开。
- Windows 11 原生标题栏随四套主题变色，减少深色页面上方的亮色断层。没有替换窗口系统按钮；字体光栅化和阴影仍由 Windows 绘制。
- 未修改投递确认、Return 长按、API 请求版本冻结或目标安全契约。

## 准确提交的最终自动化

Young 为 Windows 11 Pro x64 Build 22631。全新源码目录 `D:\AI\rimes-windows-style-20261003\release-6ec52332b304`，最终构建运行于 09:30:58–09:34:07（UTC+08:00）；下列步骤退出码均为 0。

| 检查 | 结果 |
| --- | --- |
| 全部源码清单及完整产品数据 SHA-256 | 通过；完整词库 60 文件，可独立部署 |
| Python 产品数据边界测试 | 9 项通过 |
| 安装脚本 PowerShell 语法解析 | 通过 |
| MSVC Release x64 / x86，`/W4 /WX` | 两架构通过 |
| CTest | 每架构 16 / 16 通过 |
| 产品探针 | 每架构五方案与两项繁体输出，共 7 样例通过 |
| 模拟 TSF / 隔离 Session 0 Broker | 两架构输入、延迟目标撤销通过 |
| 最终 ZIP 与回收验证 | 96 项文件通过 |

新增的设置 UI 测试只创建隔离测试窗口和回调，不连接真实 Runtime、不访问用户设置或凭据。覆盖详情优先命中、弹层屏蔽、最小宽度、无按下的抬起、跨卡片拖动、取消捕获、Escape 保留设置窗口及未保存关闭时恢复主题。Buffer 布局测试覆盖三种宽度、模式与等待组合、外框包含关系、文字余量和按钮位置。

中间 review / polish 构建也在两架构通过；最终结果仅取全新 `release-6ec52332b304`。一次上传完成前的启动没有找到审计脚本，未开始构建；原日志单独保留。未重跑未修改的完整 API 回环套件和全部 19 项安装生命周期，原证据仍属于 [10 月 1 日验收](ACCEPTANCE-20261001.md)。

## 安装版真机验收

GUI 观察、鼠标及按键均通过 Windows App / CUA。SSH 用于构建、传输、安装、日志和二进制身份核验，未通过 SSH 截屏或向交互桌面注入输入。

安装在 09:50:27 完成。`Install.ps1 -AllowPendingRestart` 与安装后 `Verify.ps1` 通过；当前目录 `C:\Program Files\RIMES\versions\0.2.0-preview.6-6ec52332b304`，上一版 preview.5 保留。使用原有 `%APPDATA%\RIMES`，未清理词库、设置或凭据。

升级前两次检查因旧 Broker 仍运行而拒绝继续。正常退出托盘后，屏幕键盘与 Edge 后台进程先后触发了旧版重连。关闭测试用屏幕键盘，并通过 Edge 的“关闭 Microsoft Edge”菜单正常退出浏览器，再正常退出 Broker 后完成安装。没有放宽占用检查、强行覆盖 DLL、强杀交互进程或注销桌面。

| 真机操作 | 结果 |
| --- | --- |
| 新 x64 宿主输入 `nihao`，鼠标点击首项 | “你好”一次上屏，横向候选与组字条正常 |
| 使用 Windows 屏幕键盘 Ctrl+Alt+B | Buffer 可打开；目标未就绪时显示未绑定，明确重绑后显示已绑定 |
| 绑定后收起屏幕键盘 | 焦点代次变化，Buffer 显示需重新绑定；随后普通输入进入宿主，不误当作缓冲投递 |
| 保持目标稳定并重新绑定，输入 `ceshi ` | “测试”进入 Buffer；宿主仍为“你好世界” |
| 点击新的发送按钮 | 宿主变为“你好世界测试”，Buffer 显示已发送且块被消费；没有重复上屏 |
| 墨竹主题中点击翡翠的详情按钮 | 显示对应说明；墨竹仍选中，Escape 仅关闭详情 |
| 切换并保存翡翠，关闭后重开 | 浅色标题栏与页面一致，选择保留，普通设置 `theme=day` |
| 新 x86 宿主输入 `nihao`，点击浅色首项 | “你好”一次上屏，B 框保持空白 |
| 逐一预览静谧、拉斯塔，再保存墨竹 | 四套标题栏和控件颜色正确变化，最终 `theme=night` |

x64 宿主 PID 73436 与 x86 宿主 PID 78544 加载的模块路径均属于 preview.6；x86 模块由 32 位 PowerShell 核验。共用 Session 1 x64 Broker PID 78668，路径与磁盘 SHA-256 匹配最终包。收尾关闭所有专用宿主、屏幕键盘与预览程序，仅保留新版 Broker 运行。

Windows App 直接转发 Ctrl+Alt+B 时仍只产生 `b`；该试验已取消组字，成功的组合键证据来自 Windows 屏幕键盘，不代表物理键盘链路通过。绑定测试中把焦点恢复与快捷键分开操作，等待上下文通知就绪。

## 固定状态与视觉证据

正式提交的 `RimesVisualPreview.exe` 使用相同生产绘制代码，已查看普通输入与翻译双轨。按钮细边线、紧凑间距、结果轨动作位置和底边留白符合本轮布局；固定翻译文字没有调用 API。安装版实际 Buffer 捕获、投递证据另行列出。

Mac 回收目录：`/Users/isaac/Documents/05-dev/apps/rimes/.build/windows-style-20261003/`。Young 根目录：`D:\AI\rimes-windows-style-20261003`。

- `visual-comparison-round2.html`：第一轮 / 第二轮 Buffer、Mac / Windows 四主题，以及真实投递截图；`visual-crop-provenance.json` 记录原图哈希、裁剪矩形与证据类型。未修饰原图中的 RDP 光标光晕。
- `mac-reference-hashes.json`、`mac-render-provenance.json`：当前 Swift 源码与已有离屏绘制器分别标识。
- `style-release-audit.ps1`、`style-release-result.json`、`style-release-build.log`：准确提交构建命令、退出码与哈希。
- `PACKAGE.json`、ZIP、`.sha256`、`package-local-verification.json`：最终包与回收验证。
- `install-6ec52332b304.log`、`verify-6ec52332b304.log`、`installed-preview6.json`、`installed-*-modules.json`、`installed-final-state.json`：安装及实际加载身份。
- `installed-*.jpg` 为安装版，`final-fixture-*.jpg` 为准确提交固定预览；`review-*.jpg` 属于中间审阅，不替代最终证据。

## 仍需完成

本轮只细化现有 Windows 功能外观，未移植 Mac 的 Mailbox、Capsule 与插件页。字体、系统阴影和菜单边框仍有平台差异；未重新进行真实 Windows 150% / 200% 缩放、多屏或锁屏验收。

旧宿主仍可能加载旧 DLL，安装状态为 `requiresSignOut=true`；新宿主通过不代表所有已开应用已升级。既有冷启动字母直通、Edge 切框拼音残留没有在本轮修复；本轮 x86 在 Broker 已就绪时成功输入不能关闭冷启动问题。完整真实 API、Return 异常矩阵、其他应用及一工作日试用仍按 [日用门槛](DAILY-PREVIEW.md) 推进。
