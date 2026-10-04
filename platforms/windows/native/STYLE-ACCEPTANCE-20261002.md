# Windows macOS 样式还原验收 · 2026-10-02

> 本文保留 preview.5 的历史证据。最新 preview.6 及第二轮改进见 [10 月 3 日验收](STYLE-ACCEPTANCE-20261003.md)。

**preview.5 已安装到 Young；候选窗、Buffer、设置、托盘和菜单已按 macOS 配色与逻辑尺寸重做。双架构构建和自动化通过，安装版的候选点击、Buffer 捕获与投递、主题保存通过。Windows 日用验收仍未完成。**

## 源码、参考与产物身份

- Windows 分支：`codex/windows-daily-buffer`，本轮基线 `995751f`，实现提交 `efec80f7e06d2ec613e80398bc463be35ec66a16`。后续文档提交不会改变下列二进制身份。
- macOS 参考基线：`b02fe9ccaae34bef927e8f7e8f78c2441d1e91c3`，包含当前工作树的设置改动；仅只读参考，未修改其他平台工作。准确文件 SHA-256 见 [样式规格](MACOS-VISUAL-PARITY.md)。
- macOS 图片来自已有生产 `panel-render` / `settings-render` 绘制器，使用隔离的偏好与数据目录。绘制器 SHA-256：`cf7de14e5bdb2093abed1f04d71cf25b5b7225c6647197e52c7023bc6a476ae7`。这是已有二进制的离屏参考，不是当前工作树重新构建或安装后截屏的证明。
- 最终源码 ZIP：66,283,643 字节，逐文件校验 1,330 个源码文件；SHA-256：`4e672aa73fb2be26e99fcf8f693bfefd96066ff83305150e28d829013096453c`。
- 安装包：`RIMES-Windows-0.2.0-preview.5.zip`，23,069,289 字节，96 个清单文件；SHA-256：`27af6d0b5496c552150979c35f3ad7e7f63249e8cd0165ebb01c2eaf776ac80d`。拉回 Mac 后再次核对全部文件字节数和 SHA-256，通过。

| 二进制 | SHA-256 |
| --- | --- |
| x64 TSF DLL | `11751e7095c3cb36dbf70d5e6d9398dc484102e70afbbea30e1cdbc7f9065334` |
| x86 TSF DLL | `ac0c5976468a00290640dba4aaa7992794cb1451c975300e5a03e1180c6b5325` |
| 实际共用的 x64 Broker | `052074a1ed1dde5fda0bfc3a3dbe4ebc51e99833a8e47a1d51bd1ab45b44862b` |
| x64 样式预览程序 | `2483d426470fadcdb5917a1aab22d434acd6133d7ea6733fc45d09406158527b` |

## 样式实现

- 四套主题：墨竹、翡翠、静谧、拉斯塔；37 个语义颜色 × 4，148 个 RGB 值与参考主题数据一致。主题写入普通设置，旧文件默认墨竹；密钥继续独立存放，不为外观读取密钥。
- 候选：最大默认宽 460 DIP，横向条目按实际文字宽度换行；普通候选行高 34 DIP、选中块高 24 DIP；组字条独立显示。字号 10–40，保留整页候选及正确点击索引，旧鼠标按压在内容、页码或目标变化后失效。
- Buffer：默认宽 760 DIP，宽度范围 520–1100；普通输入高 73 DIP，生成、翻译高 105 DIP；工具栏高 33 DIP，原文与结果上下两轨。使用矢量图标、紧凑文字、独立横向滚动、明确等待和错误状态；发送图标保持静态。保留不激活、目标重验及实际插入确认后消费的行为。
- 设置：客户区 980 × 680 DIP，最小 860 × 600，侧栏 160 DIP；真实功能分页与分段标签、三列方案卡、四套主题卡、字号、快捷键、连接器与关于。主题卡高 116 DIP，预览高 68 DIP，色块高 30 DIP。切页保留草稿，关闭不保存，保存验证所有字段；关闭后重新打开会重建原生控件。
- 托盘、菜单：主题配色、文字与间距统一，使用绘制的产品及功能图标；所有原有命令继续可用。Windows 原生弹出菜单仍有系统绘制的浅色外框。

## 最终自动化

Young：Windows 11 Pro x64 Build 22631，VS 2022 / MSVC、Windows SDK、CMake；最终从准确提交的全新源码目录构建。运行时间 22:46:44–22:49:56，以下命令退出码均为 0。

