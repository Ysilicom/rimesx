#include <cerrno>
#include <csignal>
#include <cstdlib>
#include <iostream>
#include <sys/wait.h>
#include <unistd.h>

#include "buffer_process.hpp"

namespace {

int failures = 0;

void Expect(bool condition, const char* message) {
    if (!condition) {
        std::cerr << "FAIL: " << message << '\n';
        ++failures;
    }
}

}  // namespace

int main() {
    Expect(rimes::buffer::UiProcessGone(0), "pid 0 is gone");
    Expect(rimes::buffer::UiProcessGone(-1), "negative pid is gone");

    const pid_t child = fork();
    if (child < 0) {
        std::cerr << "FAIL: fork\n";
        return EXIT_FAILURE;
    }
    if (child == 0) {
        _exit(0);
    }
    int status = 0;
    Expect(waitpid(child, &status, 0) == child, "parent reaped the child");
    Expect(rimes::buffer::UiProcessGone(child), "ECHILD after a prior waitpid is dead");

    const pid_t killed = fork();
    if (killed < 0) {
        std::cerr << "FAIL: fork killed child\n";
        return EXIT_FAILURE;
    }
    if (killed == 0) {
        pause();
        _exit(0);
    }
    Expect(kill(killed, SIGKILL) == 0, "SIGKILL the child");
    bool gone = false;
    for (int attempt = 0; attempt < 50 && !gone; ++attempt) {
        gone = rimes::buffer::UiProcessGone(killed);
        if (!gone) {
            usleep(2000);
        }
    }
    Expect(gone, "waitpid reaps a killed UI pid");

    if (failures != 0) {
        std::cerr << failures << " process-gone checks failed\n";
        return EXIT_FAILURE;
    }
    std::cout << "ok: buffer UI process-gone\n";
    return EXIT_SUCCESS;
}
