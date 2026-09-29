#pragma once

#include <cstdint>
#include <string>
#include <string_view>

namespace rimes::capsule {

// Clipboard writes are edge-triggered on copy_seq. The first snapshot
// after connect or respawn is only recorded, never copied, so a
// replacement UI cannot replay the last Ctrl+C.
inline bool ShouldWriteClipboard(bool seen_seq, std::uint64_t seen, std::uint64_t incoming,
                                 std::string_view last_copied) {
    if (!seen_seq) {
        return false;
    }
    if (incoming <= seen) {
        return false;
    }
    return !last_copied.empty();
}

enum class ClipboardWritePath {
    Skip,
    Gtk,
    WlCopy,
    GtkFallback,
};

struct ClipboardWriteDecision {
    ClipboardWritePath path = ClipboardWritePath::Skip;
    const char* status_override = nullptr;
};

struct ClipboardApplyState {
    bool seen_seq = false;
    std::uint64_t handled_seq = 0;
};

// Shown when Wayland copy has to use GTK because wl-copy is missing or
// failed. gtk_clipboard_set_text needs a recent input serial; a
// layer-shell bar with KEYBOARD_MODE_NONE never receives keyboard focus,
// so an unclicked bar often has a stale serial and the compositor drops
// the offer.
inline constexpr const char* kWaylandCopyFallbackHint =
    "Copy may need a click on the bar first — install wl-clipboard for reliable Ctrl+C";

inline ClipboardWriteDecision DecideClipboardWrite(bool should_write, bool wayland,
                                                   bool wl_copy_available) {
    ClipboardWriteDecision decision;
    if (!should_write) {
        return decision;
    }
    if (!wayland) {
        decision.path = ClipboardWritePath::Gtk;
        return decision;
    }
    if (wl_copy_available) {
        decision.path = ClipboardWritePath::WlCopy;
        return decision;
    }
    decision.path = ClipboardWritePath::GtkFallback;
    decision.status_override = kWaylandCopyFallbackHint;
    return decision;
}

inline ClipboardWriteDecision ApplyCopiedNote(ClipboardApplyState* state, std::uint64_t incoming,
                                              std::string_view last_copied, bool wayland,
                                              bool wl_copy_available) {
    const bool should =
        ShouldWriteClipboard(state->seen_seq, state->handled_seq, incoming, last_copied);
    const auto decision = DecideClipboardWrite(should, wayland, wl_copy_available);
    state->seen_seq = true;
    state->handled_seq = incoming;
    return decision;
}

// PATH lookup of a bare command name. Rejects names that contain '/'.
// Empty PATH entries (cwd) are skipped.
std::string FindOnPath(std::string_view name);

// Spawn `exe` as argv0 "wl-copy" with `text` on stdin. No shell.
bool SpawnWlCopyAt(const std::string& exe, std::string_view text);

// Find wl-copy on PATH and spawn it.
bool SpawnWlCopy(std::string_view text);

}  // namespace rimes::capsule
