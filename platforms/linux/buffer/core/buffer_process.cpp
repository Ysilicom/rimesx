#include "buffer_process.hpp"

#include <cerrno>
#include <csignal>
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

}  // namespace rimes::buffer
