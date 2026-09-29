#pragma once

#include <sys/types.h>

namespace rimes::buffer {

// True when waitpid/kill say this child is gone. Treats ECHILD as dead so a
// parent that already reaped SIGCHLD (Fcitx5's event loop) still respawns.
bool UiProcessGone(pid_t pid);

}  // namespace rimes::buffer
