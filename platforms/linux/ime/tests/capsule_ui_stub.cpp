#include <arpa/inet.h>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fcntl.h>
#include <iostream>
#include <string>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

#include "buffer_protocol.hpp"
#include "capsule_clipboard.hpp"
#include "capsule_protocol.hpp"

namespace {

int ConnectDelayMs() {
    const char* value = std::getenv("RIMES_CAPSULE_CONNECT_DELAY_MS");
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
    if (const char* override_path = std::getenv("RIMES_CAPSULE_SOCKET")) {
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
        std::cerr << "rimes-capsule-ui-stub: missing --socket\n";
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
    const char* clipboard_log = std::getenv("RIMES_CAPSULE_CLIPBOARD_LOG");
    bool seen_copy_seq = false;
    std::uint64_t handled_copy_seq = 0;
    std::string incoming;
    char chunk[4096];
    while (true) {
        const auto got = read(fd, chunk, sizeof(chunk));
        if (got <= 0) {
            break;
        }
        incoming.append(chunk, static_cast<std::size_t>(got));
        while (incoming.size() >= 4) {
            std::uint32_t length = 0;
            if (!rimes::buffer::DecodeFrameHeader(incoming.data(), &length)) {
                close(fd);
                return EXIT_FAILURE;
            }
            if (incoming.size() < 4 + length) {
                break;
            }
            const std::string payload = incoming.substr(4, length);
            incoming.erase(0, 4 + length);
            rimes::capsule::Snapshot snapshot;
            if (!rimes::capsule::DecodeSnapshot(payload, &snapshot)) {
                continue;
            }
            if (rimes::capsule::ShouldWriteClipboard(seen_copy_seq, handled_copy_seq,
                                                    snapshot.copy_seq, snapshot.last_copied) &&
                clipboard_log != nullptr && clipboard_log[0] != '\0') {
                FILE* log = std::fopen(clipboard_log, "a");
                if (log != nullptr) {
                    std::fwrite(snapshot.last_copied.data(), 1, snapshot.last_copied.size(),
                                log);
                    std::fputc('\n', log);
                    std::fclose(log);
                }
            }
            seen_copy_seq = true;
            handled_copy_seq = snapshot.copy_seq;
        }
    }
    close(fd);
    return EXIT_SUCCESS;
}
