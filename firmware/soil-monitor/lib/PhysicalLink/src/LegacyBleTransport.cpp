#include "LegacyBleTransport.h"
#include "LegacyBleAdapter.h"
#include <NimBLEDevice.h>
#include <new>
namespace physical_link {
namespace {
class Esp32LegacyTransport final : public LegacyBleTransport {
    NimBLEServer* server_ = nullptr;
    NimBLECharacteristic* characteristics_[4] = {};
    bool ownsStack_ = false;
public:
    PLResult start(const SensorRegistry& sensors, const char* metadata) override {
        if (NimBLEDevice::isInitialized()) return PLResult::Busy;
        ownsStack_ = true;
        if (!NimBLEDevice::init(LegacyBleAdapter::deviceName())) return PLResult::BleStartFailed;
        server_ = NimBLEDevice::createServer();
        if (!server_) return PLResult::BleStartFailed;
        // Use NimBLE's owned default callbacks and built-in disconnect restart.
        // No application callback pointer can outlive its owner.
        server_->advertiseOnDisconnect(true);
        auto* service = server_->createService("1234");
        if (!service) return PLResult::BleStartFailed;
        for (std::size_t i = 0; i < 4; ++i) {
            characteristics_[i] = service->createCharacteristic(
                LegacyBleAdapter::uuidFor(sensors.getSensorAt(i)->id),
                NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY);
            if (!characteristics_[i]) return PLResult::BleStartFailed;
            characteristics_[i]->setValue("");
        }
        auto* capabilities = service->createCharacteristic("5670", NIMBLE_PROPERTY::READ,
                                                           LegacyBleAdapter::AttributeLimit);
        if (!capabilities) return PLResult::BleStartFailed;
        capabilities->setValue(metadata);
        if (capabilities->getValue() != std::string(metadata)) return PLResult::BleStartFailed;
        if (!server_->start()) return PLResult::BleStartFailed;
        auto* advertising = NimBLEDevice::getAdvertising();
        if (!advertising || !advertising->addServiceUUID("1234") ||
            !advertising->setName(LegacyBleAdapter::deviceName())) return PLResult::BleStartFailed;
        advertising->enableScanResponse(true);
        return advertising->start() ? PLResult::Ok : PLResult::BleStartFailed;
    }
    bool publish(std::size_t index, const std::string& text, bool notify) override {
        characteristics_[index]->setValue(text);
        // No subscribers is normal: sampling/reads must continue offline.
        if (notify)
            return characteristics_[index]->notify();
        return true;
    }
    bool stop() override {
        if (!ownsStack_) return true;
        if (server_) server_->advertiseOnDisconnect(false);
        NimBLEDevice::stopAdvertising();
        // First stop the host, THEN free services/callbacks. deinit(true) alone
        // frees them even if nimble_port_stop fails in the installed NimBLE.
        if (!NimBLEDevice::deinit(false)) return false;
        if (!NimBLEDevice::deinit(true)) return false;
        ownsStack_ = false;
        server_ = nullptr;
        return true;
    }
};
}
LegacyBleTransport* createLegacyBleTransport() {
    return new (std::nothrow) Esp32LegacyTransport;
}
}
