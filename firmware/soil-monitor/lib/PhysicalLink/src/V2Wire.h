#ifndef PHYSICAL_LINK_V2_WIRE_H
#define PHYSICAL_LINK_V2_WIRE_H
#include <cstddef>
#include <cstdint>
namespace physical_link { namespace v2 { namespace wire {
inline void put(std::uint8_t* out, std::uint64_t value, std::size_t bytes) {
    for (std::size_t i = 0; i < bytes; ++i) out[i] = static_cast<std::uint8_t>(value >> (i * 8));
}
inline std::uint64_t get(const std::uint8_t* in, std::size_t bytes) {
    std::uint64_t value = 0;
    for (std::size_t i = 0; i < bytes; ++i) value |= std::uint64_t(in[i]) << (i * 8);
    return value;
}
inline bool countValid(std::size_t count) { return count >= 1 && count <= 16; }
} } }
#endif
