# Linux Buffer behavior spec

This is the acceptance baseline for the Linux Default Buffer. It is derived
from macOS `BufferModel`, `RimeBufferController.drainCommit`,
`BufferDeliveryCoordinator`, and `.codex/skills/rimebuffer-project/references/buffer-ui.md`.
Linux implements the **Default** workbench only. AI / translation / stream /
music plugins, Capsule, and Mailbox are out of scope.

The macOS hunch is confirmed: `RimesIme::commitText` is the single Fcitx5
commit path, the analogue of `drainCommit` before `Delivery.insert`. When
Buffer capture owns the focused input context, that text is appended as a
block and is **not** committed to the host.

## Three independent states

| State | Meaning | Linux field |
|---|---|---|
| Visibility | Is the workbench shown? | `BufferModel.visible` |
| Content | Are there unsent blocks? | `BufferModel.blocks` |
| Input route | Do keys/commits go to Buffer or the host? | `capture_enabled` + `capture_token` |

Showing the workbench does not by itself steal keys. Closing pauses capture
and **preserves** staged blocks. Switching to another field while capturing
returns the route to the host and keeps blocks. Process restart drops
content; blocks are not persisted.

## Blocks

- A Rime commit is one block (`origin=rime`). Commit boundaries are
  meaningful and are not re-diffed.
- Unhandled printable ASCII while capturing may join a growing `local` tail
  (short Latin phrases), matching macOS `appendDirectInputFragment`.
- Clipboard import is `origin=clipboard`.
- Each block has a stable id, text, origin, and `created_at_ms`.
- Successful delivery consumes that block immediately. There is no send
  history and no undo.
- Empty / hidden / paused / password / secure intervals do not auto-send.

## Capture and delivery

1. `Ctrl+Shift+B` or `Super+Shift+B` (hidden → visible) grants capture to the
   current Fcitx5 input context and shows the workbench. The host stays the
   delivery anchor; the workbench does not take keyboard focus.
2. The same hotkey, unmodified Escape, or Close pauses capture, hides the
   workbench, and keeps blocks.
3. While capturing, Space/number commits go to Buffer, not the host.
4. While capturing, a composing Return is fed to Rime so it can settle into a
   block. That same physical press must not send.
5. A ready Return tap (`< 1.2s`) is `sendNext`. Holding Return for 1.2s is
   `sendAll`. The paper-plane button is `sendNext` only.
6. Backspace edits an open direct tail or removes the last block. It never
   reaches the host while capturing.
7. Delivery calls `ic->commitString` on the **same** captured input context
   after re-checking that it is still focused. Focus change, password fields,
   and secure state fail closed and leave remaining blocks in place.
8. “Close after last delivery” defaults on: only a successful drain of the
   last pending block close-and-pauses.

## Workbench chrome

- ~760×78 window: 33px toolbar + divider + one full-width rail.
- Toolbar: Default label, status, passive target name, Close. Empty chrome
  is the X11 drag region.
- Rail: placeholder when idle and empty; otherwise chips + inline preedit.
- Trailing overlay: clipboard import and paper plane. They must not shrink
  the rail.
- Hold progress is a 2px bar along the bottom, not extra height.
- Password / secure snapshots scrub plaintext.

## Placement, focus, always-on-top

### X11 (Xfce and the CI Xvfb path)

- Borderless, skip-taskbar, `keep_above`, `accept_focus=false`.
- On first show, if Fcitx5 reports a caret rect, center horizontally on it
  and sit 10px below (or above if there is no room).
- The window does not follow later caret motion on the same display.

### wlroots Wayland (labwc / sway)

- `gtk-layer-shell` overlay layer, bottom-anchored, keyboard interactivity
  none. This is the supported always-on-top / no-focus-steal path.
- The compositor owns position. There is no reliable “10px under the caret”
  API on stock layer-shell. First show uses a bottom margin, not a caret
  rect.

### Other Wayland (GNOME, KDE)

- Without layer-shell the window is a normal xdg-toplevel. Position,
  always-on-top, and non-activating behavior are compositor-defined and
  **not** claimed.

## Deliberate macOS mismatches

| macOS behavior | Linux | Reason |
|---|---|---|
| Single IMK process owns UI | Companion `rimes-buffer` process + event-driven Unix socket | Fcitx5 addons must not assume a GTK display; a UI crash must not kill typing |
| Nonactivating `NSPanel` + Spaces | X11 keep-above / Wayland layer-shell | No AppKit / Spaces; Wayland clients cannot place or steal focus freely |
| Caret-anchored 10pt open | X11 only | wlroots layer-shell cannot place relative to a text caret |
| Invisible U+200B IMK guard | ZWSP client preedit while capturing | Same leak-prevention idea, Fcitx5 preedit protocol |
| Custom detached `CandidateWindow` | Stock Fcitx5 candidate panel | IME step 1 already uses Fcitx5 UI; a second chrome is a later port |
| Exact `FocusToken` + PID/bundle/AX box lock | IC pointer + focused-IC recheck | No IMK client identity and no Accessibility tree |
| `Command+Shift+B` while another IM is active | Only while RIMES is current | No LaunchAgent analogue in this step |
| AI / translation / stream / music plugins | Default only | Later components (and Capsule / Mailbox) |
| Secure Input + session lock/sleep | Password capability + snapshot scrub | No Carbon Secure Event Input; lock/sleep hide is manual-test only |
| Command+C generated-result copy | Not implemented | No generated-result workspace |
| Pin to all Spaces / display recovery | Not implemented | No Spaces |
| Themes 墨竹 / 翡翠 / 静谧 / Rasta | One Classic-like dark chrome | Visual port, not a theme engine |

## IPC

Length-prefixed JSON (4-byte big-endian + UTF-8) on
`$XDG_RUNTIME_DIR/rimes-buffer.sock` (override: `RIMES_BUFFER_SOCKET`).
The IME owns the model. The UI and `rimes-buffer-ctl` are clients.

Commands: `hello`, `status`, `toggle`, `show`, `close`, `send_next`,
`send_all`, `remove_last`, `select_all`, `paste`, `set_insertion`.

Environment for tests: `RIMES_BUFFER_HEADLESS=1` (no GTK spawn),
`RIMES_BUFFER_AUTO_CAPTURE=1` (capture on activate),
`RIMES_BUFFER_CLOSE_AFTER_LAST=0`.
