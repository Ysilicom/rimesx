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
   appears while holding. Keep holding to ~1.7s: the host must **not**
   receive extra Enter / newlines (a terminal must not execute the line).
7. **Settle without sending.** Type `nihao` (do not press Space) then Return.
   rime_ice maps Return to `commit_raw_input`, so the chip is `nihao` (not
   `你好`). That same press must not insert into the host. A second Return
   sends the chip.
8. **Backspace.** Stage one chip, press Backspace. The chip is gone. The host
   does not delete existing text.
9. **Escape / hotkey close.** Stage a chip, press Escape **in that same
   field** (or the hotkey, or Close). Buffer hides. The chip is still there
   when you reopen. The host did not receive it.
10. **Focus change.** Open Buffer, stage a chip, click another field or
    window. Later typing goes to the new field (direct). The old chip stays.
    Sending must not write into the new field until you reopen Buffer on it.
    The field you leave must **not** gain U+200B / ZWSP. In the new field
    (including a Firefox password box) press Escape: that app must receive
    Escape; Buffer must stay visible.
11. **Password.** Click a password field in a GTK host that keeps the IM
    enabled. Buffer must hide or scrub chips. Typed secrets must not appear
    in the workbench. **Firefox-esr disables the IM on
    `<input type=password>`**, so the password flag never reaches the addon:
    chips can stay visible and scrub will not run. Still confirm bugs 2/3
    do not inject ZWSP or swallow Escape in that field.
12. **Close after last.** With the default on, sending the last chip hides
    Buffer.
13. **X11 placement.** On Xfce, the first open sits below the caret with
    room for the stock candidate popup (not overlapping the typed-text
    rail). Drag the toolbar; the body rail does not drag. If drag fails,
    record the WM — `UTILITY` + `begin_move_drag` is the intended path.
14. **Wayland placement.** On labwc/sway the panel is overlay / always
    visible, roughly 760px wide (not a ~184×75 sliver), chips are readable,
    and it does not steal focus. It may sit at the bottom instead of under
    the caret — that is expected.
15. **UI respawn.** While capturing, `kill -9` the `rimes-buffer` process.
    Stage or toggle again: the panel must reappear without restarting
    fcitx5. Staging/send must keep working.

## Session notes

- Xfce X11: confirm `gtk_window` keep-above above a maximized gedit.
- labwc/sway: if the panel is missing, check `libgtk-layer-shell0` is
  installed and `journalctl --user -u fcitx5` / `~/.local/share/rimes` logs.
- Firefox and terminals sometimes need `GTK_IM_MODULE=fcitx` in the session,
  not only the shell that launched the test.
- Electron/Chromium: if preedit leaks raw letters into the page while Buffer
  is capturing, record it. Linux no longer installs a client-preedit ZWSP
  (GTK/VTE/Gecko would commit it on focus-out).
- Clipboard button is a GTK `edit-paste` icon. An empty box means the icon
  theme is missing, not U+2398 tofu.

## What this checklist does not cover

Capsule, Mailbox, AI / translation / stream / music plugins, IBus, and a
custom candidate window that follows the Buffer caret.