| 检查 | 最终结果 |
| --- | --- |
| 完整产品数据清单与 SHA-256 | 通过；可独立部署完整词库 |
| Python 产品数据边界测试 | 9 项通过 |
| 安装 PowerShell 语法解析 | 通过 |
| x64 / x86 Release，MSVC `/W4 /WX` | 两架构通过 |
| CTest | 每架构 15 组全部通过，包含主题持久化、布局、候选鼠标所有权及既有协议、Runtime、连接契约测试 |
| 产品探针 | 每架构五方案及两项繁体输出通过，共 7 个样例 |
| 模拟 TSF + 实际 Broker | 两架构通过，包含输入及延迟目标撤销；隔离 Session 0 与用户目录 |
| ZIP 清单与 Mac 回收核验 | 96 个文件通过 |

本轮没有重跑未修改的完整 API 回环传输套件或全部 19 项安装生命周期；这些证据仍属于 [此前验收](ACCEPTANCE-20261001.md)。样式错误与等待画面使用固定状态，不能代替真实 API 请求。

## 安装版真机操作

GUI 通过 Windows App / CUA 观察、鼠标点击与输入。SSH 仅用于源码、构建、文件、日志和进程模块核验；未通过 SSH 注入按键、读控件或截屏。

安装目录：`C:\Program Files\RIMES\versions\0.2.0-preview.5-efec80f7e06d`。`Install.ps1 -AllowPendingRestart`、安装后的 `Verify.ps1` 均通过；上一版 preview.4 保留。安装没有覆盖被占用的旧 DLL，继续使用独立的 `%APPDATA%\RIMES`，没有清理用户词库或凭据。升级前进程占用检查曾正确阻止安装，关闭本次测试窗口并正常退出托盘 Broker 后完成升级，没有强制结束交互进程或绕过检查。

新测试宿主加载 preview.5 DLL；共用 Session 1 的 x64 Broker PID 75144。x64 宿主 PID 78572 的模块路径已核对；x86 宿主 PID 70524 使用 32 位 PowerShell 确认模块位于新版本 `x86\RimesTsf.dll`。磁盘二进制哈希与最终包一致。测试后正常关闭专用宿主，Broker 保留运行。

| 实际操作 | 观察结果 |
| --- | --- |
| x64 输入 `nihao`，点击首项 | 紧凑横向候选与独立组字条正确显示；“你好”上屏一次 |
| x86 就绪后在空白 B 框输入 `nihao`，点击首项 | B 得到“你好”，A 不被改变；候选正确绑定目标 |
| Ctrl+Alt+B 打开安装版 Buffer | 通过 Windows 屏幕键盘实际点击快捷键；普通面板保持紧凑，宿主保留输入焦点 |
| 输入 `shijie` + 空格进入 Buffer | 原文轨得到“世界”，发送前宿主仍为“你好” |
| 点击发送图标 | 宿主变为“你好世界”，完成确认后消费原文块；未重复上屏 |
| 改变输入目标 | 面板显示“输入框已切换，请重新绑定”，撤销旧目标 |
| 设置逐一切换四套主题 | 安装版侧栏、主题卡、选中标记、文字与控件配色正确变化 |
| 保存翡翠，重新打开设置 | 主题保留，设置文件的 `theme` 为 `day`；实际 x86 候选使用浅色主题 |
| 浅色候选输入 `shijie`，点击“世界” | B 从“你好”变为“你好世界”，一次提交 |
| 收尾恢复墨竹并保存 | `theme` 核验为 `night`，Broker 路径和二进制哈希仍匹配 preview.5 |

Windows App 组合键转发仍不完全可靠；屏幕键盘测试不等于物理键盘与全部远程输入链路通过。

## 固定状态预览与边界

`RimesVisualPreview.exe` 使用同一套生产候选、Buffer 和设置绘制代码。预览不注册输入法或快捷键，不读取凭据、写真实设置、访问剪贴板、发网络请求或绑定真实输入框。

已观察输入、生成、等待、翻译、错误状态；40 DIP 候选九项换成三行，未丢失条目；设置草稿切页保留、关闭不保存、重新打开恢复控件。布局自动化覆盖窄区域、长文字、点击映射和 DIP 比例。

