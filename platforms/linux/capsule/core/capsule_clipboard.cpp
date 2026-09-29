#include "capsule_clipboard.hpp"

#include <fcntl.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>

#include <cerrno>
#include <cstdlib>
#include <string>

namespace rimes::capsule {
namespace {

bool WriteAll(int fd, std::string_view text) {
    std::size_t offset = 0;
    while (offset < text.size()) {
        const ssize_t wrote = write(fd, text.data() + offset, text.size() - offset);
        if (wrote < 0) {
            if (errno == EINTR) {
                continue;
            }
            return false;
        }
        if (wrote == 0) {
            return false;
        }
        offset += static_cast<std::size_t>(wrote);
    }
    return true;
}

}  // namespace

std::string FindOnPath(std::string_view name) {
    if (name.empty() || name.find('/') != std::string_view::npos) {
        return {};
    }
    const char* path_env = std::getenv("PATH");
    const std::string path = path_env != nullptr ? std::string(path_env) : std::string("/usr/bin:/bin");
    std::size_t start = 0;
    while (start <= path.size()) {
        const auto sep = path.find(':', start);
        const auto dir = path.substr(start, sep == std::string::npos ? std::string::npos : sep - start);
        if (!dir.empty()) {
            std::string candidate = dir;
            if (candidate.back() != '/') {
                candidate.push_back('/');
            }
            candidate.append(name.begin(), name.end());
            struct stat info {};
            if (access(candidate.c_str(), X_OK) == 0 && stat(candidate.c_str(), &info) == 0 &&
                S_ISREG(info.st_mode)) {
                return candidate;
            }
        }
        if (sep == std::string::npos) {
            break;
        }
        start = sep + 1;
    }
    return {};
}

bool SpawnWlCopyAt(const std::string& exe, std::string_view text) {
    if (exe.empty() || exe.find('\0') != std::string::npos) {
        return false;
    }
    int pipefd[2];
    if (pipe(pipefd) != 0) {
        return false;
    }
    const pid_t pid = fork();
    if (pid < 0) {
        close(pipefd[0]);
        close(pipefd[1]);
        return false;
    }
    if (pid == 0) {
        close(pipefd[1]);
        if (dup2(pipefd[0], STDIN_FILENO) < 0) {
            _exit(127);
        }
        close(pipefd[0]);
        const int null_fd = open("/dev/null", O_WRONLY | O_CLOEXEC);
        if (null_fd >= 0) {
            dup2(null_fd, STDOUT_FILENO);
            dup2(null_fd, STDERR_FILENO);
            if (null_fd > STDERR_FILENO) {
                close(null_fd);
            }
        }
        execl(exe.c_str(), "wl-copy", static_cast<char*>(nullptr));
        _exit(127);
    }
    close(pipefd[0]);
    const bool wrote = WriteAll(pipefd[1], text);
    close(pipefd[1]);
    int status = 0;
    if (waitpid(pid, &status, 0) < 0) {
        return false;
    }
    return wrote && WIFEXITED(status) && WEXITSTATUS(status) == 0;
}

bool SpawnWlCopy(std::string_view text) {
    const auto exe = FindOnPath("wl-copy");
    if (exe.empty()) {
        return false;
    }
    return SpawnWlCopyAt(exe, text);
}

}  // namespace rimes::capsule
