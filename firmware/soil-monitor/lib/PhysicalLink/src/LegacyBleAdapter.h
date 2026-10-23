#ifndef PHYSICAL_LINK_LEGACY_BLE_ADAPTER_H
#define PHYSICAL_LINK_LEGACY_BLE_ADAPTER_H

#include "SensorRegistry.h"
#include <string>

namespace physical_link {

// Fixed Android v1 profile. No BLE stack dependency or general UUID allocation.
class LegacyBleAdapter {
public:
    static constexpr std::size_t AttributeLimit = 512;
    static const char* deviceName() { return "PhysicalLink Soil"; }
    enum class Status {
        Ok, InvalidConfiguration, UnsupportedId, IncompatibleUnit,
        MetadataTooLarge, BufferTooSmall, InvalidBuffer, Unavailable,
        InvalidReading
    };
    struct Result {
        Status status;
        std::size_t bytesWritten;
        std::size_t requiredCapacity; // Includes NUL; zero for invalid config.
    };
    static const char* uuidFor(const char* id);
    static bool metadataFits(std::size_t bytes) { return bytes <= AttributeLimit; }
    // Requires exactly the four legacy channels, in any registration order,
    // with their original units. Registry enforces unique IDs at insertion.
    // All failures leave output unchanged. Buffer must not overlap registry.
    static Result serialize(const SensorRegistry& registry, char* output,
                            std::size_t capacity);
    // Clears output on failure; never formats an unavailable retained value.
    static Status formatReading(const SensorRegistry& registry, const char* id,
                                std::string& output);
};

} // namespace physical_link
#endif
