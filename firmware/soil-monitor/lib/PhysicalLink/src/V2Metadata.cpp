#include "V2Metadata.h"
#include "V2Wire.h"
#include <cstring>
namespace physical_link { namespace v2 {
namespace {
bool uuidValid(const char* uuid) {
    if (!uuid) return false;
    for (std::size_t i = 0; i < 36; ++i) {
        const char c = uuid[i];
        if (!c) return false;
        if (i == 8 || i == 13 || i == 18 || i == 23) { if (c != '-') return false; }
        else if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) return false;
    }
    return uuid[36] == 0;
}
// Same compact escaping rules as CapabilitySerializer, without changing its
// existing internal schema. Null destination measures the exact escaped size.
class Writer {
    char* output_;
    std::size_t size_ = 0;
public:
    explicit Writer(char* output) : output_(output) {}
    void byte(char c) { if (output_) output_[size_] = c; ++size_; }
    void text(const char* s) { while (*s) byte(*s++); }
    void string(const char* s) {
        const char* hex = "0123456789abcdef";
        byte('"');
        for (; *s; ++s) {
            const auto c = static_cast<unsigned char>(*s);
            if (c == '"' || c == '\\') { byte('\\'); byte(static_cast<char>(c)); }
            else if (c < 32) { text("\\u00"); byte(hex[c >> 4]); byte(hex[c & 15]); }
            else byte(static_cast<char>(c));
        }
        byte('"');
    }
    std::size_t size() const { return size_; }
};
void encode(const SensorRegistry& registry, const char* uuid, const char* device,
            std::uint64_t epoch, Writer& writer) {
    writer.text("{\"version\":2,\"deviceId\":"); writer.string(uuid);
    writer.text(",\"device\":"); writer.string(device);
    writer.text(",\"epoch\":\"");
    const char* hex = "0123456789abcdef";
    for (int shift = 60; shift >= 0; shift -= 4) writer.byte(hex[(epoch >> shift) & 15]);
    writer.text("\",\"sensors\":[");
    for (std::size_t i = 0; i < registry.size(); ++i) {
        const auto& sensor = *registry.getSensorAt(i);
        if (i) writer.byte(',');
        writer.text("{\"id\":"); writer.string(sensor.id);
        writer.text(",\"name\":"); writer.string(sensor.name);
        writer.text(",\"unit\":"); writer.string(sensor.unit); writer.byte('}');
    }
    writer.text("]}");
}
}
Result Metadata::build(const SensorRegistry& registry, const char* uuid,
                       const char* device, std::uint64_t epoch) {
    if (!wire::countValid(registry.size()) || !uuidValid(uuid) || !epoch || !device || !device[0])
        return {Status::InvalidMetadata, 0, 0};
    // Reuse the registry's bounded UTF-8/name validator, rather than diverging.
    SensorRegistry validation;
    if (validation.addSensor("device", device, "") != SensorRegistry::Result::Ok)
        return {Status::InvalidMetadata, 0, 0};
    Writer counter(nullptr);
    encode(registry, uuid, device, epoch, counter);
    const auto required = counter.size() + 1;
    if (counter.size() > MaxMetadataSize) return {Status::MetadataTooLarge, 0, required};
    Writer writer(json_.data());
    encode(registry, uuid, device, epoch, writer);
    json_[writer.size()] = 0; size_ = writer.size(); epoch_ = epoch;
    return {Status::Ok, size_, required};
}
Result Metadata::page(std::size_t index, std::uint8_t* output, std::size_t capacity) const {
    if (!size_) return {Status::NotStarted, 0, 0};
    const auto count = (size_ + PagePayloadSize - 1) / PagePayloadSize;
    if (index >= count) return {Status::InvalidPage, 0, 0};
    const auto offset = index * PagePayloadSize;
    const auto payload = size_ - offset > PagePayloadSize ? PagePayloadSize : size_ - offset;
    const auto required = PageHeaderSize + payload;
    if (!output && capacity) return {Status::InvalidBuffer, 0, required};
    if (capacity < required) return {Status::BufferTooSmall, 0, required};
    output[0] = 'P'; output[1] = 'L'; output[2] = 2;
    output[3] = static_cast<std::uint8_t>(index); output[4] = static_cast<std::uint8_t>(count); output[5] = 0;
    wire::put(output + 6, size_, 2); wire::put(output + 8, epoch_, 8);
    std::memcpy(output + PageHeaderSize, json_.data() + offset, payload);
    return {Status::Ok, required, required};
}
Status Metadata::decodePage(const std::uint8_t* input, std::size_t length, PageView& output) {
    if (!input) return Status::InvalidBuffer;
    if (length <= PageHeaderSize || length > MaxPageSize) return Status::InvalidLength;
    if (input[0] != 'P' || input[1] != 'L' || input[2] != 2 || input[5]) return Status::InvalidPage;
    PageView view;
    view.index = input[3]; view.count = input[4];
    view.totalBytes = static_cast<std::uint16_t>(wire::get(input + 6, 2));
    view.epoch = wire::get(input + 8, 8);
    if (!view.epoch || !view.totalBytes || view.totalBytes > MaxMetadataSize ||
        view.count != (view.totalBytes + PagePayloadSize - 1) / PagePayloadSize || view.index >= view.count)
        return Status::InvalidPage;
    const auto remaining = view.totalBytes - view.index * PagePayloadSize;
    view.payloadSize = remaining > PagePayloadSize ? PagePayloadSize : remaining;
    if (length != PageHeaderSize + view.payloadSize) return Status::InvalidLength;
    view.payload = input + PageHeaderSize;
    output = view;
    return Status::Ok;
}
} }
