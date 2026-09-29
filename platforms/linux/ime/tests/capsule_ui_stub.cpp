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

bool EnvFlag(const char* name) {
    const char* value = std::getenv(name);
    return value != nullptr && value[0] != '\0' && std::strcmp(value, "0") != 0;
}

void AppendLog(const char* path, std::string_view text) {
    if (path == nullptr || path[0] == '\0') {
        return;
    }
    FILE* log = std::fopen(path, "a");
    if (log == nullptr) {
        return;
    }
    std::fwrite(text.data(), 1, text.size(), log);
    std::fputc('\n', log);
    std::fclose(log);
}

void HandleCopiedNote(const rimes::capsule::Snapshot& snapshot,
                      rimes::capsule::ClipboardApplyState* state) {
    const bool wayland = EnvFlag("RIMES_CAPSULE_WAYLAND_CLIPBOARD");
    const bool have_wl_copy = wayland && !rimes::capsule::FindOnPath("wl-copy").empty();
    const auto decision = rimes::capsule::ApplyCopiedNote(
        state, snapshot.copy_seq, snapshot.last_copied, wayland, have_wl_copy);
    if (decision.path == rimes::capsule::ClipboardWritePath::Skip) {
        return;
    }
    if (decision.path == rimes::capsule::ClipboardWritePath::WlCopy) {
        rimes::capsule::SpawnWlCopy(snapshot.last_copied);
    }
    if (decision.status_override != nullptr) {
        AppendLog(std::getenv("RIMES_CAPSULE_COPY_HINT_LOG"), decision.status_override);
    }
    AppendLog(std::getenv("RIMES_CAPSULE_CLIPBOARD_LOG"), snapshot.last_copied);
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
    rimes::capsule::ClipboardApplyState copy_state;
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
            HandleCopiedNote(snapshot, &copy_state);
        }
    }
    close(fd);
    return EXIT_SUCCESS;
}
