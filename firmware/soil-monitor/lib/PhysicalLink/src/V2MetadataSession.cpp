#include "V2MetadataSession.h"
#include <cstring>
namespace physical_link { namespace v2 {
Status MetadataSession::prepare(const SensorRegistry& sensors, const char* id,
                                const char* name, std::uint64_t epoch) {
    if (connected_ || ready_) return Status::InvalidConfiguration;
    const auto result = metadata_.build(sensors, id, name, epoch);
    if (result.status != Status::Ok) return result.status;
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
    return true;
}
void MetadataSession::disconnect(std::uint16_t handle) {
    if (!connected_ || handle_ != handle) return;
    connected_ = false; hasPin_ = false; pending_ = false; awaitingRead_ = false; fault_ = false;
}
MetadataSession::Access MetadataSession::command(std::uint16_t handle,
                                                const std::uint8_t* data, std::size_t length) {
    if (!connected_ || handle != handle_) return Access::WrongConnection;
    if (fault_) return Access::InvalidCommand;
    if (pending_ || awaitingRead_) { fault_ = true; return Access::Busy; }
    if (!data || length != 2 || data[0] != 1 || data[1] >= pages_[published_][4] ||
        data[1] == selectedPage()) { fault_ = true; return Access::InvalidCommand; }
    requested_ = data[1]; pending_ = true;
    return Access::Ok;
}
bool MetadataSession::poll() {
    if (!connected_ || fault_ || !pending_) return false;
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
        if (awaitingRead_) awaitingRead_ = false;
    }
    std::memcpy(output, pages_[slot].data(), lengths_[slot]);
    length = lengths_[slot];
    return Access::Ok;
}
void MetadataSession::reset() {
    ready_ = false; connected_ = false; hasPin_ = false;
    pending_ = false; awaitingRead_ = false; fault_ = false;
}
} }
