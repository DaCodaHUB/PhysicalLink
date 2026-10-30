#include "DeviceIdentity.h"
#ifdef ARDUINO_ARCH_ESP32
#include <Arduino.h>
#include <esp_system.h>
#include <cstdio>
namespace physical_link {
void boardDeviceId(char (&output)[37]) {
    formatBoardDeviceId(ESP.getEfuseMac(), output);
}
std::uint64_t freshEpoch() {
    std::uint64_t epoch;
    do { epoch = (std::uint64_t(esp_random()) << 32) | esp_random(); } while (!epoch);
    return epoch;
}
}
#endif
