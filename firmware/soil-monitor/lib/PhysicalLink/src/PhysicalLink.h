#ifndef PHYSICAL_LINK_H
#define PHYSICAL_LINK_H

// ESP32 Arduino, legacy BLE v1 only. All calls belong to one application task.
enum class PLResult {
    Ok, NotRunning, AlreadyRunning, InvalidProjectName, InvalidId,
    InvalidName, InvalidUnit, DuplicateId, CapacityExceeded, UnknownSensor,
    InvalidValue, UnsupportedProfile, MetadataTooLarge, OutOfMemory,
    Busy, BleStartFailed, BleStopFailed, SendFailed
};

class PhysicalLink {
public:
    // Copies a nonempty UTF-8 project name (max 64 bytes). No BLE side effects.
    // v1 advertising/metadata always use "PhysicalLink Soil" for compatibility.
    explicit PhysicalLink(const char* projectName);
    ~PhysicalLink();
    PhysicalLink(const PhysicalLink&) = delete;
    PhysicalLink& operator=(const PhysicalLink&) = delete;
    PhysicalLink(PhysicalLink&&) = delete;
    PhysicalLink& operator=(PhysicalLink&&) = delete;

    // Copies registration metadata. Only allowed while stopped.
    PLResult addSensor(const char* id, const char* name, const char* unit);
    // Requires exactly soil/raw, temperature/C, humidity/%, pressure/hPa.
    PLResult begin();
    // Updates coalesce until poll(). InvalidValue marks a known sensor
    // unavailable, ensuring a failed acquisition cannot republish old data.
    PLResult update(const char* id, double numericValue);
    PLResult unavailable(const char* id);
    // Publishes at most four pending values. No delay or acquisition performed.
    PLResult poll();
    PLResult end(); // Idempotent. Releases BLE; retains registration.
    bool isRunning() const;
private:
    struct Impl;
    Impl* impl_;
};
#endif
