#pragma once

#include <cstdint>
#include <string>

namespace rimes::buffer {

// Random v4-style hex id. Not a wire UUID parser; identity only.
std::string NewBlockId();

std::int64_t UnixTimeMs();

}  // namespace rimes::buffer
