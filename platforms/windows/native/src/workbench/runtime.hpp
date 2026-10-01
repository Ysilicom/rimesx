#pragma once
#include <condition_variable>
#include <deque>
#include <functional>
#include <map>
#include <mutex>
#include <thread>

#include "../core/control.hpp"
#include "../engine/rime_snapshot.hpp"
#include "model.hpp"
#include "provider.hpp"
namespace rimes::windows::workbench {
class Runtime {
 public:
  Runtime();
  ~Runtime();
  Target Register(std::uint32_t process, std::uint64_t session,
                  std::uint64_t context);
  void Remove(Target target);
  void Focus(Target target);
  bool Capturing(Target target);
  bool BeforeKey(Target target, const core::KeyEvent& key, bool composing);
  void Capture(Target target, engine::EngineSnapshot* snapshot);
  core::Json Control(const core::Json& message, std::uint32_t process);
  core::Json Snapshot();
  Settings Configuration();
  bool Configure(Settings value, const std::wstring& key, bool replace_key,
                 std::string* error);
  void Toggle();
  void Close();
  void Protect();
  void Paste(std::string text);
  void Send(bool all);
  void Generate(bool translation);
  void Cancel();
  void Tick();
  void Stop();
  void SetNotify(std::function<void()> notify);
  bool Stopping();

 private:
  struct Entry {
    Target target;
    std::deque<core::Json> events;
  };
  std::mutex mutex_;
  std::condition_variable event_, api_event_;
  std::map<std::uint64_t, Entry> sessions_;
  Model model_;
  Settings settings_;
  std::function<void()> notify_;
  bool stopping_ = false, settings_valid_ = true;
  std::jthread api_worker_;
  std::optional<std::pair<Settings, Generation>> api_job_;
  std::uint64_t pressed_at_ = 0, pending_since_ = 0, edited_at_ = 0;
  Target return_target_;
  bool return_held_ = false, return_sent_ = false;
  void Changed();
  void Queue(const std::optional<Delivery>& delivery);
  void CaptureChanged();
  void StartGeneration(bool translation);
  void RunAPI();
};
}  // namespace rimes::windows::workbench
