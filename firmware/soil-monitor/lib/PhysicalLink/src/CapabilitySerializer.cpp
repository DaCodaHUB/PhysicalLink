#include "CapabilitySerializer.h"

namespace physical_link {

constexpr unsigned int CapabilitySerializer::SchemaVersion;
constexpr std::size_t CapabilitySerializer::MaxBufferSize;

namespace {

// A null destination runs the identical encoding path in count-only mode.
// Registry bounds limit the count well below even a 16-bit size_t maximum.
class Writer {
public:
    explicit Writer(char* output) : output_(output) {}

    void byte(char value) {
        if (output_ != nullptr) {
            output_[size_] = value;
        }
        ++size_;
    }

    void literal(const char* text) {
        while (*text != '\0') {
            byte(*text++);
        }
    }

    void string(const char* text) {
        static constexpr char hex[] = "0123456789abcdef";
        byte('"');
        for (; *text != '\0'; ++text) {
            const auto value = static_cast<unsigned char>(*text);
            if (value == '"' || value == '\\') {
                byte('\\');
                byte(static_cast<char>(value));
            } else if (value < 0x20) {
                // Includes tab, CR, LF, backspace and formfeed. C-string NUL
                // ends metadata at registration and cannot occur internally.
                literal("\\u00");
                byte(hex[value >> 4]);
                byte(hex[value & 0x0f]);
            } else {
                byte(static_cast<char>(value)); // Preserve validated UTF-8.
            }
        }
        byte('"');
    }

    std::size_t size() const { return size_; }

private:
    char* output_;
    std::size_t size_ = 0;
};

void encode(const SensorRegistry& registry, Writer& writer) {
    static_assert(CapabilitySerializer::SchemaVersion == 1,
                  "Update the schema literal and size bound when version changes");
    writer.literal("{\"schemaVersion\":1,\"sensors\":[");
    for (std::size_t i = 0; i < registry.size(); ++i) {
        const auto& sensor = *registry.getSensorAt(i);
        if (i != 0) {
            writer.byte(',');
        }
        writer.literal("{\"id\":");
        writer.string(sensor.id);
        writer.literal(",\"name\":");
        writer.string(sensor.name);
        writer.literal(",\"unit\":");
        writer.string(sensor.unit);
        // SensorRegistry currently accepts only numeric channels.
        writer.literal(",\"type\":\"number\"}");
    }
    writer.literal("]}");
}

} // namespace

CapabilitySerializer::Result CapabilitySerializer::serialize(
    const SensorRegistry& registry, char* output, std::size_t capacity) {
    Writer counter(nullptr);
    encode(registry, counter);
    const std::size_t required = counter.size() + 1;
    if (output == nullptr && capacity != 0) {
        return {Status::InvalidBuffer, 0, required};
    }
    if (capacity < required) {
        return {Status::BufferTooSmall, 0, required};
    }

    Writer writer(output);
    encode(registry, writer);
    output[writer.size()] = '\0';
    return {Status::Ok, writer.size(), required};
}

} // namespace physical_link
