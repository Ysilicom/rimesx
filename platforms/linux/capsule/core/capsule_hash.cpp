#include "capsule_hash.hpp"

#include <cstdint>
#include <cstdio>
#include <cstring>
#include <fcntl.h>
#include <iomanip>
#include <sstream>
#include <unistd.h>

#include <chrono>
#include <ctime>

namespace rimes::capsule {
namespace {

constexpr std::uint32_t kK[64] = {
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4,
    0xab1c5ed5, 0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe,
    0x9bdc06a7, 0xc19bf174, 0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f,
    0x4a7484aa, 0x5cb0a9dc, 0x76f988da, 0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7,
    0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967, 0x27b70a85, 0x2e1b2138, 0x4d2c6dfc,
    0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85, 0xa2bfe8a1, 0xa81a664b,
    0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070, 0x19a4c116,
    0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7,
    0xc67178f2,
};

std::uint32_t Rotr(std::uint32_t value, std::uint32_t bits) {
    return (value >> bits) | (value << (32 - bits));
}

void Sha256Transform(std::uint32_t state[8], const unsigned char block[64]) {
    std::uint32_t w[64];
    for (int i = 0; i < 16; ++i) {
        w[i] = (static_cast<std::uint32_t>(block[i * 4]) << 24) |
               (static_cast<std::uint32_t>(block[i * 4 + 1]) << 16) |
               (static_cast<std::uint32_t>(block[i * 4 + 2]) << 8) |
               static_cast<std::uint32_t>(block[i * 4 + 3]);
    }
    for (int i = 16; i < 64; ++i) {
        const std::uint32_t s0 = Rotr(w[i - 15], 7) ^ Rotr(w[i - 15], 18) ^ (w[i - 15] >> 3);
        const std::uint32_t s1 = Rotr(w[i - 2], 17) ^ Rotr(w[i - 2], 19) ^ (w[i - 2] >> 10);
        w[i] = w[i - 16] + s0 + w[i - 7] + s1;
    }
    std::uint32_t a = state[0], b = state[1], c = state[2], d = state[3];
    std::uint32_t e = state[4], f = state[5], g = state[6], h = state[7];
    for (int i = 0; i < 64; ++i) {
        const std::uint32_t S1 = Rotr(e, 6) ^ Rotr(e, 11) ^ Rotr(e, 25);
        const std::uint32_t ch = (e & f) ^ ((~e) & g);
        const std::uint32_t temp1 = h + S1 + ch + kK[i] + w[i];
        const std::uint32_t S0 = Rotr(a, 2) ^ Rotr(a, 13) ^ Rotr(a, 22);
        const std::uint32_t maj = (a & b) ^ (a & c) ^ (b & c);
        const std::uint32_t temp2 = S0 + maj;
        h = g;
        g = f;
        f = e;
        e = d + temp1;
        d = c;
        c = b;
        b = a;
        a = temp1 + temp2;
    }
    state[0] += a;
    state[1] += b;
    state[2] += c;
    state[3] += d;
    state[4] += e;
    state[5] += f;
    state[6] += g;
    state[7] += h;
}

}  // namespace

std::string Sha256Hex(std::string_view data) {
    std::uint32_t state[8] = {0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
                              0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19};
    unsigned char block[64];
    std::uint64_t bit_len = static_cast<std::uint64_t>(data.size()) * 8;
    std::size_t offset = 0;
    while (offset + 64 <= data.size()) {
        Sha256Transform(state, reinterpret_cast<const unsigned char*>(data.data() + offset));
        offset += 64;
    }
    std::size_t remain = data.size() - offset;
    std::memcpy(block, data.data() + offset, remain);
    block[remain++] = 0x80;
    if (remain > 56) {
        std::memset(block + remain, 0, 64 - remain);
        Sha256Transform(state, block);
        remain = 0;
    }
    std::memset(block + remain, 0, 56 - remain);
    for (int i = 0; i < 8; ++i) {
        block[63 - i] = static_cast<unsigned char>((bit_len >> (8 * i)) & 0xff);
    }
    Sha256Transform(state, block);
    std::ostringstream out;
    out << std::hex << std::setfill('0');
    for (int i = 0; i < 8; ++i) {
        out << std::setw(8) << state[i];
    }
    return out.str();
}

std::string NewUuidV4() {
    unsigned char bytes[16];
    int fd = open("/dev/urandom", O_RDONLY | O_CLOEXEC);
    if (fd < 0 || read(fd, bytes, sizeof(bytes)) != static_cast<ssize_t>(sizeof(bytes))) {
        if (fd >= 0) {
            close(fd);
        }
        const auto now = std::chrono::steady_clock::now().time_since_epoch().count();
        for (int i = 0; i < 16; ++i) {
            bytes[i] = static_cast<unsigned char>((now >> (i * 4)) & 0xff);
        }
    } else {
        close(fd);
    }
    bytes[6] = static_cast<unsigned char>((bytes[6] & 0x0f) | 0x40);
    bytes[8] = static_cast<unsigned char>((bytes[8] & 0x3f) | 0x80);
    char text[37];
    std::snprintf(text, sizeof(text),
                  "%02x%02x%02x%02x-%02x%02x-%02x%02x-%02x%02x-%02x%02x%02x%02x%02x%02x",
                  bytes[0], bytes[1], bytes[2], bytes[3], bytes[4], bytes[5], bytes[6], bytes[7],
                  bytes[8], bytes[9], bytes[10], bytes[11], bytes[12], bytes[13], bytes[14],
                  bytes[15]);
    return text;
}

std::string Iso8601UtcNow() {
    const auto now = std::chrono::system_clock::now();
    const auto seconds = std::chrono::time_point_cast<std::chrono::seconds>(now);
    const auto millis =
        std::chrono::duration_cast<std::chrono::milliseconds>(now - seconds).count();
    const std::time_t time = std::chrono::system_clock::to_time_t(seconds);
    std::tm utc{};
    gmtime_r(&time, &utc);
    char buf[80];
    std::snprintf(buf, sizeof(buf), "%04d-%02d-%02dT%02d:%02d:%02d.%03dZ", utc.tm_year + 1900,
                  utc.tm_mon + 1, utc.tm_mday, utc.tm_hour, utc.tm_min, utc.tm_sec,
                  static_cast<int>(millis));
    return buf;
}

bool LooksLikeUuid(std::string_view text) {
    if (text.size() != 36) {
        return false;
    }
    for (std::size_t i = 0; i < text.size(); ++i) {
        const char ch = text[i];
        if (i == 8 || i == 13 || i == 18 || i == 23) {
            if (ch != '-') {
                return false;
            }
            continue;
        }
        const bool hex = (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f') ||
                         (ch >= 'A' && ch <= 'F');
        if (!hex) {
            return false;
        }
    }
    return true;
}

}  // namespace rimes::capsule
