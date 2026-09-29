#include <chrono>
#include <cstdlib>
#include <cstring>
#include <exception>
#include <filesystem>
#include <iostream>
#include <memory>
#include <string>
#include <thread>
#include <unistd.h>

#include <sys/socket.h>
#include <sys/un.h>

#include <fcitx-utils/capabilityflags.h>
#include <fcitx-utils/event.h>
#include <fcitx-utils/eventdispatcher.h>
#include <fcitx-utils/key.h>
#include <fcitx-utils/testing.h>
#include <fcitx/addonmanager.h>
#include <fcitx/inputcontextmanager.h>
#include <fcitx/inputmethodengine.h>
#include <fcitx/inputmethodgroup.h>
#include <fcitx/inputmethodmanager.h>
#include <fcitx/inputpanel.h>
#include <fcitx/instance.h>
#include <testfrontend_public.h>

#include "buffer_protocol.hpp"

namespace {

void Die(const std::string& message) {
    std::cerr << "FAIL: " << message << '\n';
    std::exit(EXIT_FAILURE);
}

void Type(fcitx::AddonInstance* frontend, const fcitx::ICUUID& uuid, const char* keys) {
    for (const char* cursor = keys; *cursor != '\0'; ++cursor) {
        const char name[] = {*cursor, '\0'};
        frontend->call<fcitx::ITestFrontend::keyEvent>(uuid, fcitx::Key(name), false);
        frontend->call<fcitx::ITestFrontend::keyEvent>(uuid, fcitx::Key(name), true);
    }
}

std::string IsolateDirs() {
    const auto root = std::filesystem::temp_directory_path() /
                      ("rimes-buffer-e2e-" + std::to_string(getpid()));
    std::error_code error;
    std::filesystem::remove_all(root, error);
    std::filesystem::create_directories(root / "user" / "log", error);
    if (error) {
        Die("could not create isolated dirs: " + error.message());
    }
    setenv("RIMES_USER_DIR", (root / "user").string().c_str(), 1);
    setenv("RIMES_LOG_DIR", (root / "user" / "log").string().c_str(), 1);
    const auto socket = root / "rimes-buffer.sock";
    setenv("RIMES_BUFFER_SOCKET", socket.string().c_str(), 1);
    setenv("RIMES_BUFFER_HEADLESS", "1", 1);
    setenv("RIMES_BUFFER_AUTO_CAPTURE", "1", 1);
    setenv("RIMES_BUFFER_CLOSE_AFTER_LAST", "0", 1);
    return socket.string();
}

int ConnectSocket(const std::string& path, int tries) {
    for (int attempt = 0; attempt < tries; ++attempt) {
        const int fd = socket(AF_UNIX, SOCK_STREAM, 0);
        if (fd < 0) {
            return -1;
        }
        sockaddr_un address{};
        address.sun_family = AF_UNIX;
        std::strncpy(address.sun_path, path.c_str(), sizeof(address.sun_path) - 1);
        if (connect(fd, reinterpret_cast<sockaddr*>(&address), sizeof(address)) == 0) {
            return fd;
        }
        close(fd);
        std::this_thread::sleep_for(std::chrono::milliseconds(50));
    }
    return -1;
}

std::string Request(const std::string& path, const std::string& op) {
    const int fd = ConnectSocket(path, 40);
    if (fd < 0) {
        Die("buffer socket is not listening at " + path);
    }
    const auto payload = std::string("{\"v\":1,\"op\":\"") + op + "\"}";
    std::string frame;
    if (!rimes::buffer::EncodeFrame(payload, &frame) ||
        send(fd, frame.data(), frame.size(), 0) != static_cast<ssize_t>(frame.size())) {
        close(fd);
        Die("could not send " + op);
    }
    std::string incoming;
    const auto deadline = std::chrono::steady_clock::now() + std::chrono::seconds(3);
    while (std::chrono::steady_clock::now() < deadline) {
        char chunk[4096];
        const auto got = recv(fd, chunk, sizeof(chunk), 0);
        if (got <= 0) {
            break;
        }
        incoming.append(chunk, static_cast<std::size_t>(got));
        if (incoming.size() >= 4) {
            std::uint32_t length = 0;
            if (!rimes::buffer::DecodeFrameHeader(incoming.data(), &length)) {
                break;
            }
            if (incoming.size() >= 4 + length) {
                close(fd);
                return incoming.substr(4, length);
            }
        }
    }
    close(fd);
    Die("no snapshot after " + op);
    return {};
}

void ExpectContains(const std::string& haystack, const char* needle, const char* message) {
    if (haystack.find(needle) == std::string::npos) {
        std::cerr << haystack << '\n';
        Die(message);
    }
}

void ExpectMissing(const std::string& haystack, const char* needle, const char* message) {
    if (haystack.find(needle) != std::string::npos) {
        std::cerr << haystack << '\n';
        Die(message);
    }
}

void RunBufferSuite(fcitx::Instance& instance,
                    fcitx::AddonInstance* frontend,
                    const fcitx::ICUUID& uuid,
                    fcitx::InputContext* ic,
                    const std::string& socket_path) {
    ic->setCapabilityFlags(fcitx::CapabilityFlags{fcitx::CapabilityFlag::Preedit} |
                           fcitx::CapabilityFlag::ClientUnfocusCommit);

    auto snapshot = Request(socket_path, "status");
    ExpectContains(snapshot, "\"capturing\":true", "AUTO_CAPTURE did not enable capture");

    Type(frontend, uuid, "nihao");
    frontend->call<fcitx::ITestFrontend::keyEvent>(uuid, fcitx::Key("space"), false);
    frontend->call<fcitx::ITestFrontend::keyEvent>(uuid, fcitx::Key("space"), true);
    snapshot = Request(socket_path, "status");
    ExpectContains(snapshot, "你好", "Rime commit was not staged into Buffer");
    ExpectContains(snapshot, "\"capturing\":true", "capture lost after commit");
    std::cout << "ok: buffer staged nihao + Space\n";

    Type(frontend, uuid, "shijie");
    frontend->call<fcitx::ITestFrontend::keyEvent>(uuid, fcitx::Key("space"), false);
    frontend->call<fcitx::ITestFrontend::keyEvent>(uuid, fcitx::Key("space"), true);
    snapshot = Request(socket_path, "status");
    ExpectContains(snapshot, "世界", "second commit was not staged");
    std::cout << "ok: buffer kept a second block\n";

    frontend->call<fcitx::ITestFrontend::pushCommitExpectation>("你好");
    snapshot = Request(socket_path, "send_next");
    ExpectMissing(snapshot, "你好", "send_next left the delivered block");
    ExpectContains(snapshot, "世界", "send_next consumed more than one block");
    std::cout << "ok: buffer send_next delivered 你好\n";

    frontend->call<fcitx::ITestFrontend::pushCommitExpectation>("世界");
    frontend->call<fcitx::ITestFrontend::keyEvent>(uuid, fcitx::Key("Return"), false);
    frontend->call<fcitx::ITestFrontend::keyEvent>(uuid, fcitx::Key("Return"), true);
    snapshot = Request(socket_path, "status");
    ExpectMissing(snapshot, "世界", "Return tap did not send the remaining block");
    std::cout << "ok: buffer Return tap sendNext\n";

    Type(frontend, uuid, "nihao");
    frontend->call<fcitx::ITestFrontend::keyEvent>(uuid, fcitx::Key("space"), false);
    frontend->call<fcitx::ITestFrontend::keyEvent>(uuid, fcitx::Key("space"), true);
    snapshot = Request(socket_path, "remove_last");
    ExpectMissing(snapshot, "你好", "Backspace/remove_last did not drop the block");
    std::cout << "ok: buffer remove_last\n";

    Type(frontend, uuid, "nihao");
    frontend->call<fcitx::ITestFrontend::keyEvent>(uuid, fcitx::Key("Return"), false);
    frontend->call<fcitx::ITestFrontend::keyEvent>(uuid, fcitx::Key("Return"), true);
    snapshot = Request(socket_path, "status");
    ExpectContains(snapshot, "你好", "composing Return must settle into a block");
    frontend->call<fcitx::ITestFrontend::pushCommitExpectation>("你好");
    snapshot = Request(socket_path, "send_all");
    ExpectMissing(snapshot, "你好", "send_all left staged text");
    std::cout << "ok: buffer composing Return settles, send_all delivers\n";

    snapshot = Request(socket_path, "close");
    ExpectContains(snapshot, "\"visible\":false", "close did not hide");
    ExpectContains(snapshot, "\"capturing\":false", "close did not pause capture");

    frontend->call<fcitx::ITestFrontend::pushCommitExpectation>("你好");
    Type(frontend, uuid, "nihao");
    frontend->call<fcitx::ITestFrontend::keyEvent>(uuid, fcitx::Key("space"), false);
    frontend->call<fcitx::ITestFrontend::keyEvent>(uuid, fcitx::Key("space"), true);
    std::cout << "ok: buffer paused; later commits go to the host\n";

    frontend->call<fcitx::ITestFrontend::destroyInputContext>(uuid);
    instance.exit();
}

}  // namespace

