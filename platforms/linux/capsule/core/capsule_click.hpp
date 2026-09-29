#pragma once

#include <cstdint>

namespace rimes::capsule {

// Fallback when GTK rebuilds the card widget between the two presses of a
// double-click and never emits GDK_2BUTTON_PRESS.
inline constexpr int kDoubleClickMs = 400;

inline bool ShouldActivateOnPress(int index, int last_index, std::int64_t now_ms,
                                  std::int64_t last_ms, bool gdk_double) {
    if (gdk_double) {
        return true;
    }
    if (index < 0 || last_index < 0 || index != last_index) {
        return false;
    }
    if (last_ms < 0 || now_ms < last_ms) {
        return false;
    }
    return (now_ms - last_ms) <= kDoubleClickMs;
}

}  // namespace rimes::capsule
