#ifndef PHYSICAL_LINK_V2_METADATA_SESSION_H
#define PHYSICAL_LINK_V2_METADATA_SESSION_H
#include "V2Metadata.h"
namespace physical_link { namespace v2 {
// All calls serialized by the caller (backend uses a bounded critical section).
// Owns its manifest and two page generations; no borrowed registry/buffers.
class MetadataSession {
public:
    enum class Access { Ok, NotReady, WrongConnection, InvalidCommand, Busy, InvalidOffset, BufferTooSmall };
    Status prepare(const SensorRegistry&, const char* deviceId, const char* name, std::uint64_t epoch);
    bool connect(std::uint16_t handle);
    void disconnect(std::uint16_t handle);
    Access command(std::uint16_t handle, const std::uint8_t*, std::size_t length);
    bool poll(); // Applies at most one copied page-select command.
    // Returns the FULL pinned attribute, not a pre-sliced suffix. NimBLE's
    // ble_gatts_val_access performs offset slicing AFTER the access callback.
    Access read(std::uint16_t handle, std::size_t offset, std::uint8_t* output,
                std::size_t capacity, std::size_t& length);
    bool faulted() const { return fault_; }
    bool connected() const { return connected_; }
    std::uint16_t handle() const { return handle_; }
    std::size_t selectedPage() const { return pages_[published_][3]; }
    void reset(); // Host must be stopped before lifetime/storage can end.
private:
    Metadata metadata_;
    std::array<std::array<std::uint8_t, MaxPageSize>, 2> pages_{};
    std::array<std::size_t, 2> lengths_{};
    std::uint16_t handle_ = 0;
    unsigned int published_ = 0, pinned_ = 0;
    std::uint8_t requested_ = 0;
    bool ready_ = false, connected_ = false, hasPin_ = false;
    bool pending_ = false, awaitingRead_ = false, fault_ = false;
};
} }
#endif