int main(int argc, char** argv) {
    if (argc < 4) {
        std::cerr << "Usage: rimes-buffer-fcitx-e2e <build-dir> <addon-rel-dir> <data-rel-dir>\n";
        return EXIT_FAILURE;
    }

    const auto socket_path = IsolateDirs();
    fcitx::setupTestingEnvironment(argv[1], {argv[2]}, {argv[3]});

    char arg0[] = "rimes-buffer-fcitx-e2e";
    char arg1[] = "--disable=all";
    char arg2[] = "--enable=testfrontend,testui,rimes";
    char* args[] = {arg0, arg1, arg2};
    fcitx::Instance instance(3, args);
    instance.addonManager().registerDefaultLoader(nullptr);

    fcitx::EventDispatcher dispatcher;
    dispatcher.attach(&instance.eventLoop());

    fcitx::AddonInstance* frontend = nullptr;
    fcitx::ICUUID uuid{};
    fcitx::InputContext* ic = nullptr;
    std::unique_ptr<fcitx::EventSourceTime> wait_timer;
    const auto deadline = std::chrono::steady_clock::now() + std::chrono::seconds(180);
    bool suite_started = false;

    dispatcher.schedule([&]() {
        auto& manager = instance.inputMethodManager();
        if (manager.entry("rimes") == nullptr) {
            Die("input method 'rimes' was not registered");
        }
        if (manager.groupCount() == 0) {
            manager.addEmptyGroup("Default");
            manager.setGroupOrder({"Default"});
        }
        fcitx::InputMethodGroup group("Default");
        group.setDefaultLayout("us");
        group.inputMethodList().emplace_back(fcitx::InputMethodGroupItem("rimes"));
        group.setDefaultInputMethod("rimes");
        manager.setGroup(group);
        manager.setCurrentGroup("Default");

        frontend = instance.addonManager().addon("testfrontend", true);
        if (frontend == nullptr) {
            Die("testfrontend addon did not load");
        }
        uuid = frontend->call<fcitx::ITestFrontend::createInputContext>("rimes-buffer-e2e");
        ic = instance.inputContextManager().findByUUID(uuid);
        if (ic == nullptr) {
            Die("test input context was not created");
        }
        instance.setCurrentInputMethod(ic, "rimes", true);
        if (instance.inputMethod(ic) != "rimes") {
            Die("could not switch the test context to rimes");
        }

        wait_timer = instance.eventLoop().addTimeEvent(
            CLOCK_MONOTONIC, fcitx::now(CLOCK_MONOTONIC) + 50000, 50000,
            [&](fcitx::EventSourceTime* source, uint64_t) {
                if (std::chrono::steady_clock::now() > deadline) {
                    Die("addon stayed in Deploying; deploy-ready notify never ran");
                }
                auto* ime = instance.inputMethodEngine(ic);
                const auto* entry = instance.inputMethodEntry(ic);
                if (ime != nullptr && entry != nullptr &&
                    ime->subMode(*entry, *ic) == "Deploying") {
                    source->setTime(fcitx::now(CLOCK_MONOTONIC) + 200000);
                    source->setOneShot();
                    return true;
                }
                if (suite_started) {
                    return true;
                }
                suite_started = true;
                std::cout << "ok: buffer testfrontend left Deploying\n";
                RunBufferSuite(instance, frontend, uuid, ic, socket_path);
                return true;
            });
    });

    try {
        return instance.exec();
    } catch (const fcitx::InstanceQuietQuit&) {
        return EXIT_SUCCESS;
    } catch (const std::exception& exception) {
        Die(exception.what());
    }
}
