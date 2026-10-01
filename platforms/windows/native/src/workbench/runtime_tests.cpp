#include <cstdlib>
#include <iostream>

#include "runtime.hpp"
using namespace rimes::windows;
void Check(bool ok, const char* reason) {
  if (!ok) {
    std::cerr << reason << '\n';
    std::exit(1);
  }
}
int main() {
  workbench::Runtime runtime;
  const auto peer = GetCurrentProcessId() + 1;
  auto target = runtime.Register(peer, 1, 1);
  runtime.Focus(target);
  runtime.Toggle();
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
  runtime.Close();
  Check(runtime.Snapshot()["source"] == "Second.", "close retains content");
  runtime.Protect();
  Check(!runtime.Snapshot()["visible"].get<bool>(),
        "locked session hides Buffer");
  runtime.Stop();
  std::cout << "Runtime target/control/Return tests passed\n";
}
