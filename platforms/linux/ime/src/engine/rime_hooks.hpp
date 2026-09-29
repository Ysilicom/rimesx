#pragma once

// Buffer / Capsule / Mailbox integration points for the Linux IME.
//
// Buffer (this PR) hooks the commit and key surfaces below. Capsule and
// Mailbox are still later ports and must not fork a second librime runtime.
//
// 1. Commit
//    RimesIme::commitText is the only path from librime commit text into a
//    Fcitx5 InputContext. When Buffer capture owns that IC, the text becomes
//    a staged block instead of ic->commitString — the same intercept as macOS
//    drainCommit before Delivery.insert.
//
// 2. Keys
//    BufferService::HandleEarlyKey sits in front of RimesState::keyEvent.
//    Ctrl/Super+Shift+B toggles the workbench. While capturing, Return,
//    Backspace, Escape (this IC only), Ctrl+A and Ctrl+V are consumed here.
//    After send-all closes the panel, Return auto-repeats are eaten until
//    key-up so they cannot leak Enter into the host. A composing Return is
//    passed through to Rime so it can settle, then must not send. Same-IC
//    reactivation keeps capture only during an explicit toolbar drag
//    (until the pointer is released, plus a 1 s tail). The first same-IC
//    reactivation after drag_begin consumes the drag. Every other
//    same-IC reactivation — including a Firefox same-page field switch
//    — stages the raw input and returns the route to the host.
//
// 3. Session / field isolation
//    One Rime session per Fcitx5 InputContext (RimesState). Buffer is
//    process-wide and binds capture to one IC pointer token. Focus change
//    returns typing to the host and keeps staged blocks.
//
// 4. UI
//    Candidates stay on Fcitx5's InputPanel. The GTK workbench is a
//    companion process (rimes-buffer) talking length-prefixed JSON over a
//    Unix socket. It is a renderer, not a second engine. A dead UI (ECHILD
//    after Fcitx5 reaps SIGCHLD, or a dropped socket) is respawned.
//
// 5. Still later
//    - Capsule / Mailbox
//    - Custom candidate chrome that follows the Buffer caret
//    - Global hotkeys while another IM is active
//    - Cross-batch chord pairing (macOS frontend only)
//    - IBus engine (would reuse RimeEngine + these hooks)

namespace rimes::linuxime {

inline constexpr const char* kCommitHookNote =
    "RimesIme::commitText is the single Fcitx5 commit path";

}  // namespace rimes::linuxime
