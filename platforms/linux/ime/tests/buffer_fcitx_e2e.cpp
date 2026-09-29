#include <chrono>
#include <cstdlib>
#include <cstring>
#include <exception>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <memory>
#include <stdexcept>
#include <string>
#include <unistd.h>

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

namespace {

int g_exit_status = EXIT_SUCCESS;

struct TestFailed : std::runtime_error {
    explicit TestFailed(const std::string& message) : std::runtime_error(message) {}
};

void Die(const std::string& message) {
    std::cerr << "FAIL: " << message << '\n';
    g_exit_status = EXIT_FAILURE;
    throw TestFailed(message);
}

void Type(fcitx::AddonInstance* frontend, const fcitx::ICUUID& uuid, const char* keys) {
    for (const char* cursor = keys; *cursor != '\0'; ++cursor) {
        const char name[] = {*cursor, '\0'};
        frontend->call<fcitx::ITestFrontend::keyEvent>(uuid, fcitx::Key(name), false);
        frontend->call<fcitx::ITestFrontend::keyEvent>(uuid, fcitx::Key(name), true);
    }
}

void SendKey(fcitx::AddonInstance* frontend, const fcitx::ICUUID& uuid, const char* name) {
    frontend->call<fcitx::ITestFrontend::keyEvent>(uuid, fcitx::Key(name), false);
    frontend->call<fcitx::ITestFrontend::keyEvent>(uuid, fcitx::Key(name), true);
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
    static std::string user_env;
    static std::string log_env;
    static std::string socket_env;
    static std::string dump_env;
    user_env = (root / "user").string();
    log_env = (root / "user" / "log").string();
    socket_env = (root / "rimes-buffer.sock").string();
    dump_env = (root / "buffer-snapshot.json").string();
    setenv("RIMES_USER_DIR", user_env.c_str(), 1);
    setenv("RIMES_LOG_DIR", log_env.c_str(), 1);
    setenv("RIMES_BUFFER_SOCKET", socket_env.c_str(), 1);
    setenv("RIMES_BUFFER_DUMP", dump_env.c_str(), 1);
    setenv("RIMES_BUFFER_HEADLESS", "1", 1);
    // Enable via the user hotkey. AUTO_CAPTURE plus a synthetic key that also
    // activates the IC would Toggle the workbench closed in the same event.
    setenv("RIMES_BUFFER_AUTO_CAPTURE", "0", 1);
    setenv("RIMES_BUFFER_CLOSE_AFTER_LAST", "0", 1);
    return dump_env;
}

std::string ReadDump(const std::string& path) {
    std::ifstream in(path);
    if (!in) {
        Die("buffer dump is missing at " + path);
    }
    std::string json((std::istreambuf_iterator<char>(in)), std::istreambuf_iterator<char>());
    if (json.empty()) {
        Die("buffer dump is empty");
    }
    return json;
}

bool HasNeedle(const std::string& haystack, const char* needle) {
    return haystack.find(needle) != std::string::npos;
}

void ExpectContains(const std::string& haystack, const char* needle, const char* message) {
    if (!HasNeedle(haystack, needle)) {
        std::cerr << haystack << '\n';
        Die(message);
    }
}

void ExpectMissing(const std::string& haystack, const char* needle, const char* message) {
    if (HasNeedle(haystack, needle)) {
        std::cerr << haystack << '\n';
        Die(message);
    }
}

void RunBufferSuite(fcitx::Instance& instance,
                    fcitx::AddonInstance* frontend,
                    const fcitx::ICUUID& uuid,
                    fcitx::InputContext* ic,
                    const std::string& dump_path) {
    ic->setCapabilityFlags(fcitx::CapabilityFlags{fcitx::CapabilityFlag::Preedit} |
                           fcitx::CapabilityFlag::ClientUnfocusCommit);

    SendKey(frontend, uuid, "Control+Shift+B");
    auto snapshot = ReadDump(dump_path);
    ExpectContains(snapshot, "\"capturing\":true", "Ctrl+Shift+B did not enable capture");
    ExpectContains(snapshot, "\"visible\":true", "Ctrl+Shift+B did not show the workbench");
    std::cout << "ok: Ctrl+Shift+B enabled capture\n";

    SendKey(frontend, uuid, "Control+Shift+B");
    snapshot = ReadDump(dump_path);
    ExpectContains(snapshot, "\"capturing\":false", "hotkey did not pause capture");
    ExpectContains(snapshot, "\"visible\":false", "hotkey did not hide the workbench");
    std::cout << "ok: Ctrl+Shift+B paused an open workbench\n";

    SendKey(frontend, uuid, "Control+Shift+B");
    snapshot = ReadDump(dump_path);
    ExpectContains(snapshot, "\"capturing\":true", "hotkey did not resume capture");
    ExpectContains(snapshot, "\"visible\":true", "hotkey did not show the workbench");
    std::cout << "ok: Ctrl+Shift+B resumed capture\n";

    Type(frontend, uuid, "nihao");
    SendKey(frontend, uuid, "space");
    snapshot = ReadDump(dump_path);
    ExpectContains(snapshot, "你好", "Rime commit was not staged into Buffer");
    ExpectContains(snapshot, "\"capturing\":true", "capture lost after commit");
    std::cout << "ok: buffer staged nihao + Space\n";

    Type(frontend, uuid, "shijie");
    SendKey(frontend, uuid, "space");
    snapshot = ReadDump(dump_path);
    ExpectContains(snapshot, "世界", "second commit was not staged");
    std::cout << "ok: buffer kept a second block\n";

    frontend->call<fcitx::ITestFrontend::pushCommitExpectation>("你好");
    SendKey(frontend, uuid, "Return");
    snapshot = ReadDump(dump_path);
    ExpectMissing(snapshot, "你好", "Return tap left the delivered block");
    ExpectContains(snapshot, "世界", "Return tap consumed more than one block");
    std::cout << "ok: buffer Return tap sendNext delivered 你好\n";

    frontend->call<fcitx::ITestFrontend::pushCommitExpectation>("世界");
    SendKey(frontend, uuid, "Return");
    snapshot = ReadDump(dump_path);
    ExpectMissing(snapshot, "世界", "second Return tap did not send the remaining block");
    std::cout << "ok: buffer second Return tap sendNext\n";

    Type(frontend, uuid, "nihao");
    SendKey(frontend, uuid, "space");
    SendKey(frontend, uuid, "BackSpace");
    snapshot = ReadDump(dump_path);
    ExpectMissing(snapshot, "你好", "Backspace did not drop the staged block");
    std::cout << "ok: buffer Backspace remove_last\n";

    Type(frontend, uuid, "nihao");
    SendKey(frontend, uuid, "Return");
    snapshot = ReadDump(dump_path);
    // rime_ice maps Return to commit_raw_input, so the settled chip is the
    // spelling, not the highlighted candidate.
    ExpectContains(snapshot, "nihao", "composing Return must settle into a block");
    ExpectContains(snapshot, "\"capturing\":true", "settle must not send or pause");
    frontend->call<fcitx::ITestFrontend::pushCommitExpectation>("nihao");
    SendKey(frontend, uuid, "Return");
    snapshot = ReadDump(dump_path);
    ExpectMissing(snapshot, "nihao", "ready Return did not send the settled block");
    std::cout << "ok: buffer composing Return settles, next Return sends\n";

    SendKey(frontend, uuid, "Escape");
    snapshot = ReadDump(dump_path);
    ExpectContains(snapshot, "\"visible\":false", "Escape did not hide");
    ExpectContains(snapshot, "\"capturing\":false", "Escape did not pause capture");

    frontend->call<fcitx::ITestFrontend::pushCommitExpectation>("你好");
    Type(frontend, uuid, "nihao");
    SendKey(frontend, uuid, "space");
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

    try {
        const auto dump_path = IsolateDirs();
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
                    try {
                        RunBufferSuite(instance, frontend, uuid, ic, dump_path);
                    } catch (const TestFailed&) {
                        instance.exit();
                    }
                    return true;
                });
        });

        try {
            const int rc = instance.exec();
            return g_exit_status != EXIT_SUCCESS ? g_exit_status : rc;
        } catch (const fcitx::InstanceQuietQuit&) {
            return g_exit_status;
        }
    } catch (const TestFailed&) {
        return EXIT_FAILURE;
    } catch (const std::exception& exception) {
        std::cerr << "FAIL: " << exception.what() << '\n';
        return EXIT_FAILURE;
    }
}
