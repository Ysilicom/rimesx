# Windows 1.0.0 发布准备

`native/VERSION` 统一提供产品版本 **1.0.0**。CMake 工程、Broker 握手和帮助、关于界面、构建清单均从它生成；EXE/DLL 的 ProductVersion 为 `1.0.0`、FileVersion 为 `1.0.0.0`。历史 preview.8 安装及验收记录保持原样，不能改名当作正式包。

本轮目标为 Windows 11 x64；同时构建 x64 与 x86 TSF，以支持两类宿主应用。x86 TSF 不代表支持 32 位 Windows。

维护者已明确接受 **Windows 正式版不签名，以可双击安装的 EXE 交付**。1.0.0 不因缺少 Authenticode 而改为 preview；发布说明如实标注未签名，并提供 SHA-256。Windows 的安全提示或组织策略仍可能影响运行，安装器不会修改系统安全设置。

## 正式包入口

在 Windows 运行 `Build-RimesWindows.ps1`，分别传入 `-Architecture x64` 与 `x86`、`-Configuration Release`。正式产物必须来自冻结的私有源码快照；`SOURCE-COMMIT.txt` 记录基础提交，`SOURCE-MANIFEST.json` 与 `SOURCE-SNAPSHOT.txt` 记录实际文件哈希。构建清单会保留 snapshot 身份，不能把未提交的源码当成基础 commit 的原样产物。

正式 `New-RimesNativePackage.ps1` 默认版本为 1.0.0，允许未签名构建；`-SigningCertificateThumbprint` 为可选项，传入时仍要求所有 RIMES 二进制具有对应的有效签名。打包前继续核对两种架构的提交、源码快照、产品版本、PE 版本与数据文件。`-Commit`、`-SharedData`、`-RimeDll`、`-OutputDirectory` 需显式提供，另用 `-VCRedistX64` / `-VCRedistX86` 指定从微软官方下载的运行库安装器，打包器核验其微软签名。

输出包括 `RIMES-Windows-1.0.0-Setup.exe`、SHA-256 及供排查使用的 ZIP。EXE 内置安装文件和两种架构的 VC++ 运行库，使用 Windows 自带 .NET Framework / PowerShell，用户无需安装开发工具或手工运行脚本。双击后请求管理员权限，并显示安装界面；保留现有词库与设置，升级到独立版本目录。需要注销或重启时仅提示，不自动执行。当前安装需要使用同一个 Windows 用户提权；不支持输入其他管理员账号凭据后代替原用户安装。

构建验证可运行 `Setup.exe --verify-only <report.json>`：校验嵌入归档及全部文件并输出报告，不安装、不注册、不启动输入法。`tests/Test-SetupPayload.ps1` 覆盖归档损坏、越界路径及路径冲突。真实 EXE 安装和升级仍需单独验收。

## 发行前待关闭

- 冷启动 Broker 未就绪时的原始字母泄漏，以及 Edge 普通组字切换输入框的残留；已有 Buffer 重绑验收不覆盖这两项。
- 真实 AI 服务、VS Code / 微信 / Office、缩放与多屏、锁屏恢复、提权宿主及连续日用。
- 最终 EXE 的新装、升级、回退、卸载，以及升级后旧 TSF DLL 退出的验收；代码签名不再是本轮发布门槛。
- 在最终未签名包上记录真实宿主结果与包哈希。工程编译、CTest 与版本资源检查通过，仍不等于以上项目通过。

本轮不安装或替换 Young 正在使用的 preview.8，也不发布安装包。
