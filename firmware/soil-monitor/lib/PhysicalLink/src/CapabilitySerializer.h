#ifndef PHYSICAL_LINK_CAPABILITY_SERIALIZER_H
#define PHYSICAL_LINK_CAPABILITY_SERIALIZER_H

#include "SensorRegistry.h"

namespace physical_link {

// Internal capability representation, NOT a BLE v1/v2 wire contract.
class CapabilitySerializer {
public:
    static constexpr unsigned int SchemaVersion = 1;
    // Worst-case complete document, including its terminating NUL. IDs need no
    // escaping; each name/unit byte may expand to six bytes (\u00xx).
    static constexpr std::size_t MaxBufferSize =
        sizeof("{\"schemaVersion\":1,\"sensors\":[]}") +
        SensorRegistry::MaxSensors *
            ((sizeof("{\"id\":\"\",\"name\":\"\",\"unit\":\"\",\"type\":\"number\"}") - 1) +
             SensorRegistry::MaxIdLength +
             6 * (SensorRegistry::MaxNameLength + SensorRegistry::MaxUnitLength)) +
        (SensorRegistry::MaxSensors - 1);

    enum class Status { Ok, BufferTooSmall, InvalidBuffer };
    struct Result {
        Status status;
        std::size_t bytesWritten;     // JSON bytes, excluding NUL; zero on failure
        std::size_t requiredCapacity; // Exact capacity including NUL, on all paths
    };

    // output must designate capacity writable bytes, disjoint from registry
    // storage. No allocation or ownership transfer. Two passes; registry must
    // not be mutated concurrently. Failure leaves ALL output bytes unchanged.
    // nullptr with zero capacity is a size query (BufferTooSmall). nullptr with
    // nonzero capacity is InvalidBuffer. Success always writes a final NUL.
    static Result serialize(const SensorRegistry& registry,
                            char* output, std::size_t capacity);
};

} // namespace physical_link

#endif
