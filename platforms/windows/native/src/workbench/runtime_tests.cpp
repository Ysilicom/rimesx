#include <cstdlib>
#include <iostream>
#include <atomic>
#include <filesystem>

#include "runtime.hpp"
#include "official_features.hpp"
using namespace rimes::windows;
void Check(bool ok, const char* reason) {
  if (!ok) {
    std::cerr << reason << '\n';
    std::exit(1);
  }
}
void TestTargets() {
  workbench::Runtime runtime;
  const auto peer = GetCurrentProcessId() + 1;
  auto target = runtime.Register(peer, 1, 1);
  runtime.Focus(target);
  runtime.Toggle(peer);
  Check(runtime.Capturing(target), "explicit binding captures input");
  engine::EngineSnapshot text;
  text.handled = true;
  text.commit_text = "First. Second.";
  runtime.Capture(target, &text);
  Check(text.commit_text.empty(), "captured input does not leak into host");
  runtime.Send(false);
  auto event = runtime.Control({{"op", "wait"}, {"session", 1}}, peer);
  if (event.value("kind", "") == "capture")
    event = runtime.Control({{"op", "wait"}, {"session", 1}}, peer);
  Check(event.value("kind", "") == "deliver",
        "separate notification carries delivery");
  Check(runtime.Snapshot()["source"] == "First. Second.",
        "sending does not consume");
  auto foreign = runtime.Control({{"op", "ack"},
                                  {"session", 1},
                                  {"request", event["request"]},
                                  {"accepted", true}},
                                 peer + 1);
  Check(foreign["kind"] == "disconnected",
        "foreign process cannot acknowledge");
  runtime.Control({{"op", "ack"},
                   {"session", 1},
                   {"request", event["request"]},
                   {"accepted", true}},
                  peer);
  Check(runtime.Snapshot()["source"] == "Second.",
        "actual host acceptance consumes only first block");
  runtime.Control({{"op", "ack"},
                   {"session", 1},
                   {"request", event["request"]},
                   {"accepted", true}},
                  peer);
  Check(runtime.Snapshot()["source"] == "Second.",
        "duplicate ack does not consume again");
  core::KeyEvent key;
  key.virtual_key = VK_RETURN;
  key.event_flags = static_cast<unsigned>(core::KeyEventFlags::kKeyDown);
  Check(runtime.BeforeKey(target, key, false), "Return down owned");
  key.event_flags = static_cast<unsigned>(core::KeyEventFlags::kKeyDown) |
                    static_cast<unsigned>(core::KeyEventFlags::kRepeat);
  Check(runtime.BeforeKey(target, key, false), "Return repeat owned");
  key.event_flags = 0;
  Check(runtime.BeforeKey(target, key, false), "Return up owned");
  auto other = runtime.Register(peer, 2, 2);
  runtime.Focus(other);
  Check(!runtime.Capturing(target) && !runtime.Capturing(other),
        "focus change pauses without automatic retarget");
  Check(runtime.Snapshot()["target_pid"] == 0,
        "paused snapshot never advertises the saved target as bound");
  runtime.Bind(peer + 2);
  Check(!runtime.Capturing(other), "background target cannot bind from a tray");
  runtime.Focus(other);
  runtime.Bind(peer);
  Check(runtime.Capturing(other), "source click explicitly binds the new field");
  runtime.Bind(peer);
  Check(runtime.Capturing(other) && runtime.Snapshot()["visible"] == true,
        "repeated source clicks keep capture open instead of toggling closed");
  runtime.Remove(other);
  runtime.Bind(peer);
  Check(!runtime.Snapshot()["capture"].get<bool>() &&
            runtime.Snapshot()["target_pid"] == 0,
        "removed context cannot be revived by a click");
  auto own = runtime.Register(GetCurrentProcessId(), 3, 3);
  runtime.Focus(own);
  runtime.Bind(GetCurrentProcessId());
  Check(!runtime.Capturing(own), "settings window never becomes a Buffer target");
  runtime.Close();
  Check(runtime.Snapshot()["source"] == "Second.", "close retains content");
  runtime.Protect();
  Check(!runtime.Snapshot()["visible"].get<bool>(),
        "locked session hides Buffer");
  runtime.Stop();
  std::cout << "Runtime target/control/Return tests passed\n";
}

