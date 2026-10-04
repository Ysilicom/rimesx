# Windows Buffer binding repair — October 3, 2026

The corrected preview is installed on Young Windows 11 x64. Fresh x64/x86 native hosts and Edge have passed real Buffer capture and delivery with the installed DLLs. Existing processes still require Windows sign-out to unload earlier DLLs; this is not full daily-use acceptance.

## Source and behavior

- Branch: `codex/windows-daily-buffer`.
- Initial repair: `be788f7b550fd2e1e31910cd47af58e3c1652b18`.
- Final implementation: `48c435b245f64389059508de03225fc3dd31e0fd`.
- Final package: `0.2.0-preview.8`.
- The chord scheme is **isaac2026**. The Windows frontend still exposes five ordinary schemes. Legacy `my_combo` data does not constitute the completed isaac2026 feature.

On installed preview.6, opening Buffer from the tray, selecting a host field and clicking Buffer's source rail did not capture subsequent input: `测试` went into the native host while Buffer stayed empty. The rail had no bind action; returning foreground focus and completing the Broker connection also did not publish a target before the first key. The GUI could display a saved target after capture had been revoked.

The repair makes a source-rail click an explicit, idempotent bind. The Broker accepts only a live session belonging to the actual foreground process. Foreground return and connection readiness republish a fresh TSF target without resuming capture. Paused snapshots no longer expose the old target as active.

