#pragma once

#include <cstddef>
#include <string>
#include <string_view>

namespace rimes::capsule {

std::string Sha256Hex(std::string_view data);
std::string NewUuidV4();
std::string Iso8601UtcNow();
bool LooksLikeUuid(std::string_view text);

}  // namespace rimes::capsule
