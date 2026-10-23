#include "V2SensorState.h"
#include <cmath>
#include <cstring>
#include <limits>
namespace physical_link { namespace v2 {
Status SensorState::addSensor(const char* id, const char* name, const char* unit) {
    if (started_) return Status::RegistrationClosed;
    switch (sensors_.addSensor(id, name, unit)) {
    case SensorRegistry::Result::Ok: return Status::Ok;
    case SensorRegistry::Result::InvalidId: return Status::InvalidId;
    case SensorRegistry::Result::InvalidName: return Status::InvalidName;
    case SensorRegistry::Result::InvalidUnit: return Status::InvalidUnit;
    case SensorRegistry::Result::DuplicateId: return Status::DuplicateId;
    case SensorRegistry::Result::CapacityExceeded: return Status::CapacityExceeded;
    default: return Status::InvalidConfiguration;
    }
}
Status SensorState::start(std::uint64_t epoch, std::uint32_t now) {
    if (!epoch || !sensors_.size() || !limit_ || (started_ && epoch == epoch_)) return Status::InvalidConfiguration;
    epoch_ = epoch; previous_ = now; monotonic_ = 0;
    timing_ = {}; exhausted_ = false; started_ = true;
    for (std::size_t i = 0; i < sensors_.size(); ++i) sensors_.unavailable(sensors_.getSensorAt(i)->id);
    return Status::Ok;
}
Status SensorState::tick(std::uint32_t now) {
    if (!started_) return Status::NotStarted;
    if (exhausted_) return Status::SequenceExhausted;
    const std::uint32_t elapsed = now - previous_;
    // Observe millis at least every 2^31-1 ms. Backward/ambiguous input fails.
    if (elapsed >= 0x80000000u || monotonic_ > std::numeric_limits<std::uint64_t>::max() - elapsed)
        return Status::InvalidTime;
    monotonic_ += elapsed; previous_ = now;
    return Status::Ok;
}
Status SensorState::change(const char* id, double value, bool available, std::uint32_t now) {
    if (!started_) return Status::NotStarted;
    const auto* sensor = sensors_.getSensor(id);
    if (!sensor) return Status::UnknownSensor;
    const auto clock = tick(now);
    if (clock != Status::Ok) return clock;
    std::size_t channel = 0;
    while (sensors_.getSensorAt(channel) != sensor) ++channel;
    auto& timing = timing_[channel];
    if (timing.sequence == limit_) {
        exhausted_ = true; // Fail closed for ALL readings/images until new epoch.
        return Status::SequenceExhausted;
    }
    ++timing.sequence; timing.acquired = monotonic_;
    const bool invalid = available && !std::isfinite(value);
    if (available && !invalid) sensors_.update(id, value);
    else sensors_.unavailable(id);
    return invalid ? Status::InvalidValue : Status::Ok;
}
Status SensorState::update(const char* id, double value, std::uint32_t now) { return change(id, value, true, now); }
Status SensorState::unavailable(const char* id, std::uint32_t now) { return change(id, 0, false, now); }
Record SensorState::recordAt(std::size_t channel) const {
    const auto& sensor = *sensors_.getSensorAt(channel);
    Record record;
    record.channel = static_cast<std::uint8_t>(channel);
    record.available = sensor.available; record.sequence = timing_[channel].sequence;
    if (record.available) {
        const auto age = monotonic_ - timing_[channel].acquired;
        record.age = age > MaxAge ? MaxAge : static_cast<std::uint32_t>(age);
        record.value = sensor.value;
    }
    return record;
}
Status SensorState::reading(std::size_t channel, std::uint32_t now, Record& output) {
    if (channel >= sensors_.size()) return Status::InvalidChannel;
    const auto status = tick(now);
    if (status != Status::Ok) return status;
    output = recordAt(channel);
    return Status::Ok;
}
Status SensorState::capture(std::uint16_t request, std::uint32_t now, Image& output) {
    const auto status = tick(now);
    if (status != Status::Ok) return status;
    Image image;
    image.epoch = epoch_; image.count = static_cast<std::uint8_t>(sensors_.size()); image.requestId = request;
    for (std::size_t i = 0; i < sensors_.size(); ++i) {
        image.records[i] = recordAt(i);
        if (request == 0 && image.records[i].sequence != 0) return Status::InvalidImage;
    }
    output = image;
    return Status::Ok;
}
} }
