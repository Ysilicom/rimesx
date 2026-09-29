#include "buffer_process.hpp"

#include <cerrno>
#include <csignal>
#include <unistd.h>
#include <sys/wait.h>

namespace rimes::buffer {

bool UiProcessGone(pid_t pid) {
    if (pid <= 0) {
        return true;
    }
    int status = 0;
    const pid_t ended = waitpid(pid, &status, WNOHANG);
    if (ended == pid) {
        return true;
    }
    if (ended < 0 && errno == ECHILD) {
        return true;
    }
    if (kill(pid, 0) != 0 && errno == ESRCH) {
        return true;
    }
    return false;
}

void DiscardUiProcess(pid_t pid) {
    if (pid <= 0) {
        return;
    }
    if (!UiProcessGone(pid)) {
        kill(pid, SIGKILL);
    }
    for (int attempt = 0; attempt < 50 && !UiProcessGone(pid); ++attempt) {
        usleep(2000);
    }
}

bool ShouldForceUiRespawn(pid_t current_pid, pid_t dropped_pid) {
    return current_pid > 0 && dropped_pid > 0 && current_pid == dropped_pid;
}

}  // namespace rimes::buffer
