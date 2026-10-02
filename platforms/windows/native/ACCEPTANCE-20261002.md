# Windows 真机验收记录 · 2026-10-02

本次恢复了 Windows App 对 Young 远程桌面的画面读取、鼠标与单键操作，已完成真实 Windows 输入测试。此前的 [2026-10-01 验收](ACCEPTANCE-20261001.md) 保留为历史记录；构建、安装、模拟宿主与本次真实宿主结果分别记录。

**当前状态：preview.4 已安装；x64/x86、记事本的基本中文输入及鼠标选词通过。Edge 切换输入框仍残留未提交拼音，未达到日用门槛。**

## 最终产物与自动化

- 二进制源码：`c99a3aadcead3deaa89c40030d50543c8a598241`。本记录是后续文档，未改变二进制身份。
- 安装包：`RIMES-Windows-0.2.0-preview.4.zip`，23,015,831 字节，96 个文件清单项。
- ZIP SHA-256：`a785c20fbfab08c3d7f9d927af01f47d122bcba175f63a3a6d3bf026a571eeb8`。
- 源码 ZIP SHA-256：`8b605e6b0c54dec72b16e5ff19334dbc7d9524be7518434f814494f520b9cdb6`。
- 安装目录：`C:\Program Files\RIMES\versions\0.2.0-preview.4-c99a3aadcead`，可回退到 preview.3。升级没有覆盖已加载的旧 DLL；保留用户目录；仍提示注销后完成所有宿主切换。
- Young 上 x64/x86 Release、每架构 11 组 CTest、完整五方案及繁体产品词库、模拟 TSF 均通过。新增真实 Broker 的候选校验回归：字母抬键后仍接受当前候选，提交后拒绝旧候选。
- 本次未重跑未修改的 API 回环传输与完整 19 项安装生命周期；其通过结果属于 `7c33090`。本次实际执行了 preview.1 → .2 → .3 → .4 的独立目录升级和安装后校验。
- 包及日志已拉回 `.build/windows-preview-20261002/c99a3aadcead/`；本地核对 ZIP、源码及所有文件 SHA-256，实际加载的 x64/x86 DLL 哈希与包一致。

## 环境与方法

- Young：Windows 11 Pro x64 Build 22631，交互桌面 Session 1；SSH 构建和模拟 TSF 使用隔离的 Session 0。
- 通过 Windows App 观察桌面并用 CUA 键鼠操作真实窗口。SSH 仅用于源码传输、构建、安装、日志与进程模块校验；没有通过 SSH 注入按键或读取控件来代替 GUI 验收。
- 使用专用 x64/x86 `RimesTsfTestHost` 和无业务数据的记事本、Edge 测试文件。未向聊天软件发送消息。
- Windows App 的组合键转发在本轮控制通道中未可靠传递修饰键；Buffer 快捷键通过 Windows 屏幕键盘实际点击 Ctrl、Alt、B 测试。不能据此认定物理键盘组合键或远程输入链路已全面通过。
- 没有注销现有桌面；Explorer 仍持有旧 DLL。新启动的测试宿主加载新安装 DLL，二者分别记录，不能把注册成功视作所有已开应用已完成升级。

## 真机发现与修复

| 现象 | 修复及验证 |
| --- | --- |
| 预览 1 候选窗固定出现在屏幕左上角 | TSF 插入点可以是零宽、正高矩形；布局不再把它当作无效矩形。预览 2 在实际插入点旁显示候选。 |
| A 框输入 `ni` 后切换 B，A 留下未上屏拼音 | 宿主终止组字时，使用回调授予的写入 cookie 清除原组字范围后撤销上下文。预览 2 实测 A 保留已提交的“泥”，不残留 `ni`；B 可独立输入“好”。 |
| 点击候选未上屏，数字键选词正常 | 修正默认数字键映射、使用可延迟的 TSF 写入会话后，preview.3 仍复现。最终查到 Broker 在不改变状态的 KeyUp 后清空了 composing，使候选校验误判过期；修复后 preview.4 的 x64/x86 及记事本点击选词均成功。 |

对应源码提交：`b5a66ee`（定位、终止组字、候选默认标签），`4abf14e`（模拟宿主样例改用测试词库实际包含的词），`4ea3e1c`（候选点击编辑会话）。第一次自动化失败是极小测试词库不包含 `hao`，保留失败报告，没有将其记作通过。

`348822f` 保留未处理 KeyUp 前的组字状态并新增回归；该回归首次运行暴露 Session 0 Broker 未开放候选校验指令，不能覆盖此路径。`c99a3aa` 将仅依赖引擎会话的候选校验与界面工作台解耦，双架构完整通过。其他工作台控制仍要求交互 Broker。保留两次中间失败日志，没有用中间构建替代最终验证。

## 已观察到的真实输入结果

预览 1（`7c33090`）：

- x64 宿主真实加载安装目录中的 x64 DLL；`nihao` 候选和空格上屏得到“你好”，`shi` 数字键 3 得到“使”。
- x86 宿主窗口中 `nihao` 加空格得到“你好”。进程模块的跨架构校验另行记录。
- x64 密码框输入虚拟测试文本仅显示掩码，无候选窗口；这只证明该次普通输入的表面行为，不覆盖全部敏感上下文或 Buffer 路径。
- 初次激活后曾有 `ni` 直接进入宿主，等待连接后恢复正常。冷启动握手窗口仍需专门复现，不能宣称完全没有原始字母泄漏。