预览中的 144 / 192 DPI 模拟只缩放 Buffer 绘制区域；候选继续使用真实系统 DPI，200% 模拟下会与固定候选位置重叠。这是预览夹具限制，不是实际多屏或 Windows 150% / 200% 缩放通过的证据。

原生字体与光栅化、标题栏、窗口阴影、菜单系统边框仍有平台差异。本轮对齐颜色、布局和交互状态，不宣称跨操作系统像素完全一致。Mac 专有的 Mailbox、Capsule 与插件功能页未在本轮移植。

## Windows 本地 Cursor 开发与审查

按用户授权将源码传到 Young，使用已有 Cursor CLI 在 Windows 本地开发并双架构测试，再回收代码。两轮原始报告与导出包保留；最终由 Codex 在隔离 Windows 分支审查、整合并重新测试准确提交。

审查修正了大字号候选裁剪与点击、两轨结果被裁剪、重复预览、设置布局、选中标记、隐藏控件草稿与重建、悬停和键盘导航等问题。第二轮回收还原了部分较旧生产文件，整合时保留已审核的本地候选与通知实现，未用旧文件覆盖它们。

合并覆盖到旧构建树后曾因混用旧对象出现 x64 Runtime 测试失败（14/15）；清理构建后两架构通过。最终版本进一步使用全新源码及构建目录，两架构各 15/15 通过。原始失败日志保留，不能将中间构建当作最终证据。

## 证据与复现

Young 根目录：`D:\AI\rimes-windows-style-20261002`；准确源码目录：`release-efec80f7e06d`。固定运行库来自 `D:\AI\rimes-windows-20260929\runtime-x64` / `runtime-x86`；完整只读词库来自 `D:\AI\rimes-windows-daily-20261001\daily-shared`。

```powershell
# 在 release-efec80f7e06d 中，使用 VS 自带的 cmake.exe / ctest.exe
./platforms/windows/scripts/Build-RimesWindows.ps1 -Architecture x64 -Configuration Release -Parallel 4
./platforms/windows/scripts/Build-RimesWindows.ps1 -Architecture x86 -Configuration Release -Parallel 4
python ./platforms/windows/scripts/prepare-native-data.py verify D:\AI\rimes-windows-daily-20261001\daily-shared
python -m unittest discover -s platforms/windows/tests -p test_native_data.py
```

完整路径、探针、隔离 Broker 和打包命令见 `style-release-audit.ps1`；准确退出码与哈希见 `style-release-result.json` / `style-release-build.log`。

Mac 回收目录：`/Users/isaac/Documents/05-dev/apps/rimes/.build/windows-style-20261002/`：

- `visual-comparison.html`：四主题可切换对照，按相同逻辑尺寸展示；裁剪保留原截图，矩形记录于 `visual-crop-provenance.json`。
- `windows-installed-*.jpg`：实际安装版 GUI；`windows-buffer-*.jpg`：标明的固定状态预览；`mac-render-provenance.json`：Mac 离屏参考来源。
- `style-release-result.json`、`style-release-build.log`、`style-release-audit.ps1`：准确提交最终构建。
- `PACKAGE.json`、ZIP 及 `.sha256`、`local-package-verification.json`：最终包与逐文件回收验证。
- `install-efec80f7e06d.log`、`verify-efec80f7e06d.log`、`installed-preview5.json`、`installed-wow32-modules.json`、`installed-final-state.json`：安装、模块与最终主题/二进制身份。
- `retrieved/`、`retrieved-2/`：Windows 本地 Cursor 原始开发报告、日志和导出内容。

## 仍未通过的日用门槛

- 本轮 x86 首次激活仍观察到 `nihao` 的前缀 `nih` 直通宿主，连接就绪后输入正常。未修改断连直通契约，冷启动问题仍需修复。
- [此前 Edge 切框残留未提交拼音](ACCEPTANCE-20261002.md) 仍是未解决问题；本轮样式验收没有重测或宣称修复。
- Explorer 等旧宿主仍映射旧 DLL，`requiresSignOut=true`。未强行注销或重启用户桌面，新测试宿主通过不能代表所有已开应用升级完成。
- 真实系统 100% / 150% / 200% 缩放、多屏、锁屏恢复、管理员窗口、VS Code、微信、Office、配置后的真实 API、完整 Return 长按与异常确认矩阵，以及一个工作日试用仍需完成。

这些限制属于 Windows 日用交付门槛，不能由样式对照、构建或模拟宿主测试替代。
