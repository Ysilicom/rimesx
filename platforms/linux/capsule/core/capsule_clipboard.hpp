#pragma once

#include <cstdint>
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

}  // namespace rimes::capsule
