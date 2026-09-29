# Linux Capsule behavior spec

This is the acceptance baseline for the Linux Capsule rail. It is derived
from macOS `CAPSULE.md`, `CapsuleRailLibrary`, `ClipboardHistoryWindowController`,
`CapsuleContentStore`, and `Delivery.insert`. Linux implements the **note
library + bottom rail + exact-target insert** path. Clipboard-history
capture, iCloud, media kinds, the password vault, Capture, and Mailbox are
documented here as macOS behavior and called out as not ported.

Capsule is a sibling of Buffer, not a Buffer plugin. It does not own a
second librime runtime. The IME still commits through
`RimesIme::commitText` for ordinary typing; Capsule insert calls
`InputContext::commitString` on the **same** armed input context after
re-checking that it is still focused — the analogue of macOS
`Delivery.insert` after a live `FocusToken` check.

## What it is

On macOS, Capsule is the local content library next to Buffer and Mailbox.
The user-facing surface is a **208 pt bottom rail** (`⌘⇧V`, Linux paints
**940 × 213** on X11) plus a manager window. Tabs:

| Tab (label) | Kind | Activate | Copy |
|---|---|---|---|
| 临时 | clipboard history | text insert / file paste | yes |
| 捕获 | screen captures | file paste | yes |
| 笔记 | note | insert body | yes |
| 图库 | image | file paste | yes |
| 影集 | video | file paste | yes |
| PDF | pdf | file paste | yes |
| 技能 | skill | file paste | yes |
| 密码 | password | reveal in place only | only after 15 s lease |

