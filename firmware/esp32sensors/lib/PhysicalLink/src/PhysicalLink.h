#ifndef PHYSICAL_LINK_H
#define PHYSICAL_LINK_H

// One owning application task for all calls. ESP32 Arduino; no sensor drivers required.
enum class PLResult {
    Ok, NotRunning, AlreadyRunning, InvalidProjectName, InvalidId,
    InvalidName, InvalidUnit, DuplicateId, CapacityExceeded, UnknownSensor,
    InvalidValue, InvalidMetadata, MetadataTooLarge, OutOfMemory,
    Busy, BleStartFailed, BleStopFailed, InvalidTime, SequenceExhausted
};
class PhysicalLink {
public:
    // Copies a nonempty UTF-8 project name (max 64 bytes); no BLE side effects.
    explicit PhysicalLink(const char* projectName);
    ~PhysicalLink();
    PhysicalLink(const PhysicalLink&) = delete;
    PhysicalLink& operator=(const PhysicalLink&) = delete;
    PhysicalLink(PhysicalLink&&) = delete;
    PhysicalLink& operator=(PhysicalLink&&) = delete;
    // Copies metadata. Register 1..16 numeric channels while stopped.
    // Keep IDs stable across boots/firmware updates to preserve dashboard bindings.
    PLResult addSensor(const char* id, const char* name, const char* unit);
    // Uses the factory eFuse board UUID and a fresh random session epoch.
    // Every begin starts all channels unavailable; registration survives end().
    PLResult begin();
    // Repeated samples coalesce. Nonfinite values mark a known channel unavailable.
    PLResult update(const char* id, double numericValue);
    PLResult unavailable(const char* id);
    // No sensor acquisition or radio waits. Existing bounded send scheduler applies.
    PLResult poll();
    PLResult end(); // Idempotent; retry if BLE host shutdown fails.
    bool isRunning() const;
private:
    struct Impl;
    Impl* impl_;
};
#endif
