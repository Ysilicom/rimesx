#include <arpa/inet.h>
#include <cstdlib>
#include <cstring>
#include <fcntl.h>
#include <iostream>
#include <string>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

#include "buffer_protocol.hpp"

namespace {

int ConnectDelayMs() {
    const char* value = std::getenv("RIMES_BUFFER_CONNECT_DELAY_MS");
    if (value == nullptr || value[0] == '\0') {
        return 0;
    }
    char* end = nullptr;
    const auto parsed = std::strtol(value, &end, 10);
    if (end == value || parsed < 0 || parsed > 30000) {
        return 0;
    }
    return static_cast<int>(parsed);
}

std::string SocketPath(int argc, char** argv) {
    for (int index = 1; index + 1 < argc; ++index) {
        if (std::strcmp(argv[index], "--socket") == 0) {
            return argv[index + 1];
        }
    }
    if (const char* override_path = std::getenv("RIMES_BUFFER_SOCKET")) {
        if (override_path[0] != '\0') {
            return override_path;
        }
    }
    return {};
}

}  // namespace

int main(int argc, char** argv) {
    const auto path = SocketPath(argc, argv);
    if (path.empty()) {
        std::cerr << "rimes-buffer-ui-stub: missing --socket\n";
        return EXIT_FAILURE;
    }
    const int delay_ms = ConnectDelayMs();
    if (delay_ms > 0) {
        usleep(static_cast<useconds_t>(delay_ms) * 1000);
    }
    const int fd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (fd < 0) {
        return EXIT_FAILURE;
    }
    sockaddr_un address{};
    address.sun_family = AF_UNIX;
    std::strncpy(address.sun_path, path.c_str(), sizeof(address.sun_path) - 1);
    if (connect(fd, reinterpret_cast<sockaddr*>(&address), sizeof(address)) != 0) {
        close(fd);
        return EXIT_FAILURE;
    }
    std::string frame;
    if (!rimes::buffer::EncodeFrame(R"({"v":1,"op":"hello"})", &frame) ||
        send(fd, frame.data(), frame.size(), MSG_NOSIGNAL) !=
            static_cast<ssize_t>(frame.size())) {
        close(fd);
        return EXIT_FAILURE;
    }
    char sink[256];
    while (read(fd, sink, sizeof(sink)) > 0) {
    }
    close(fd);
    return EXIT_SUCCESS;
}
