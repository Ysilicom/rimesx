#include "buffer_ids.hpp"

#include <chrono>
#include <iomanip>
#include <random>
#include <sstream>

namespace rimes::buffer {
namespace {

std::mt19937_64& Generator() {
    static thread_local std::mt19937_64 generator([] {
        std::random_device device;
        std::seed_seq seed{device(), device(), device(), device()};
        return std::mt19937_64(seed);
    }());
    return generator;
}

}  // namespace

std::string NewBlockId() {
    std::uniform_int_distribution<std::uint64_t> dist;
    const auto high = dist(Generator());
    const auto low = dist(Generator());
    std::ostringstream out;
    out << std::hex << std::nouppercase << std::setfill('0') << std::setw(16) << high
        << std::setw(16) << low;
    return out.str();
}

std::int64_t UnixTimeMs() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
               std::chrono::system_clock::now().time_since_epoch())
        .count();
}

}  // namespace rimes::buffer
