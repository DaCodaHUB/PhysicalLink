#include "V2Codec.h"
#include "V2Wire.h"
#include <cmath>
#include <cstring>
#include <limits>
namespace physical_link { namespace v2 {
static_assert(sizeof(double) == 8 && std::numeric_limits<double>::is_iec559 &&
              std::numeric_limits<double>::digits == 53, "v2 requires IEEE binary64");
namespace {
Status validate(const Record& record, std::size_t count) {
    if (!wire::countValid(count) || record.channel >= count) return Status::InvalidChannel;
    if (!std::isfinite(record.value)) return Status::InvalidRecord;
    if (record.available) {
        if (record.sequence == 0 || record.age == UnavailableAge) return Status::InvalidRecord;
    } else if (record.age != UnavailableAge || record.value != 0.0 || std::signbit(record.value)) {
        return Status::InvalidRecord;
    }
    return Status::Ok;
}
void writeRecord(const Record& r, std::uint8_t* out) {
    out[0] = 0x20; out[1] = r.channel; out[2] = r.available ? 0 : 1; out[3] = 0;
    wire::put(out + 4, r.sequence, 4); wire::put(out + 8, r.age, 4);
    std::uint64_t bits;
    std::memcpy(&bits, &r.value, sizeof(bits)); // Scalar bits only, never a struct.
    wire::put(out + 12, bits, 8);
}
Result bufferCheck(std::uint8_t* output, std::size_t capacity, std::size_t required) {
    if (!output && capacity) return {Status::InvalidBuffer, 0, required};
    if (capacity < required) return {Status::BufferTooSmall, 0, required};
    return {Status::Ok, 0, required};
}
}
Result Codec::encodeRecord(const Record& r, std::size_t count, std::uint8_t* out, std::size_t cap) {
    const auto status = validate(r, count);
    if (status != Status::Ok) return {status, 0, RecordSize};
    const auto result = bufferCheck(out, cap, RecordSize);
    if (result.status != Status::Ok) return result;
    writeRecord(r, out);
    return {Status::Ok, RecordSize, RecordSize};
}
Status Codec::decodeRecord(const std::uint8_t* in, std::size_t len, std::size_t count, Record& out) {
    if (!in) return Status::InvalidBuffer;
    if (len != RecordSize) return Status::InvalidLength;
    if (in[0] != 0x20 || in[2] > 1 || in[3] != 0) return Status::InvalidRecord;
    Record r;
    r.channel = in[1]; r.available = in[2] == 0;
    r.sequence = static_cast<std::uint32_t>(wire::get(in + 4, 4));
    r.age = static_cast<std::uint32_t>(wire::get(in + 8, 4));
    const auto bits = wire::get(in + 12, 8);
    std::memcpy(&r.value, &bits, sizeof(bits));
    const auto status = validate(r, count);
    if (status == Status::Ok) out = r;
    return status;
}
Result Codec::encodeImage(const Image& image, std::uint8_t* out, std::size_t cap) {
    if (!wire::countValid(image.count) || !image.epoch) return {Status::InvalidImage, 0, 0};
    const std::size_t required = ImageHeaderSize + RecordSize * image.count;
    for (std::size_t i = 0; i < image.count; ++i) {
        if (image.records[i].channel != i || validate(image.records[i], image.count) != Status::Ok ||
            (image.requestId == 0 && (image.records[i].available || image.records[i].sequence != 0)))
            return {Status::InvalidImage, 0, required};
    }
    const auto result = bufferCheck(out, cap, required);
    if (result.status != Status::Ok) return result;
    out[0] = 0x21; out[1] = image.count;
    wire::put(out + 2, image.requestId, 2); wire::put(out + 4, image.epoch, 8);
    wire::put(out + 12, 0, 4);
    for (std::size_t i = 0; i < image.count; ++i) writeRecord(image.records[i], out + 16 + i * 20);
    return {Status::Ok, required, required};
}
Status Codec::decodeImage(const std::uint8_t* in, std::size_t len, Image& out) {
    if (!in) return Status::InvalidBuffer;
    if (len < ImageHeaderSize) return Status::InvalidLength;
    if (in[0] != 0x21 || !wire::countValid(in[1]) || wire::get(in + 12, 4)) return Status::InvalidImage;
    if (len != ImageHeaderSize + RecordSize * in[1]) return Status::InvalidLength;
    Image image;
    image.count = in[1]; image.requestId = static_cast<std::uint16_t>(wire::get(in + 2, 2));
    image.epoch = wire::get(in + 4, 8);
    if (!image.epoch) return Status::InvalidImage;
    for (std::size_t i = 0; i < image.count; ++i) {
        if (decodeRecord(in + 16 + i * 20, 20, image.count, image.records[i]) != Status::Ok ||
            image.records[i].channel != i ||
            (image.requestId == 0 && (image.records[i].available || image.records[i].sequence != 0)))
            return Status::InvalidImage;
    }
    out = image;
    return Status::Ok;
}
} }