预览 2（`4abf14e`）：实际 x64 宿主 PID 66268 和 x64 Broker PID 53876 均运行预览 2 安装路径；候选定位、组字下划线、数字键选词和 A/B 切换清理通过。鼠标点击仍失败，因此继续修复并生成预览 3。

### 预览 4 实际宿主

| 宿主 | 操作与结果 | 实际加载身份 |
| --- | --- | --- |
| x64 测试宿主 | `ni` 后点击第二项“泥”，只上屏“泥”；再输入 `ni` 后点 B，B 输入 `hao` 加空格，A 仍为“泥”，B 为“好”，旧拼音已清理 | PID 74604，preview.4 x64 DLL |
| x86 测试宿主 | `nihao` 后点击“你好”，准确上屏且候选关闭 | PID 72920，32 位 PowerShell 读取模块确认 preview.4 x86 DLL |
| 共用 Broker | 两架构测试期间使用同一 x64 Broker | PID 69572，Session 1，preview.4 x64 Broker 与 librime |
| Windows 记事本 | 独立空测试页输入 `nihao`，鼠标点击“你好”，再输入中文句号，内容为“你好。”，状态栏为 3 字符、1 行 | PID 74496，Notepad 11.2607.14.0，preview.4 x64 DLL |
| Edge 普通输入 | 本地无网络测试页的 input A 输入 `nihao` 加空格为“你好”；textarea B 可独立输入“好” | PID 66964，Edge 154.0.4258.48，preview.4 x64 DLL |
| Edge 切换输入框 | **未通过**：A 已有“你好”，继续输入未提交的 `ni` 后点击 B，A 留下“你好ni”。B 输入没有串到 A，但旧组字未清除 | 已排除测试到旧安装 DLL 的可能；原生宿主修复不等于 Chromium 控件通过 |

x64 DLL SHA-256：`1ea49d21b4f650576f5f04459d6f2b6fe367749c47ab357bcd7c3f70d0ec12ed`；x86：`984fa2dc65da105137b1b8b3da2fdec9f03148927b2848e2ba1fa7fffb7aad54`。记事本原有其他标签未改动。Edge contenteditable 与密码区尚未完成；随后远程桌面控制检测到用户正在操作，停止继续点击。

### Buffer 真实测试（预览 1）

1. 屏幕键盘 Ctrl+Alt+B 打开面板；输入 `nihao`、空格、句号生成 Buffer 原文块“你好。”。宿主内容在发送前未改变。
2. Return 单次发送显示 `Delivered`，块被消费；关闭面板后，宿主 A 从“你好”变为“你好你好。”，未观察到多余换行。
3. 再次积累“你好。”，切换宿主 B 后点击发送，显示 `Target changed. Rebind to send.`，块保留。
4. 点击 Bind 明确重绑，再按 Return，关闭面板后 B 为“你好。”，A 内容不变。

这些结果不覆盖 1.2 秒长按、丢失确认、所有真实应用或真实 API。一次未先观察面板状态的快速远程组合操作没有打开面板，文本进入宿主；该次不计通过，也未在证据不足时归因为产品缺陷。

## Windows 本地 Cursor CLI 开发链路

按用户提出的方式，已通过 SSH 准备独立 Windows 副本 `D:\AI\rimes-windows-daily-20261002\cursor-cold-start-c99a3aadcead`，含准确源码身份、Git 基线、任务边界和冷启动问题指令；目标是本地开发/测试，再拉回 diff、日志审查。本地 Git 初始提交仅是快照，不冒充原分支提交。

实际存在 `cursor-agent.cmd`，版本 `2026.04.17-787b533`。`status` 返回已登录但无法获取用户信息；真正的 `--print --trust --output-format stream-json --workspace ...` 调用以退出码 1 返回 `Authentication required`。因此 **尚没有 Cursor 实施或测试的代码，也没有 Cursor 修改回收或合并**。已请求用户在 Young 执行 `cursor-agent login`，未读取或复制其他应用凭据。

## 证据位置

Young 根目录：`D:\AI\rimes-windows-daily-20261002`。

- `release-c99a3aadcead-result.json`、`native-host-audit-c99a3aa.log` 和 `native-host-audit.ps1`：准确源码、命令、时间、退出码、二进制哈希及完整测试记录。
- `install-c99a3aadcead.log`、`verify-c99a3aadcead.log`：独立版本升级与安装后校验。
- `real-x64-preview4.json`、`real-x86-preview4.json`、`real-notepad-preview4.json`、`real-edge-preview4.json`：真实进程、会话、模块路径与 SHA-256。
- `cursor-cold-start-result.json`、`cursor-cold-start-stderr.log`：CLI 实际鉴权失败；`cursor-cold-start-prompt.txt` 保存待运行任务，不包含凭据。

## 尚未完成的门槛

- 五方案及简繁/标点设置的全套真实应用验证；产品词库探针通过不能替代这些操作。
- Edge 切换输入框残留组字修复；contenteditable、VS Code、微信草稿、可用 Office；100%/150%/200% 缩放、多屏、锁屏恢复、管理员窗口、完整输入法切换矩阵、Broker 崩溃重连。
- Buffer 长按/迟到抬键、全部发送、真实宿主拒绝插入与确认丢失；真实 API 供应商的生成、翻译、取消与配置变更。
- 首次连接时原始字母进入宿主的复现与处理；旧桌面进程退出后的安装状态复核。
- 至少一个工作日试用。当前不能认定整个 Windows 日用计划完成，也未签名或公开发布。
