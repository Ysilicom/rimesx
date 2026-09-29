# RIMES Capsule (Linux)

Note library and bottom rail for the Fcitx5 RIMES addon. Behavior and the
deliberate macOS gaps are in [SPEC.md](SPEC.md). Real-desktop checks are in
[MANUAL-TEST.md](MANUAL-TEST.md).

## Layout

- `core/` — Markdown store, rail model, JSON snapshot. Shared by the addon,
  `rimes-capsule`, `rimes-capsule-ctl`, and tests. New C++ of the macOS
  contract, not a rewrite of the Swift sources.
- `ui/` — GTK 3 rail. X11: keep-above, bottom-centered. wlroots Wayland:
  `gtk-layer-shell` overlay stretched with a 940×208 size request.
- `ctl/` — `rimes-capsule-ctl` talks to the IME socket without a display.
- Built from `platforms/linux/ime/CMakeLists.txt` and shipped in the same
  `fcitx5-rimes` `.deb`.

## How it is wired

`CapsuleService` lives next to `BufferService`. Toggle is
`Ctrl+Shift+V` / `Super+Shift+V` while RIMES is current. While the rail is
armed on an input context, navigation and Return are consumed before
Rime. A selected note is inserted with `InputContext::commitString` after
the same-IC recheck. The GTK process is a renderer. It does not run
librime.

## Build

```bash
platforms/linux/ime/scripts/deps.sh --install
platforms/linux/ime/scripts/build.sh
ctest --test-dir platforms/linux/ime/build --output-on-failure
platforms/linux/ime/tests/e2e/e2e.sh --build-dir platforms/linux/ime/build
```

## Runtime

| Item | Value |
|---|---|
| Toggle | `Ctrl+Shift+V` or `Super+Shift+V` while RIMES is current |
| Close | same hotkey, Escape on the armed field, or the close button |
| Insert selected note | Return, `Ctrl+1`–`Ctrl+9`, or double-click |
| Copy selected note | `Ctrl+C` (clipboard only) |
| Socket | `$XDG_RUNTIME_DIR/rimes-capsule.sock` |
| Store | `$XDG_DATA_HOME/rimes/capsule` |
| UI binary | `/usr/libexec/rimes/rimes-capsule` and `/usr/bin/rimes-capsule` |
