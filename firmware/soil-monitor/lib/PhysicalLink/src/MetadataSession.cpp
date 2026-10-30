#include "MetadataSession.h"
#include <cstring>
#include <new>
namespace physical_link { namespace protocol {
Status MetadataSession::prepare(const SensorRegistry& sensors, const char* id,
                                const char* name, std::uint64_t epoch, std::uint32_t now) {
    if (connected_ || ready_) return Status::InvalidConfiguration;
    const auto result = metadata_.build(sensors, id, name, epoch);
    if (result.status != Status::Ok) return result.status;
    // Registry deliberately forbids copying; reconstruct in owned storage only
    // during prepare, with the host stopped. No heap allocation is involved.
    state_.~SensorState();
    new (&state_) SensorState;
    for (std::size_t i = 0; i < sensors.size(); ++i) {
        const auto& sensor = *sensors.getSensorAt(i);
        const auto added = state_.addSensor(sensor.id, sensor.name, sensor.unit);
        if (added != Status::Ok) return added;
    }
    const auto started = state_.start(epoch, now);
    if (started != Status::Ok) return started;
    Image initial;
    if (state_.capture(0, now, initial) != Status::Ok) return Status::InvalidImage;
    const auto encoded = Codec::encodeImage(initial, initialImage_.data(), MaxImageSize);
    if (encoded.status != Status::Ok) return encoded.status;
    imageLength_ = encoded.bytesWritten;
    attempted_ = false;
    const auto page = metadata_.page(0, pages_[0].data(), MaxPageSize);
    if (page.status != Status::Ok) return page.status;
    lengths_[0] = page.bytesWritten; published_ = 0; ready_ = true;
    return Status::Ok;
}
bool MetadataSession::connect(std::uint16_t handle) {
    if (!ready_ || connected_) return false;
    const auto page = metadata_.page(0, pages_[0].data(), MaxPageSize);
    if (page.status != Status::Ok) return false;
    lengths_[0] = page.bytesWritten; published_ = 0;
    handle_ = handle; connected_ = true; hasPin_ = false;
    pending_ = false; awaitingRead_ = false; fault_ = false;
    images_[0] = initialImage_; imagePublished_ = 0; imageHasPin_ = false;
    subscribed_ = false; lastRequest_ = 0; requestedCapture_ = 0; opcode_ = 0;
    sentKnown_.fill(false); nextChannel_ = 0;
    return true;
}
void MetadataSession::disconnect(std::uint16_t handle) {
    if (!connected_ || handle_ != handle) return;
    connected_ = false; hasPin_ = false; pending_ = false; awaitingRead_ = false; fault_ = false;
    subscribed_ = false; imageHasPin_ = false; lastRequest_ = 0; opcode_ = 0;
    sentKnown_.fill(false);
}
MetadataSession::Access MetadataSession::command(std::uint16_t handle,
                                                const std::uint8_t* data, std::size_t length) {
    if (!connected_ || handle != handle_) return Access::WrongConnection;
    if (fault_) return Access::InvalidCommand;
    if (pending_ || awaitingRead_) { fault_ = true; return Access::Busy; }
    if (!data || !length) { fault_ = true; return Access::InvalidCommand; }
    if (data[0] == 1 && length == 2 && data[1] < pages_[published_][4] && data[1] != selectedPage()) {
        requested_ = data[1];
    } else if (data[0] == 2 && length == 3 && subscribed_) {
        const auto request = static_cast<std::uint16_t>(data[1] | (std::uint16_t(data[2]) << 8));
        if (!request || (!lastRequest_ && request != 1) || request <= lastRequest_) {
            fault_ = true; return Access::InvalidCommand;
        }
        requestedCapture_ = request;
    } else { fault_ = true; return Access::InvalidCommand; }
    opcode_ = data[0]; pending_ = true;
    return Access::Ok;
}
bool MetadataSession::poll(std::uint32_t now) {
    if (ready_ && state_.tick(now) != Status::Ok) fault_ = true;
    if (!connected_ || fault_ || !pending_) return false;
    if (opcode_ == 2) {
        if (!subscribed_) { fault_ = true; return false; }
        const unsigned int next = 1u - imagePublished_;
        if (imageHasPin_ && imagePinned_ == next) { fault_ = true; return false; }
        Image image;
        if (state_.capture(requestedCapture_, now, image) != Status::Ok ||
            Codec::encodeImage(image, images_[next].data(), MaxImageSize).status != Status::Ok) {
            fault_ = true; return false;
        }
        imagePublished_ = next; lastRequest_ = requestedCapture_;
        pending_ = false; awaitingRead_ = true;
        return true;
    }
    // The single-command/header-observation rule ensures the unused slot is
    // not pinned. Never overwrite it if a broken caller violates that invariant.
    const unsigned int next = 1u - published_;
    if (hasPin_ && pinned_ == next) { fault_ = true; return false; }
    const auto result = metadata_.page(requested_, pages_[next].data(), MaxPageSize);
    if (result.status != Status::Ok) { fault_ = true; return false; }
    lengths_[next] = result.bytesWritten; published_ = next;
    pending_ = false; awaitingRead_ = true;
    return true;
}
MetadataSession::Access MetadataSession::read(std::uint16_t handle, std::size_t offset,
                                             std::uint8_t* output, std::size_t capacity,
                                             std::size_t& length) {
    if (!ready_) return Access::NotReady;
    if (!connected_ || handle != handle_) return Access::WrongConnection;
    const auto slot = offset == 0 ? published_ : pinned_;
    if ((offset && !hasPin_) || offset > lengths_[slot]) return Access::InvalidOffset;
    if (!output || capacity < lengths_[slot]) return Access::BufferTooSmall;
    if (offset == 0) {
        pinned_ = published_; hasPin_ = true;
        if (awaitingRead_ && opcode_ == 1) awaitingRead_ = false;
    }
    std::memcpy(output, pages_[slot].data(), lengths_[slot]);
    length = lengths_[slot];
    return Access::Ok;
}
void MetadataSession::reset() {
    ready_ = false; connected_ = false; hasPin_ = false;
    pending_ = false; awaitingRead_ = false; fault_ = false;
    subscribed_ = false; imageHasPin_ = false; sentKnown_.fill(false);
}
Status MetadataSession::update(const char* id, double value, bool available, std::uint32_t now) {
    if (!ready_) return Status::NotStarted;
    const auto result = available ? state_.update(id, value, now) : state_.unavailable(id, now);
    if (result == Status::SequenceExhausted || result == Status::InvalidTime) fault_ = true;
    return result;
}
void MetadataSession::subscribe(std::uint16_t handle, bool enabled) {
    if (!connected_ || handle != handle_) return;
    if (enabled && !subscribed_) { sentKnown_.fill(false); nextChannel_ = 0; }
    subscribed_ = enabled;
}
MetadataSession::Access MetadataSession::readState(std::uint16_t handle, std::size_t offset,
    std::uint8_t* output, std::size_t capacity, std::size_t& length) {
    if (!ready_) return Access::NotReady;
    if (!connected_ || handle != handle_) return Access::WrongConnection;
    const auto slot = offset == 0 ? imagePublished_ : imagePinned_;
    if ((offset && !imageHasPin_) || offset > imageLength_) return Access::InvalidOffset;
    if (!output || capacity < imageLength_) return Access::BufferTooSmall;
    if (offset == 0) {
        imagePinned_ = imagePublished_; imageHasPin_ = true;
        if (awaitingRead_ && opcode_ == 2) awaitingRead_ = false;
    }
    std::memcpy(output, images_[slot].data(), imageLength_); length = imageLength_;
    return Access::Ok;
}
bool MetadataSession::notification(std::uint32_t now, Notification& output) {
    if (!connected_ || !subscribed_ || fault_ || (attempted_ && std::uint32_t(now - lastAttempt_) < 50))
        return false;
    const auto count = state_.registry().size();
    for (std::size_t i = 0; i < count; ++i) {
        const auto channel = (nextChannel_ + i) % count;
        Record record;
        if (state_.reading(channel, now, record) != Status::Ok) { fault_ = true; return false; }
        if (sentKnown_[channel] && sentSequences_[channel] == record.sequence) continue;
        Notification next;
        if (Codec::encodeRecord(record, count, next.bytes.data(), next.bytes.size()).status != Status::Ok) {
            fault_ = true; return false;
        }
        next.handle = handle_; next.channel = record.channel; next.sequence = record.sequence;
        output = next; nextChannel_ = (channel + 1) % count;
        lastAttempt_ = now; attempted_ = true;
        return true;
    }
    return false;
}
void MetadataSession::sent(const Notification& notification) {
    if (!connected_ || !subscribed_ || handle_ != notification.handle) return;
    sentSequences_[notification.channel] = notification.sequence;
    sentKnown_[notification.channel] = true;
}
} }
