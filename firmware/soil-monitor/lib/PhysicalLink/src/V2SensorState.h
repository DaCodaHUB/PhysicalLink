#ifndef PHYSICAL_LINK_V2_SENSOR_STATE_H
#define PHYSICAL_LINK_V2_SENSOR_STATE_H
#include "SensorRegistry.h"
#include "V2Codec.h"
namespace physical_link { namespace v2 {
// Independent owner: never mutates a legacy registry. One application task;
// no concurrent calls/ISR access. Registration freezes after successful start.
class SensorState {
public:
    // A smaller sequence budget is useful for bounded sessions and exhaustion
    // tests. Production default is UINT32_MAX. Zero is rejected by start().
    explicit SensorState(std::uint32_t sequenceLimit = 0xffffffffu) : limit_(sequenceLimit) {}
    Status addSensor(const char* id, const char* name, const char* unit);
    Status start(std::uint64_t epoch, std::uint32_t nowMs);
    Status tick(std::uint32_t nowMs);
    Status update(const char* id, double value, std::uint32_t nowMs);
    Status unavailable(const char* id, std::uint32_t nowMs);
    Status reading(std::size_t channel, std::uint32_t nowMs, Record& output);
    Status capture(std::uint16_t requestId, std::uint32_t nowMs, Image& output);
    const SensorRegistry& registry() const { return sensors_; }
    bool exhausted() const { return exhausted_; }
private:
    struct Timing { std::uint32_t sequence = 0; std::uint64_t acquired = 0; };
    SensorRegistry sensors_;
    std::array<Timing, MaxSensors> timing_{};
    std::uint32_t limit_, previous_ = 0;
    std::uint64_t epoch_ = 0, monotonic_ = 0;
    bool started_ = false, exhausted_ = false;
    Status change(const char*, double, bool available, std::uint32_t);
    Record recordAt(std::size_t) const;
};
} }
#endif