Linux v1 keeps that tab strip so the rail is recognizable, but only **笔记**
has a working store and delivery path. The other tabs render empty with an
explicit hint. **临时 (clipboard history) is the tab macOS users reach for
first and is not ported.** See
[Deliberate macOS mismatches](#deliberate-macos-mismatches).

Default seed (once per library, marker `content-seed-v1` survives deletion):

- id `72696d65-7300-4000-8000-000000000001`
- title `RIMES 默认词条`
- body `RIMES`

## How it is triggered

| Source | macOS | Linux |
|---|---|---|
| Global hotkey | `⌘⇧V` (Carbon exclusive; works under any IM after login bootstrap) | `Ctrl+Shift+V` or `Super+Shift+V` **only while RIMES is the active IM**. Another IM (including stock `fcitx5-rime`) does not see the hotkey. |
| Status menu | “Capsule…（⇧⌘V）” | not ported (no Linux status menu yet) |
| Settings page | rebind, iCloud, passcode | not ported |
| `rimes-capsule-ctl` | n/a | `show` / `close` / `toggle` / `activate` / `copy` |
| Manager window | gear / `＋` / card 编辑 | not ported; CRUD is the store + ctl |

Show is refused on a password / secure field. Hide uses the same hotkey
**while this IC is armed**, Escape **on the armed IC**, the rail close
button, a successful insert, or an IM switch. Hide also clears the
in-rail search query, matching macOS.

A field switch **disarms** and keeps the rail visible. The next
`Ctrl+Shift+V` / `Super+Shift+V` on the new field **re-arms**; a second
press closes. The hint says so.

## What the user sees

macOS rail (`ClipboardHistoryWindowMetrics`):

- 940 × 208 pt, cards 206 × 126, spacing 8, corner radius 14
- Header (search, count, gear, `＋`) + card row + one hint line
- Selected card: `⋯` menu; hover copy on non-password cards

Linux rail (GTK 3):

- Size request **940 × 208**. X11 allocates about **940 × 213** (chrome).
  labwc/sway stretch the overlay to the output minus 80 px side margins
  (1120 px wide on a 1280-wide output). Classic-like dark chrome, one
  hint line.
- Header: “Capsule”, tab labels, item count, close.
- Horizontal cards: title + 280-character preview. Selected card is
  highlighted. Click selects; double-click (or a second press on the
  same card within 400 ms) activates a note and **closes the rail**.
  Card widgets are reused by id so a select snapshot does not destroy
  the widget under the second click.
- The window **never takes keyboard focus** (`accept_focus=false`,
  Wayland `GTK_LAYER_SHELL_KEYBOARD_MODE_NONE`). Search, tab, arrows,
  Return and Escape are consumed by the IME while the rail is **armed**.
- Why GTK 3: same toolkit as `rimes-buffer`, `libgtk-3-0` /
  `libgtk-layer-shell0` are Debian 13 and Ubuntu 24.04 packages, and
  layer-shell is the only stock way to stay overlay / non-activating on
  wlroots. A second UI stack would duplicate process, packaging, and e2e.

## States

| State | Meaning | Linux field |
|---|---|---|
| Visibility | Is the rail shown? | `CapsuleModel.visible` |
| Armed session | May this IC's keys / Return deliver? | `armed` + `token` |
| Tab / query / selection | What the rail is showing | `tab`, `query`, `selected` |
| Password / secure | Fail closed | `password_field` |

Showing the rail does not steal host focus. Closing drops the session and
**preserves** the library (disk). Process restart reloads the store; the
rail starts hidden. Switching to another field while the rail is visible
**disarms** the session and keeps the rail up — later keys go to the new
field, and Activate / Return must not write there until the user
re-arms on that field (one hotkey). A successful insert **closes** the
rail.

Buffer capturing wins the key route: Capsule toggle still works, but
Return / Escape / typing stay with Buffer until capture pauses.

## Hotkeys (armed rail, RIMES current)

| Input | Effect |
|---|---|
| `Ctrl+Shift+V` / `Super+Shift+V` | Show, or re-arm a visible disarmed rail, or close when armed on this field (also works while Buffer is capturing). **RIMES must be the active IM.** |
| Escape | Close (this IC only) |
| Tab / Shift+Tab | Next / previous tab |
| Left / Right | Move selection |
| Return | Activate the selected **note** (insert body) and close |
| `Ctrl+1`–`Ctrl+9` | Activate visible card 0–8 |
| `Ctrl+C` | Copy the selected note body (clipboard only; no paste) |
| Printable ASCII / Backspace | Edit the in-rail search query |
| Host typing while disarmed | Reaches the host / Rime; not search |

Password chords `RH / WO / CVN / QU`, `⌘S` favorite, and the `⋯` migrate
menu are macOS-only in this step.

## Interaction with Buffer, IME, Mailbox

- **IME.** Capsule is not on the ordinary composition path. While armed,
  the IME consumes the keys above **before** `RimesState` so they cannot
  leak into the host or a password field. There is **no client-preedit
  ZWSP**. Insert uses `ic->commitString` after `LiveTarget()` still
  matches the armed token.
- **Buffer.** Sibling. Opening Capsule does not stage Buffer blocks.
  Buffer `HandleEarlyKey` runs first; a capturing workbench keeps
  Return / Escape / Backspace. Capsule insert bypasses
  `RimesIme::commitText` so a capturing Buffer cannot swallow a note as a
  chip. Delivery still fail-closes if the field changed.
- **Mailbox.** Not ported. No coupling on macOS either beyond shared
  typography.

## Store

Linux root (Obsidian-readable Markdown, same front matter as macOS):

```text
${XDG_DATA_HOME:-$HOME/.local/share}/rimes/capsule/
├── content-seed-v1
├── entries/<uuid>.md
└── .lock
```

Overrides: `RIMES_CAPSULE_ROOT`, else `$RIMES_USER_DIR/capsule`.
Directories `0700`, files `0600`. Cross-process `flock` on `.lock`.
Revision is SHA-256 of the exact Markdown bytes. Concurrent writers with a
stale revision are rejected. Symlinks are refused. Limits match macOS:
title 256, body 256 KiB, 20 000 records, document 1 MiB.

```text
---
capsule: note
version: 1
id: "72696d65-7300-4000-8000-000000000001"
title: "RIMES 默认词条"
updated_at: "2026-09-29T00:00:00.000Z"
---

RIMES
```

## Placement, focus, always-on-top

### X11 (Xfce and the CI Xvfb path)

- Borderless, skip-taskbar, `keep_above`, `accept_focus=false`.
- Window type `UTILITY` (same reason as Buffer: xfwm + move-drag).
- Bottom-centered on the monitor workarea with a 16 px margin. The macOS
  rail is a bottom bar, not caret-anchored, so Linux does not follow the
  caret (and must not sit on the same side as the candidate popup by
  accident — bottom dock stays clear of a downward candidate list).

### wlroots Wayland (labwc / sway)

- `gtk-layer-shell` overlay, bottom + left/right anchors, 48 px bottom
  and 80 px side margins, **940 × 208** minimum size request (allocated
  width follows the output; height is about 213). Keyboard
  interactivity none.
- The compositor owns the exact y. That is expected.

### Other Wayland (GNOME, KDE)

- Without layer-shell the window is a normal xdg-toplevel. Position and
  always-on-top are compositor-defined and **not** claimed.

## Safety invariants (carried from Buffer)

1. Never leak keystrokes into another app or a password field. Armed keys
   are consumed only for the armed IC. Escape in any other field reaches
   that app.
2. Never install U+200B / ZWSP in client preedit.
3. Never steal focus. The GTK process is a renderer.
4. If the focused field may have changed (other IC, same-IC reactivate,
   focus-out grace, password, IC destroy, IM switch), **disarm**. Activate
   and Return fail closed. Remaining cards stay on disk.
5. Password / `CapabilityFlag::Password`: refuse to arm, hide or keep
   hidden, never insert. The addon watches
   `InputContextCapabilityChanged` so a field that becomes a password
   box is flagged even after Fcitx has already switched the IM away
   (later keys never reach `HandleEarlyKey`). Firefox-esr still
   disables the IM on `<input type=password>` — same limit as Buffer.
6. Exactly one `rimes-capsule` UI process. `kill -9` + dropped socket
   uses the Buffer respawn algorithm (`waitpid` ECHILD / ESRCH, 50 ms
   then 2 s backoff, do not kill a newer child that is still connecting).
7. The companion process is a renderer. A UI crash must not kill typing.

## IPC

Length-prefixed JSON (4-byte big-endian + UTF-8) on
`$XDG_RUNTIME_DIR/rimes-capsule.sock` (override: `RIMES_CAPSULE_SOCKET`).
Framing is the Buffer wire (`rimes::buffer::EncodeFrame`); the command
set is Capsule's. The IME owns the model. The UI and `rimes-capsule-ctl`
are clients.

Commands: `hello`, `status`, `toggle`, `show`, `close`, `next`, `prev`,
`select`, `tab`, `activate`, `copy`, `search`.

`status` reloads the note store before publishing, so `rimes-capsule-ctl
status` is not a stale closed-rail count. Connecting publishes the
current snapshot immediately.

Environment for tests: `RIMES_CAPSULE_HEADLESS=1`,
`RIMES_CAPSULE_UI=/path`, `RIMES_CAPSULE_CONNECT_DELAY_MS`,
`RIMES_CAPSULE_DUMP=/path.json`, `RIMES_CAPSULE_ROOT`,
`RIMES_CAPSULE_FOCUS_GRACE_MS` (default 5000).

## Why a separate process (not `rimes-buffer`)

The Buffer socket, GTK 3 window flags, layer-shell, `buffer_process`
respawn helpers, and `rimes-buffer-ctl` pattern are reused. Capsule does
**not** sit inside `rimes-buffer`:

- Independent visibility and a 940 × 208 rail versus the 760 × 78
  workbench.
- Buffer's “exactly one UI process” kill-9 contract must not take the
  rail down, and the reverse.
- Different ops and snapshots. Extending `rimes-buffer.sock` would break
  existing Buffer e2e and ctl.
- Capsule insert must not travel through Buffer's `OnCommit` interceptor.

## Deliberate macOS mismatches

| macOS behavior | Linux | Reason |
|---|---|---|
| Single IMK process owns UI | Companion `rimes-capsule` + Unix socket | Same Fcitx5 constraint as Buffer |
| `⌘⇧V` under any IM (LaunchAgent) | **Only while RIMES is the active IM** | No login bootstrap in this step. This is the first thing a macOS user will miss. |
| Rail can become a key window | Never takes focus; IME routes keys | Buffer safety invariant |
| `Delivery.insert` + auto-paste | `commitString` for notes only; rail closes after insert | No Accessibility / synthetic Ctrl+V |
| 临时 clipboard history | **Empty tab — not ported** | No NSPasteboard watcher; Wayland clipboard is compositor-owned. This is the first tab macOS users expect. |
| 捕获 / Image / PDF / Video / Skill | Empty tabs | No ImageIO, PDFKit, Capture, file-URL paste |
| Password vault + physical chords | Empty tab | CryptoKit / Carbon keycodes / 15 s canvas; later port |
| iCloud Drive mirror | Not ported | No ubiquitous folder / security-scoped bookmarks |
| Manager 940 × 660 + compact 460 × 400 | ctl + Markdown files | Rail is the interactive surface |
| `org.nspasteboard.ConcealedType` | GTK clipboard text only | No equivalent on X11/Wayland |
| Themes / rounded AppKit chrome | One Classic-like GTK theme | Visual port, not a theme engine |

## Out of scope

Mailbox, IBus, a custom candidate window, Buffer plugins, and any change
to the macOS Swift sources.
