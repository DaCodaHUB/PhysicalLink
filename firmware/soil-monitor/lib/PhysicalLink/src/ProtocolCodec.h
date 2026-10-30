#ifndef PHYSICAL_LINK_PROTOCOL_CODEC_H
#define PHYSICAL_LINK_PROTOCOL_CODEC_H
#include <array>
#include <cstddef>
#include <cstdint>
namespace physical_link { namespace protocol {
constexpr std::size_t MaxSensors = 16;
constexpr std::size_t RecordSize = 20;
constexpr std::size_t ImageHeaderSize = 16;
constexpr std::size_t MaxImageSize = 336;
constexpr std::uint32_t UnavailableAge = 0xffffffffu;
constexpr std::uint32_t MaxAge = 0xfffffffeu;
enum class Status {
    Ok, InvalidBuffer, BufferTooSmall, InvalidLength, InvalidChannel,
    InvalidRecord, InvalidImage, InvalidMetadata, MetadataTooLarge,
    InvalidPage, NotStarted, RegistrationClosed, UnknownSensor, InvalidValue,
    InvalidTime, SequenceExhausted, InvalidConfiguration,
    InvalidId, InvalidName, InvalidUnit, DuplicateId, CapacityExceeded
};
struct Result {
    Status status;
    std::size_t bytesWritten;
    std::size_t requiredCapacity; // Binary excludes NUL; metadata build includes it.
};
struct Record {
    std::uint8_t channel = 0;
    bool available = false;
    std::uint32_t sequence = 0;
    std::uint32_t age = UnavailableAge;
    double value = 0.0;
};
struct Image {
    std::uint8_t count = 0;
    std::uint16_t requestId = 0;
    std::uint64_t epoch = 0;
    std::array<Record, MaxSensors> records{};
};
// No allocation. Buffers must not overlap input objects. Every failure leaves
// output unchanged. Decode output objects are committed only after validation.
class Codec {
public:
    static Result encodeRecord(const Record&, std::size_t sensorCount,
                               std::uint8_t* output, std::size_t capacity);
    static Status decodeRecord(const std::uint8_t*, std::size_t length,
                               std::size_t sensorCount, Record& output);
    static Result encodeImage(const Image&, std::uint8_t*, std::size_t capacity);
    static Status decodeImage(const std::uint8_t*, std::size_t length, Image& output);
};
} }
#endif
