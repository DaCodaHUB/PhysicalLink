#ifndef PHYSICAL_LINK_PROTOCOL_METADATA_H
#define PHYSICAL_LINK_PROTOCOL_METADATA_H
#include "SensorRegistry.h"
#include "ProtocolCodec.h"
namespace physical_link { namespace protocol {
constexpr std::size_t MaxMetadataSize = 8192;
constexpr std::size_t PageHeaderSize = 16;
constexpr std::size_t PagePayloadSize = 496;
constexpr std::size_t MaxPageSize = 512;
struct PageView {
    std::uint8_t index = 0, count = 0;
    std::uint16_t totalBytes = 0;
    std::uint64_t epoch = 0;
    const std::uint8_t* payload = nullptr; // Borrowed from decode input.
    std::size_t payloadSize = 0;
};
// Owns immutable manifest bytes until next successful build. No allocation.
// One owner task; disjoint buffers required. Failures preserve existing output.
class Metadata {
public:
    Result build(const SensorRegistry&, const char* deviceId,
                 const char* deviceName, std::uint64_t epoch);
    const char* data() const { return json_.data(); }
    std::size_t size() const { return size_; }
    Result page(std::size_t index, std::uint8_t* output, std::size_t capacity) const;
    static Status decodePage(const std::uint8_t*, std::size_t length, PageView&);
private:
    std::array<char, MaxMetadataSize + 1> json_{};
    std::size_t size_ = 0;
    std::uint64_t epoch_ = 0;
};
} }
#endif
