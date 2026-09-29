#pragma once

#include <sys/types.h>

namespace rimes::buffer {

// True when waitpid/kill say this child is gone. Treats ECHILD as dead so a
// parent that already reaped SIGCHLD (Fcitx5's event loop) still respawns.
bool UiProcessGone(pid_t pid);

// SIGKILL + reap a leftover UI child before a replacement is forked.
// No-op when pid is already gone (including ECHILD).
void DiscardUiProcess(pid_t pid);

// Force-clear the recorded pid only when it is still the one that dropped
// the socket. A child this retry sequence started must be given time to
// connect — GTK can take well over 150 ms on slow hardware.
bool ShouldForceUiRespawn(pid_t current_pid, pid_t dropped_pid);

}  // namespace rimes::buffer
