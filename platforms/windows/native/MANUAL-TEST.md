# Windows IME manual test checklist

Use this on a real Windows 10 or 11 machine after the owner has hardware
again. CI covers in-process TSF + Broker + pinned `rime.dll` only. It does
**not** prove host compatibility.

Prerequisites: build x64 and x86 Release, fetch the pinned librime, copy
`rime.dll` next to `RimesBroker.exe` (or into `%LOCALAPPDATA%\RIMES\runtime`),
copy shared schemas (at least `rime-data` or the e2e table), then:

```powershell
.\RimesBroker.exe --install-autostart
# elevated, once per architecture
..\scripts\Register-RimesWindows.ps1 -Architecture x64
..\scripts\Register-RimesWindows.ps1 -Architecture x86
```

Add RIMES from Windows Settings → Time & language → Language & region →
Chinese (Simplified) → Options → Keyboards. Do not make it the system default
until the pass below is done.

Mark each row pass / fail / n/a. Attach host, DPI, and schema.

## Hosts

- [ ] `RimesTsfTestHost.exe` (plain Win32 EDIT): `nihao` then Space → `你好`;
      inline dotted preedit; candidate window under the caret
- [ ] Notepad
- [ ] WordPad
- [ ] Microsoft Edge address bar and a contenteditable page
- [ ] Chrome or Firefox text field
- [ ] An Electron app (VS Code / Cursor / Slack)
- [ ] Microsoft Word
- [ ] Excel formula bar
- [ ] Outlook compose
- [ ] Windows Terminal / PowerShell (expect limited or no composition)
- [ ] UWP / WinUI text box if available
- [ ] Elevated Notepad vs unelevated Broker (UIPI): keys must fail open

## Composition and candidates

- [ ] `nihao` shows preedit and a candidate page; Space commits `你好`
- [ ] Number keys 1–5 select the matching candidate
- [ ] PageDown / PageUp (and `,` / `.` if the schema binds them) change pages
- [ ] Escape cancels and inserts nothing
- [ ] Backspace edits the preedit; the candidate list updates
- [ ] Enter commits the highlight (or the schema's Enter binding)
- [ ] Switching away mid-composition ends or hides the panel without a stray commit
- [ ] Password / PIN fields (`TF_TMAE_SECUREMODE`): no Broker traffic, no
      candidate window, keys pass through

## Placement, DPI, monitors

- [ ] 100% DPI, 150%, 200% — candidate text stays readable, not clipped
- [ ] Caret near the bottom of the screen: window flips above
- [ ] Caret near the right edge: window stays on the same monitor
- [ ] Drag the host to a second monitor and type again
- [ ] Mixed-DPI laptop + external display
- [ ] Per-monitor DPI change while composing (move window, then type)

## Broker robustness

- [ ] First activation with no Broker running starts `RimesBroker.exe`
- [ ] Logoff / logon starts the Broker via the HKCU Run value `RimesBroker`
- [ ] Kill `RimesBroker.exe` while focused in a host; the next key fail-opens,
      then composition works again after reconnect
- [ ] Sleep and resume; lock and unlock; Fast User Switching if available
- [ ] `--print-paths` shows sensible `%LOCALAPPDATA%\RIMES` / `%APPDATA%\RIMES`
      defaults when no flags are passed
- [ ] `--remove-autostart` then reboot: Broker does not start until first IME use

## Schemes and data

- [ ] Isolated e2e table schema (CI schema) still types `nihao`
- [ ] Product `rime_ice` with full `rime-data` + lua: first-run deploy, Space
      commit, number select (expected gap if lua scripts were not copied)
- [ ] OpenCC 简↔繁 if the user enables it

## Uninstall / coexistence

- [ ] Unregister x86, x64 remains usable
- [ ] Unregister x64, no RIMES profile remains
- [ ] Weasel or other TSF IMEs still work after RIMES is removed

## Out of scope this pass

Signed MSI, SmartScreen, Buffer, Capsule, Mailbox, and making RIMES the
default keyboard.
