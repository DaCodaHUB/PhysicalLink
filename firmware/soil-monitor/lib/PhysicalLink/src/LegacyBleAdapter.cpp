#include "LegacyBleAdapter.h"
#include <cmath>
#include <cstring>

namespace physical_link {
constexpr std::size_t LegacyBleAdapter::AttributeLimit;
namespace {
struct Binding { const char* id; const char* uuid; const char* unit; };
const Binding bindings[] = {
    {"soil", "5678", "raw"}, {"temperature", "5679", "C"},
    {"humidity", "567a", "%"}, {"pressure", "567b", "hPa"}
};
const Binding* bindingFor(const char* id) {
    if (id) for (const auto& binding : bindings) {
        if (std::strcmp(id, binding.id) == 0) return &binding;
    }
    return nullptr;
}
} // namespace

const char* LegacyBleAdapter::uuidFor(const char* id) {
    const auto* binding = bindingFor(id);
    return binding ? binding->uuid : nullptr;
}

LegacyBleAdapter::Result LegacyBleAdapter::serialize(
    const SensorRegistry& registry, char* output, std::size_t capacity) {
    // Validate every entry before emitting anything; never silently filter.
    unsigned int seen = 0;
    for (std::size_t i = 0; i < registry.size(); ++i) {
        const auto& sensor = *registry.getSensorAt(i);
        const auto* binding = bindingFor(sensor.id);
        if (!binding) return {Status::UnsupportedId, 0, 0};
        const unsigned int bit = 1u << (binding - bindings);
        if (seen & bit) return {Status::InvalidConfiguration, 0, 0};
        seen |= bit;
        if (std::strcmp(sensor.unit, binding->unit) != 0)
            return {Status::IncompatibleUnit, 0, 0};
    }
    if (seen != 15u) return {Status::InvalidConfiguration, 0, 0};

    // All strings below are validated fixed-profile strings, requiring no
    // JSON escaping. Display names are absent from the original v1 schema.
    std::string json = "{\"version\":1,\"device\":\"";
    json += deviceName();
    json += "\",\"capabilities\":[";
    for (std::size_t i = 0; i < registry.size(); ++i) {
        const auto& sensor = *registry.getSensorAt(i);
        if (i) json += ',';
        json += "{\"id\":\"";
        json += sensor.id;
        json += "\",\"uuid\":\"";
        json += uuidFor(sensor.id);
        json += "\",\"unit\":\"";
        json += sensor.unit;
        json += "\",\"type\":\"number\"}";
    }
    json += "]}";
    const auto required = json.size() + 1;
    if (!metadataFits(json.size())) return {Status::MetadataTooLarge, 0, required};
    if (!output && capacity) return {Status::InvalidBuffer, 0, required};
    if (capacity < required) return {Status::BufferTooSmall, 0, required};
    std::memcpy(output, json.c_str(), required);
    return {Status::Ok, json.size(), required};
}

LegacyBleAdapter::Status LegacyBleAdapter::formatReading(
    const SensorRegistry& registry, const char* id, std::string& output) {
    output.clear();
    const auto* binding = bindingFor(id);
    if (!binding) return Status::UnsupportedId;
    const auto* sensor = registry.getSensor(id);
    if (!sensor) return Status::InvalidConfiguration;
    if (std::strcmp(sensor->unit, binding->unit) != 0) return Status::IncompatibleUnit;
    if (!sensor->available) return Status::Unavailable;
    if (!std::isfinite(sensor->value)) return Status::InvalidReading;
    if (std::strcmp(id, "soil") == 0) {
        // Preserve the original integer ADC text, without unsafe narrowing.
        if (sensor->value < 0 || sensor->value > 4095 ||
            std::floor(sensor->value) != sensor->value) return Status::InvalidReading;
        output = std::to_string(static_cast<int>(sensor->value));
    } else {
        output = std::to_string(sensor->value); // Original six decimal places.
    }
    return Status::Ok;
}
} // namespace physical_link
