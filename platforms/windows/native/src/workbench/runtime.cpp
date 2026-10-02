#include "runtime.hpp"

#include <algorithm>
#include <chrono>
namespace rimes::windows::workbench {
using core::Json;
Runtime::Runtime() {
  std::string error;
  settings_valid_ = LoadSettings(&settings_, &error);
  if (!settings_valid_) model_.status = error;
  api_worker_ = std::jthread([this] { RunAPI(); });
}
Runtime::~Runtime() {
  Stop();
  if (api_worker_.joinable()) api_worker_.join();
}
void Runtime::Changed() {
  if (notify_) notify_();
}
void Runtime::SetNotify(std::function<void()> notify) {
  std::lock_guard lock(mutex_);
  notify_ = std::move(notify);
}
bool Runtime::Stopping() {
  std::lock_guard lock(mutex_);
  return stopping_;
}
Target Runtime::Register(std::uint32_t process, std::uint64_t session,
                         std::uint64_t context) {
  std::lock_guard lock(mutex_);
  Target target{process, session, context};
  sessions_.emplace(session, Entry{target, {}});
  return target;
}
void Runtime::Remove(Target target) {
  std::lock_guard lock(mutex_);
  model_.Revoke(target);
  sessions_.erase(target.session);
  CaptureChanged();
  event_.notify_all();
  Changed();
}
void Runtime::Focus(Target target) {
  std::lock_guard lock(mutex_);
  if (target.process == GetCurrentProcessId()) target = {};
  model_.Focus(target);
  CaptureChanged();
  Changed();
}
bool Runtime::Capturing(Target target) {
  std::lock_guard lock(mutex_);
  return model_.capture && model_.bound == target && model_.live == target;
}
void Runtime::CaptureChanged() {
  for (auto& [id, entry] : sessions_) {
    (void)id;
    // Replace obsolete capture notifications but never replace a delivery.
    std::erase_if(entry.events, [](const Json& e) {
      return e.value("kind", "") == "capture";
    });
    entry.events.push_back(
        {{"kind", "capture"},
         {"context", entry.target.context},
         {"font", settings_.font_size},
         {"theme", settings_.theme},
         {"enabled", model_.capture && model_.bound == entry.target &&
                         model_.live == entry.target}});
  }
  event_.notify_all();
}
void Runtime::Queue(const std::optional<Delivery>& delivery) {
  if (!delivery) return;
  auto found = sessions_.find(delivery->target.session);
  if (found == sessions_.end()) {
    model_.LostAcknowledgement();
    return;
  }
  found->second.events.push_back({{"kind", "deliver"},
                                  {"request", delivery->request},
                                  {"context", delivery->target.context},
                                  {"text", delivery->block.text}});
  pending_since_ = GetTickCount64();
  event_.notify_all();
  Changed();
}
bool Runtime::BeforeKey(Target target, const core::KeyEvent& key,
                        bool composing) {
  std::lock_guard lock(mutex_);
  const bool down = (key.event_flags &
                     static_cast<unsigned>(core::KeyEventFlags::kKeyDown)) != 0;
  if (return_held_ && key.virtual_key == VK_RETURN &&
      return_target_ == target) {
    if (!down) {
      if (!return_sent_ && model_.capture && model_.bound == target)
        Queue(model_.Send(GetTickCount64() - pressed_at_ >= 1200));
      return_held_ = false;
    }
    return true;
  }
  if (!model_.capture || model_.bound != target || model_.live != target)
    return false;
  if (key.virtual_key != VK_ESCAPE && key.virtual_key != VK_RETURN &&
      key.virtual_key != VK_BACK &&
      (model_.SourceText().size() > Model::kLimit - core::kMaxCommitTextBytes ||
       (model_.Pending() && !model_.result.empty()))) {
    model_.status =
        "Buffer is full or a result is being delivered. Input paused.";
    Changed();
    return true;
  }
  if (key.virtual_key == VK_ESCAPE) {
    if (down) {
      model_.Close();
      CaptureChanged();
      Changed();
    }
    return true;
  }
  if (key.virtual_key == VK_BACK && !composing) {
    if (down) {
      model_.Backspace();
      edited_at_ = GetTickCount64();
      Changed();
    }
    return true;
  }
  if (key.virtual_key == VK_RETURN && !composing) {
    if (down && !(key.event_flags &
                  static_cast<unsigned>(core::KeyEventFlags::kRepeat))) {
      return_held_ = true;
      return_sent_ = false;
      pressed_at_ = GetTickCount64();
      return_target_ = target;
    }
    return true;
  }
  return false;
}
void Runtime::Capture(Target target, engine::EngineSnapshot* snapshot) {
  std::lock_guard lock(mutex_);
  if (!model_.capture || model_.bound != target || model_.live != target)
    return;
  model_.preedit = snapshot->composition;
  if (!snapshot->commit_text.empty()) {
    if (!model_.Append(snapshot->commit_text))
      throw std::runtime_error("Buffer capacity invariant");
    snapshot->commit_text.clear();
    edited_at_ = GetTickCount64();
  }
  Changed();
}
Json Runtime::Control(const Json& message, std::uint32_t process) {
  std::unique_lock lock(mutex_);
  const auto id = message.value("session", 0ULL);
  auto found = sessions_.find(id);
  if (found == sessions_.end() || found->second.target.process != process)
    return {{"kind", "disconnected"}};
  const auto target = found->second.target;
  const auto op = message.value("op", "");
  if (op == "focus") {
    model_.Focus(target.process == GetCurrentProcessId() ? Target{} : target);
    CaptureChanged();
    Changed();
    return {{"kind", "ok"}};
  }
  if (op == "ack") {
    Queue(model_.Acknowledge(message.value("request", 0ULL), target,
                             message.value("accepted", false)));
    Changed();
    return {{"kind", "ok"}};
  }
  if (op == "wait") {
    event_.wait_for(lock, std::chrono::seconds(10), [&] {
      auto i = sessions_.find(id);
      return stopping_ || i == sessions_.end() || !i->second.events.empty();
    });
    found = sessions_.find(id);
    if (stopping_ || found == sessions_.end())
      return {{"kind", "disconnected"}};
    if (found->second.events.empty()) return {{"kind", "heartbeat"}};
    Json value = std::move(found->second.events.front());
    found->second.events.pop_front();
    return value;
  }
  return {{"kind", "error"}};
}
Json Runtime::Snapshot() {
  std::lock_guard lock(mutex_);
  Json source = Json::array(), result = Json::array();
  for (const auto& b : model_.source)
    source.push_back({{"id", b.id}, {"text", b.text}});
  for (const auto& b : model_.result)
    result.push_back({{"id", b.id}, {"text", b.text}});
  return {{"source_blocks", source},
          {"result_blocks", result},
          {"visible", model_.visible},
          {"capture", model_.capture},
          {"busy", model_.busy},
          {"uncertain", model_.uncertain},
          {"source", model_.SourceText()},
          {"result", model_.ResultText()},
          {"preview", model_.preview},
          {"preedit", model_.preedit},
          {"status", model_.status},
          {"translate", model_.translate},
          {"target_pid", model_.bound.process}};
}
Settings Runtime::Configuration() {
  std::lock_guard lock(mutex_);
  return settings_;
}
bool Runtime::Configure(Settings value, const std::wstring& key,
                        bool replace_key, std::string* error) {
  std::lock_guard lock(mutex_);
  if (!settings_valid_) {
    if (error)
      *error =
          "Settings file is unreadable. Preserve it and resolve before saving.";
    return false;
  }
  value.revision = settings_.revision + 1;
  if (!SaveSettings(value, error)) return false;
  if (replace_key && !SaveSecret(key, value.base_url, error)) {
    if (!SaveSettings(settings_, nullptr)) {
      settings_valid_ = false;
      model_.Cancel();
    }
    return false;
  }
  settings_ = std::move(value);
  model_.Cancel();
  model_.capture = false;
  CaptureChanged();
  Changed();
  return true;
}
void Runtime::Toggle() {
  std::lock_guard lock(mutex_);
  if (model_.visible && model_.capture)
    model_.Close();
  else
    model_.Open();
  CaptureChanged();
  Changed();
}
void Runtime::Close() {
  std::lock_guard lock(mutex_);
  model_.Close();
  CaptureChanged();
  Changed();
}
void Runtime::Protect() {
  std::lock_guard lock(mutex_);
  model_.Protect();
  CaptureChanged();
  Changed();
}
void Runtime::Paste(std::string text) {
  std::lock_guard lock(mutex_);
  if (model_.visible) {
    if (!model_.Append(std::move(text)))
      model_.status = "Paste exceeds Buffer capacity or delivery is pending.";
    edited_at_ = GetTickCount64();
    Changed();
  }
}
void Runtime::Send(bool all) {
  std::lock_guard lock(mutex_);
  Queue(model_.Send(all));
  Changed();
}
void Runtime::StartGeneration(bool translation, bool complete_sentence_only) {
  if (!settings_valid_ || stopping_ || model_.busy || model_.Pending() ||
      (!translation && !model_.result.empty()))
    return;
  auto job = model_.Generate(settings_.revision, translation,
                             complete_sentence_only);
  if (!model_.busy) return;
  api_job_ = std::make_pair(settings_, std::move(job));
  api_event_.notify_one();
  Changed();
}
void Runtime::Generate(bool translation) {
  std::lock_guard lock(mutex_);
  model_.translate = translation;
  StartGeneration(translation);
}
void Runtime::Cancel() {
  std::lock_guard lock(mutex_);
  model_.Cancel();
  model_.translate = false;
  Changed();
}
void Runtime::Tick() {
  std::lock_guard lock(mutex_);
  const auto now = GetTickCount64();
  if (return_held_ && !return_sent_ && now - pressed_at_ >= 1200) {
    return_sent_ = true;
    if (model_.capture && return_target_ == model_.bound)
      Queue(model_.Send(true));
  }
  if (model_.Pending() && pending_since_ && now - pending_since_ > 10000) {
    model_.LostAcknowledgement();
    pending_since_ = 0;
    CaptureChanged();
    Changed();
  }
  if (model_.visible && model_.translate && !model_.busy &&
      !model_.source.empty() && model_.preedit.empty() &&
      now - edited_at_ >= 800)
    StartGeneration(true, true);
}
void Runtime::Stop() {
  std::lock_guard lock(mutex_);
  stopping_ = true;
  model_.Protect();
  api_event_.notify_all();
  event_.notify_all();
  Changed();
}
void Runtime::RunAPI() {
  for (;;) {
    std::unique_lock lock(mutex_);
    api_event_.wait(lock, [&] { return stopping_ || api_job_.has_value(); });
    if (stopping_) return;
    auto [config, job] = std::move(*api_job_);
    api_job_.reset();
    lock.unlock();
    std::string error;
    const bool ok = GenerateAPI(
        config, job,
        [&](const std::string& text) {
          std::lock_guard l(mutex_);
          const bool accepted = model_.Stream(job, settings_.revision, text);
          Changed();
          return accepted;
        },
        [&] {
          std::lock_guard l(mutex_);
          return stopping_ || !model_.Accepts(job, settings_.revision);
        },
        &error);
    lock.lock();
    if (model_.Accepts(job, settings_.revision)) {
      const bool finished = model_.Finish(job, settings_.revision, ok);
      if (!finished) model_.translate = false;
      if (!ok) {
        model_.status = error;
        model_.translate = false;
      }
      Changed();
    }
  }
}
}  // namespace rimes::windows::workbench