template <typename Predicate> void Wait(Predicate predicate, const char* reason) {
  const auto start = GetTickCount64();
  while (!predicate() && GetTickCount64() - start < 3000) Sleep(2);
  Check(predicate(), reason);
}
void TestPluginAuthority() {
  std::atomic<unsigned> calls{0}, completed{0};
  std::atomic<bool> hold{false}, release{false}, accepted{false}, correct_instruction{false};
  workbench::Runtime runtime([&](const workbench::Settings&, const workbench::Generation& job,
      const std::function<bool(const std::string&)>& chunk, const std::function<bool()>& cancelled, std::string*) {
    ++calls;
    correct_instruction = job.instruction.find("English") != std::string::npos;
    if (hold) {
      const auto start = GetTickCount64();
      while (!release && GetTickCount64() - start < 2500) Sleep(2);
    }
    accepted = chunk("First. Second.");
    const bool ok = accepted && !cancelled();
    ++completed;
    return ok;
  });
  const auto peer = GetCurrentProcessId() + 1;
  const auto target = runtime.Register(peer, 10, 10);
  runtime.Focus(target); runtime.Bind(peer); runtime.Paste("第一句。第二句。");
  runtime.Generate(false);
  Check(!runtime.Snapshot()["busy"].get<bool>() && calls == 0, "absent optional plugin cannot dispatch a network job");
  runtime.Generate(true);
  Wait([&] { return !runtime.Snapshot()["busy"].get<bool>(); }, "translation completion");
  Check(calls == 1 && correct_instruction && runtime.Snapshot()["result"] == "First. Second.", "verified package supplies runtime instructions");
  std::string error;
  Check(runtime.ManagePlugin(official::kTranslation, "disable", &error), "disable completed plugin");
  Check(runtime.Snapshot()["result"] == "" && runtime.Snapshot()["source"] == "第一句。第二句。", "disable clears old result and retains source");
  Check(runtime.ManagePlugin(official::kTranslation, "enable", &error), "reenable translation");
  hold = true; runtime.Generate(true);
  Wait([&] { return calls == 2; }, "in-flight mock started");
  Check(runtime.ManagePlugin(official::kTranslation, "disable", &error), "disable during generation");
  Check(runtime.ManagePlugin(official::kTranslation, "enable", &error), "re-enable during generation");
  release = true;
  Wait([&] { return completed == 2; }, "late callback completed");
  Check(!accepted && runtime.Snapshot()["result"] == "", "re-enable cannot authorize an old in-flight callback");
  runtime.Stop();
  // Destructor joins the test transport; its callback must reject the stale
  // grant even though the exact same package has already been re-enabled.
}
void TestChordFallback() {
  workbench::Runtime runtime;
  auto settings = runtime.Configuration(); settings.schema = "my_combo";
  std::string error;
  Check(runtime.Configure(settings, L"", false, &error), "save chord selection");
  Check(runtime.Configuration().schema == "my_combo", "enabled chord selected");
  Check(runtime.ManagePlugin(official::kChord, "disable", &error), "disable chord");
  Check(runtime.Configuration().schema == "rime_ice", "disabled chord falls back to full pinyin");
  Check(runtime.ManagePlugin(official::kChord, "enable", &error), "re-enable chord");
  Check(runtime.Configuration().schema == "my_combo", "re-enable preserves the saved choice");
}
int main() {
  const auto root = std::filesystem::temp_directory_path() /
      (L"rimes-runtime-test-" + std::to_wstring(GetCurrentProcessId()) + L"-" + std::to_wstring(GetTickCount64()));
  std::filesystem::create_directories(root);
  Check(SetEnvironmentVariableW(L"LOCALAPPDATA", root.c_str()) != 0, "isolated runtime preferences");
  TestTargets();
  TestPluginAuthority();
  TestChordFallback();
  std::filesystem::remove_all(root);
  std::cout << "Runtime plugin authority and chord fallback tests passed\n";
}
