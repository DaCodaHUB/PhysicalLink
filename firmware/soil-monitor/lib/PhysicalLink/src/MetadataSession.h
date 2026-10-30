#ifndef PHYSICAL_LINK_PROTOCOL_METADATA_SESSION_H
#define PHYSICAL_LINK_PROTOCOL_METADATA_SESSION_H
#include "Metadata.h"
#include "SensorState.h"
namespace physical_link { namespace protocol {
// All calls serialized by the caller (backend uses a bounded critical section).
// Owns manifest, sensor cache, two page generations and two state generations;
// no borrowed registry/buffers. Reconnect resets protocol state, not acquisitions.
class MetadataSession {
public:
    enum class Access { Ok, NotReady, WrongConnection, InvalidCommand, Busy, InvalidOffset, BufferTooSmall };
    Status prepare(const SensorRegistry&, const char* deviceId, const char* name, std::uint64_t epoch,
                   std::uint32_t nowMs = 0);
    bool connect(std::uint16_t handle);
    void disconnect(std::uint16_t handle);
    Access command(std::uint16_t handle, const std::uint8_t*, std::size_t length);
    bool poll(std::uint32_t nowMs = 0); // Applies at most one copied command.
    Status update(const char* id, double value, bool available, std::uint32_t nowMs);
    void subscribe(std::uint16_t handle, bool enabled);
    Access readState(std::uint16_t handle, std::size_t offset, std::uint8_t* output,
                     std::size_t capacity, std::size_t& length);
    struct Notification {
        std::array<std::uint8_t, RecordSize> bytes{};
        std::uint16_t handle = 0;
        std::uint8_t channel = 0;
        std::uint32_t sequence = 0;
    };
    // One attempt per 50 ms, no catch-up bursts. Failed sends consume budget.
    bool notification(std::uint32_t nowMs, Notification&);
    void sent(const Notification&);
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
    SensorState state_;
    std::array<std::array<std::uint8_t, MaxImageSize>, 2> images_{};
    std::size_t imageLength_ = 0;
    unsigned int imagePublished_ = 0, imagePinned_ = 0;
    bool imageHasPin_ = false, subscribed_ = false;
    std::uint16_t lastRequest_ = 0, requestedCapture_ = 0;
    std::uint8_t opcode_ = 0;
    std::array<std::uint32_t, MaxSensors> sentSequences_{};
    std::array<bool, MaxSensors> sentKnown_{};
    std::size_t nextChannel_ = 0;
    std::uint32_t lastAttempt_ = 0;
    bool attempted_ = false;
    std::array<std::uint8_t, MaxImageSize> initialImage_{};
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
