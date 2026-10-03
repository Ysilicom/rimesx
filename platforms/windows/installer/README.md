# RIMES Windows 内部预览

Windows 11 x64；包括 x64/x86 TSF 和一个 x64 Broker。需要已安装 Microsoft Visual C++ 2015–2022 Redistributable（x64、x86）。未签名内部预览，真实宿主与一天试用的验收状态见开发仓库的 DAILY-PREVIEW.md，安装验证不等于输入验收。

1. 保留 ZIP、SHA-256 和解压后的 PACKAGE.json；用 `Get-FileHash -Algorithm SHA256` 比对 ZIP。
2. 以当前用户打开管理员 **64 位 PowerShell**，在解压目录运行 `powershell -NoProfile -ExecutionPolicy Bypass -File .\Install.ps1`。安装器逐文件验证，并注册两个架构。默认启用登录启动，可传 `-NoAutostart`。
3. 用 Win+Space 选择 RIMES。托盘右键打开设置，选择输入方案、简繁/标点、候选字号、启动行为、API 和快捷键。
4. 在宿主输入框按 Ctrl+Alt+B 打开并绑定 Buffer。面板不激活，键盘继续留在宿主。点击“粘贴”只将剪贴板内容放入原文；点击“下一块”或轻按 Return 投递下一块，长按 Return 1.2 秒或点击“全部”投递当时队列。中文组字中的 Return 先处理组字。
5. 从托盘打开后，先点击宿主输入框，再点击 Buffer 原文区开始输入（当前输入法需为 RIMES）。切换输入框、应用或输入法后，Buffer 暂停输入和投递；重新点击原文区，或在宿主按快捷键明确绑定。重复点击原文区不会关闭 Buffer。无可靠目标可复制。关闭面板保留本次进程内容；退出 Broker、注销或重启不会恢复正文。确认丢失时不重发，先检查宿主，再复制保留内容。
6. API 地址填到兼容服务的 API 根路径（例如 `https://example.com/v1`），程序追加 `/chat/completions`。模型可配置；修改 API 地址后需要重新填写密钥，旧密钥不会转发到新地址。密钥在凭据管理器 `RIMES.Windows.OpenAI`，设置文件不含密钥。使用“生成”或“翻译”会发送原文到你配置的服务。取消、编辑原文、切换配置会使旧请求失效。服务须提供 SSE 和 `[DONE]`；普通输入不等待网络。

## 升级、回退、卸载

先复制/发送 Buffer 内容，从 RIMES 托盘退出，并切换到其他输入法、关闭使用 RIMES 的应用；被宿主占用的 DLL 默认会让安装器拒绝升级，必要时注销后重试。开发验收可显式传 `-AllowPendingRestart`，只切换注册到独立新目录，状态会标记 `RequiresSignOut`；运行中的宿主仍可能使用旧 DLL，必须注销后才能作为完成升级。不会终止宿主，也不会强行覆盖 DLL。

- 升级：在新包运行 `Install.ps1`。使用独立版本目录；注册失败尝试恢复旧版本。
- 验证：运行 `Verify.ps1`。核对两架构注册、文件清单、版本和提交。
- 回退：运行 `Rollback.ps1`，恢复 state.json 中记录的前一版。回退同样检查占用。迁移自旧的未打包安装时，恢复 `legacy-recovery.json` 记录的原始 DLL 路径与启动设置。
- 卸载：运行 `Uninstall.ps1`。注销两架构，删除自启动；保留版本文件和 `uninstalled-state.json` 供恢复。
- 用户词库：`%APPDATA%\RIMES`；设置：`%LOCALAPPDATA%\RIMES\settings.json`。升级、回退、默认卸载均保留词库、设置和凭据。

初次部署完整词库可能较慢。诊断只包含版本、路径、错误类型和阶段，不包含 Buffer 正文、API 密钥或响应正文。不要把用户词库、设置和凭据放入问题报告。