An additional Edge test on preview.7 found that moving from a textarea to contenteditable correctly paused capture, but could not rebind without typing into the host first. Chromium can reuse one TSF context and report only a selection change. The final commit revokes capture immediately and posts a focus refresh to the host message loop. The refresh checks current TSF thread focus and obtains the current document anew; it neither restores an old lease nor resumes capture. This respects the read-only callback boundary of [OnEndEdit](https://learn.microsoft.com/en-us/windows/win32/api/msctf/nf-msctf-itftexteditsink-onendedit), with foreground eligibility checked by [IsThreadFocus](https://learn.microsoft.com/en-us/windows/win32/api/msctf/nf-msctf-itfthreadmgr-isthreadfocus).

User flow: select RIMES, select the destination field, open Buffer from the tray if needed, and click its source rail. Type into Buffer, then press Return or click Send. After changing fields, click the source rail again to bind explicitly. No reliable target means no delivery; content remains available. Settings are in the RIMES tray icon's right-click menu, under **设置**.

## Exact-source automated checks

Final audit ran on Young from **12:30:32 to 12:33:45 +08:00**. Every recorded command completed with exit code 0:

- Transferred exact commit archive and verified SHA-256 for all 1,334 source files.
- Verified complete product-data inventory and hashes; nine data-boundary tests passed.
- Parsed all installer PowerShell scripts.
- Fresh MSVC Release builds, x64 and x86, **16 CTest groups each**.
- Full product-data probes: five ordinary schemes and traditional output, both architectures.
- Fake TSF with a real isolated Broker: typing, deferred edit revocation and focus regression checks, both architectures.
- Created preview.8 and verified its per-file manifest. Retrieved ZIP SHA-256 matches Young; local verification passed all **96 payload files**.

Focus regressions cover target publication before a key, foreground loss/return, stable duplicate focus events, connection readiness, readonly/empty/secure targets, immediate selection-change revocation, deferred fresh target publication without automatic capture, and rejection of a queued refresh after thread-focus loss or a readonly change. Runtime/layout regressions cover background or removed targets, repeated source clicks, truthful paused state, and action hit-test priority. Existing delivery/acknowledgement and no-automatic-resend rules remain in force.

The audit script records commands, timestamps and exit codes. Main commands are `Build-RimesWindows.ps1 -Architecture <x64|x86> -Configuration Release`, `RimesProductProbe.exe`, `RimesTsfE2E.exe`, and `New-RimesNativePackage.ps1 -Version 0.2.0-preview.8 -Commit 48c435b245f64389059508de03225fc3dd31e0fd`. Product probes and fake-host runs use isolated test data directories, not the interactive user database.

| Artifact | SHA-256 |
| --- | --- |
| Source ZIP | `69346fbfbc6c477cd0e4edaf224189e478ac5d39839927c8a82b982fd175fe71` |
| preview.8 ZIP, 23,074,067 bytes | `b2eb1b8bf5e14d3ca6149bce6ada250942e0b26f21c9b8fe566bb0d6215972c4` |
| x64 RimesTsf.dll | `07296508c22a2a7b15690c1e35ca046375eab2cf3f3efb9cfcb2b9ff76149941` |
| x86 RimesTsf.dll | `be6e841a70d5c6b2d9d84cf3026622e0650800930b8c76caef3a8cb98682bd9f` |
| Shared x64 RimesBroker.exe | `26cf2e670cc38e19269aa87ce02b60d6ff45549d3bec6996bd8f9462539b5fef` |

## Installation and real desktop evidence

Installed at **12:39:45 +08:00** in `C:\Program Files\RIMES\versions\0.2.0-preview.8-48c435b245f6`. `Verify.ps1` passed for registration, package and runtime paths. Previous immutable versions remain available; `%APPDATA%\RIMES` was retained. Installer state explicitly reports `requiresSignOut: true`. No occupied DLL was overwritten and no sign-out was forced.

All desktop input and screenshots used Windows App/RDP through computer use. SSH was used for builds, files, installation and read-only process/module checks. The desktop Broker is PID 76472, Session 1, at the installed preview.8 x64 path. Its binary hash matches the table. The same Broker serves both architectures.

| Installed preview.8 host | Observed behavior | Loaded module evidence |
| --- | --- | --- |
| x64 native host, PID 79604 | Select empty Input A, click source rail, type `nihao `: Buffer shows `你好`, host stays empty. Return inserts `你好` once into A, consumes Buffer and shows 已发送. | Installed x64 DLL path and SHA match. |
| x86 native host, PID 59376 | Select empty Input A, click source rail, type `chenggong `: Buffer shows `成功`, host stays empty. Send inserts `成功` once and consumes Buffer. | SysWOW64 PowerShell verified installed x86 DLL path and SHA. |
| Edge, PID 79996 | Select empty textarea, click source rail, type `ceshi `: Buffer shows `测试`, textarea stays empty. Click contenteditable: capture pauses, Send remains ineffective, Buffer retains text. Click source rail and Send: contenteditable receives `测试` once; original fields remain empty and Buffer clears. No intervening host key is needed. | Installed x64 DLL path and SHA match. |

The same task also tested preview.7's native guards: repeated source clicks kept capture active; A→B paused delivery without losing text; explicit rebind delivered only to B; a password field rejected binding; closing Buffer retained its process-local text for reopening in x86. Those are preview.7 observations, separate from the final preview.8 table above.

Test hosts and the test browser window were closed normally. The new Broker remains running and RIMES remains selected; Night theme was retained.

## Evidence locations and remaining boundaries

Final Young audit: `D:\AI\rimes-windows-buffer-selection-20261003`.

Local retrieved artifacts and screenshots: `/Users/isaac/Documents/05-dev/apps/rimes/.build/windows-buffer-selection-20261003`:

- `bind-release-result.json`, `bind-release-audit.log`, `PACKAGE.json`, `local-package-verification.json`, and the preview.8 ZIP.
- `install-48c435b245f6.log`, `verify-48c435b245f6.log`, `installed-preview8.json`.
- `installed-edge-modules.json`, `installed-x64-modules.json`, `installed-x86-modules.json`.
- `edge-capture.jpg`, `edge-switch-paused.jpg`, `edge-rebind-delivery.jpg`, `x64-capture.jpg`, `x64-delivery.jpg`, `x86-capture.jpg`, `x86-delivery.jpg`.

Initial reproduction and preview.7 evidence remains in the corresponding `rimes-windows-buffer-bind-20261003` directory on Young and `.build/windows-buffer-bind-20261003` on the Mac. `before-body-click-leak.jpg` records the original failure. The preview.7 file `after-edge-contenteditable-delivery.jpg` records the **failed** rebind that prompted the final repair, despite its action-oriented filename.

This closes the reproduced Buffer binding failures in the tested hosts. It does not establish a working-day trial, ordinary Edge preedit cleanup, cold-Broker key handling, VS Code/WeChat/Office compatibility, multi-monitor/DPI behavior, lock/resume, elevated windows or configured-provider acceptance. Physical Ctrl+Alt+B forwarding through RDP was not revalidated in this task; the desktop cases used tray/source clicks and Return. The isaac2026 native chord frontend remains separate work.
