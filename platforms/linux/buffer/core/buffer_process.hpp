#pragma once

#include <sys/types.h>

namespace rimes::buffer {

// True when waitpid/kill say this child is gone. Treats ECHILD as dead so a
// parent that already reaped SIGCHLD (Fcitx5's event loop) still respawns.
bool UiProcessGone(pid_t pid);

// After the UI socket drops, the pid can still look alive for a beat
// (not yet a zombie). The second retry treats that pid as dead so we
// fork a replacement without waiting for the next keystroke.
bool ShouldForceUiRespawn(bool process_gone, int retry_attempt);

}  // namespace rimes::buffer
