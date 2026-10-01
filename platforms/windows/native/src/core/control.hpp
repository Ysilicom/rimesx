#pragma once
#include <optional>
#include <stdexcept>

#include "../../third_party/nlohmann/json.hpp"
#include "broker_protocol.hpp"
namespace rimes::windows::core {
using Json = nlohmann::json;
inline std::vector<std::byte> EncodeControl(const Json& value) {
  auto text = value.dump();
  if (text.size() > kMaxPayloadSize) throw std::length_error("control limit");
  const auto* begin = reinterpret_cast<const std::byte*>(text.data());
  return {begin, begin + text.size()};
}
inline std::optional<Json> DecodeControl(std::span<const std::byte> bytes) {
  if (bytes.size() > kMaxPayloadSize) return {};
  try {
    auto value =
        Json::parse(reinterpret_cast<const char*>(bytes.data()),
                    reinterpret_cast<const char*>(bytes.data() + bytes.size()),
                    [](int depth, Json::parse_event_t, Json&) {
                      if (depth > 16) throw std::length_error("depth");
                      return true;
                    });
    if (value.is_object()) return value;
  } catch (...) {
  }
  return {};
}
}  // namespace rimes::windows::core
