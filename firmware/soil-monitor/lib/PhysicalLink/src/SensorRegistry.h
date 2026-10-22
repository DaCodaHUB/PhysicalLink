#ifndef PHYSICAL_LINK_SENSOR_REGISTRY_H
#define PHYSICAL_LINK_SENSOR_REGISTRY_H

#include <array>
#include <cstddef>

namespace physical_link {

class SensorRegistry {
public:
    static constexpr std::size_t MaxSensors = 16;
    static constexpr std::size_t MaxIdLength = 32;
    static constexpr std::size_t MaxNameLength = 64;
    static constexpr std::size_t MaxUnitLength = 16;

    enum class Result {
        Ok,
        InvalidId,
        InvalidName,
        InvalidUnit,
        DuplicateId,
        CapacityExceeded,
        UnknownSensor,
        InvalidValue
    };

    struct Sensor {
        char id[MaxIdLength + 1] = {};
        char name[MaxNameLength + 1] = {};
        char unit[MaxUnitLength + 1] = {};
        double value = 0.0;
        bool available = false;
    };

    SensorRegistry() = default;
    // Keep borrowed Sensor pointers tied to one stable owner.
    SensorRegistry(const SensorRegistry&) = delete;
    SensorRegistry& operator=(const SensorRegistry&) = delete;
    SensorRegistry(SensorRegistry&&) = delete;
    SensorRegistry& operator=(SensorRegistry&&) = delete;

    // ID: [a-z][a-z0-9_.-]{0,31}. Name/unit: bounded UTF-8.
    // A null or empty name falls back to ID; a null unit means no unit.
    // All accepted strings are copied, never retained by pointer.
    Result addSensor(const char* id, const char* name, const char* unit);
    Result update(const char* id, double value);
    // Retains the last finite value, but it is not a current valid reading.
    Result unavailable(const char* id);
    // Null for malformed/unknown IDs. Read-only live view, valid until owner
    // destruction; later updates change the viewed reading. Not thread-safe.
    const Sensor* getSensor(const char* id) const;
    // Read-only registration-order access; null when index >= size().
    // Same ownership and synchronization rules as getSensor().
    const Sensor* getSensorAt(std::size_t index) const;
    std::size_t size() const { return size_; }

private:
    static bool validId(const char* id);
    std::size_t findIndex(const char* id) const;

    std::array<Sensor, MaxSensors> sensors_{};
    std::size_t size_ = 0;
};

} // namespace physical_link

#endif
