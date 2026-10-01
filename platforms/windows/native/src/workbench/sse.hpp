#pragma once
#include <functional>
#include <string>
#include <string_view>

#include "../core/control.hpp"
namespace rimes::windows::workbench {
// Incremental SSE decoding is independent of transport packet boundaries,
// including boundaries inside UTF-8 and CRLF. No payload is logged.
class SseDecoder {
 public:
  explicit SseDecoder(std::function<bool(const std::string&)> chunk)
      : chunk_(std::move(chunk)) {}
  bool Feed(std::string_view bytes) {
    if (done_ || failed_) return false;
    total_ += bytes.size();
    if (total_ > 2 * 1024 * 1024) return Fail();
    pending_.append(bytes);
    try {
      for (;;) {
        auto end = pending_.find('\n');
        if (end == std::string::npos) break;
        auto line = pending_.substr(0, end);
        pending_.erase(0, end + 1);
        if (!line.empty() && line.back() == '\r') line.pop_back();
        if (line.starts_with("data:")) {
          if (!event_.empty()) event_ += '\n';
          event_ += line.substr(line.size() > 5 && line[5] == ' ' ? 6 : 5);
        } else if (line.empty() && !event_.empty()) {
          if (event_ == "[DONE]") {
            done_ = true;
            return true;
          }
          const auto data = core::Json::parse(event_);
          event_.clear();
          if (data.contains("error")) return Fail();
          if (data.contains("choices") && !data["choices"].empty()) {
            const auto& delta = data.at("choices").at(0).at("delta");
            if (delta.contains("content") && delta["content"].is_string() &&
                !chunk_(delta["content"].get<std::string>()))
              return Fail();
          }
        }
        if (event_.size() > 256 * 1024) return Fail();
      }
      if (pending_.size() + event_.size() > 256 * 1024) return Fail();
      return true;
    } catch (...) {
      return Fail();
    }
  }
  bool Done() const { return done_ && !failed_; }

 private:
  bool Fail() {
    failed_ = true;
    return false;
  }
  std::function<bool(const std::string&)> chunk_;
  std::string pending_, event_;
  std::size_t total_ = 0;
  bool done_ = false, failed_ = false;
};
}  // namespace rimes::windows::workbench
