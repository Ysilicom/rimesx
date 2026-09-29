# Linux Buffer — real-desktop checklist

Automated CI covers the C++ model, the in-process Fcitx5 `testfrontend` Buffer
suite, and a headless DBus/Xvfb path. It does **not** replace a real session.
Run this on Debian 13 Xfce (X11) and a wlroots Wayland session (labwc or sway).

Record distro, session type, compositor, and host for every row.

## Machines

| Distro | Session | Compositor | Host | Result |
|---|---|---|---|---|
| Debian 13 | X11 | Xfce | gedit | |
| Debian 13 | X11 | Xfce | kate | |
| Debian 13 | X11 | Xfce | Firefox | |
| Debian 13 | X11 | Xfce | xfce4-terminal / GNOME Terminal | |
| Debian 13 | Wayland | labwc | gedit | |
| Debian 13 | Wayland | labwc | kate | |
| Debian 13 | Wayland | labwc | Firefox | |
| Debian 13 | Wayland | labwc | foot / kitty / GNOME Terminal | |
| Debian 13 | Wayland | sway | gedit | |
| Ubuntu 24.04 | X11 or Wayland | any | one GTK + one terminal | |

Also try one machine with stock `fcitx5-rime` still installed: RIMES must stay
a separate IM, and `~/.local/share/rimes` must stay distinct from
`~/.local/share/fcitx5/rime`.

## Per-host cases

For each host above, switch to **RIMES** (雾凇全拼) first.

1. **Direct still works.** With Buffer hidden, type `nihao` + Space. The field
   contains `你好` and no leftover `nihao`.
2. **Open Buffer.** Press `Ctrl+Shift+B` (or `Super+Shift+B`). The workbench
   appears. The host keeps keyboard focus — the next key must not type into
   the Buffer window itself.
3. **Stage, do not leak.** Type `nihao` + Space. `你好` appears as a chip in
   Buffer. The host field is unchanged.
4. **Second block.** Type `shijie` + Space. Two chips: `你好` then `世界`.
5. **Send next.** Press Return once (or click the paper plane). Only `你好`
   is inserted. `世界` remains. No extra newline.
6. **Send all.** Stage two more commits. Hold Return about 1.2 seconds. Both
   remaining chips are inserted in order and disappear. The 2px progress bar
   appears while holding.
7. **Settle without sending.** Type `nihao` (do not press Space) then Return.
   Composition becomes a chip. That same press must not insert into the host.
   A second Return sends the chip.
8. **Backspace.** Stage one chip, press Backspace. The chip is gone. The host
   does not delete existing text.
9. **Escape / hotkey close.** Stage a chip, press Escape (or the hotkey, or
   Close). Buffer hides. The chip is still there when you reopen. The host
   did not receive it.
10. **Focus change.** Open Buffer, stage a chip, click another field or
    window. Later typing goes to the new field (direct). The old chip stays.
    Sending must not write into the new field until you reopen Buffer on it.
11. **Password.** Click a password field. Buffer must hide or scrub chips.
    Typed secrets must not appear in the workbench.
12. **Close after last.** With the default on, sending the last chip hides
    Buffer.
13. **X11 placement.** On Xfce, the first open sits near the caret when the
    toolkit reports one, not in a random corner. Drag the toolbar; the body
    rail does not drag.
14. **Wayland placement.** On labwc/sway the panel is overlay / always
    visible and does not steal focus. It may sit at the bottom instead of
    10px under the caret — that is expected.

## Session notes

- Xfce X11: confirm `gtk_window` keep-above above a maximized gedit.
- labwc/sway: if the panel is missing, check `libgtk-layer-shell0` is
  installed and `journalctl --user -u fcitx5` / `~/.local/share/rimes` logs.
- Firefox and terminals sometimes need `GTK_IM_MODULE=fcitx` in the session,
  not only the shell that launched the test.
- Electron/Chromium: if preedit leaks raw letters into the page while Buffer
  is capturing, record it. Linux uses a ZWSP client preedit guard, not IMK
  marked text.

## What this checklist does not cover

Capsule, Mailbox, AI / translation / stream / music plugins, IBus, and a
custom candidate window that follows the Buffer caret.
