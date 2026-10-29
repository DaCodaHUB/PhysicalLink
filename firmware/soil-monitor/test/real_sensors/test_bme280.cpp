#include "Bme280.h"
#include <cstdio>
#include <cstdlib>
TestWire Wire;
void check(bool value) { if (!value) { std::fprintf(stderr, "BME280 check failed\n"); std::exit(1); } }
void put16(int reg, int value) { Wire.regs[reg] = uint8_t(value); Wire.regs[reg+1] = uint8_t(value >> 8); }
void put20(int reg, uint32_t value) {
    Wire.regs[reg] = uint8_t(value >> 12); Wire.regs[reg+1] = uint8_t(value >> 4);
    Wire.regs[reg+2] = uint8_t(value << 4);
}
int main() {
    Bme280 sensor;
    check(!sensor.begin()); // Absent/wrong chip.
    Wire.regs[0xd0] = 0x60;
    check(!sensor.begin()); // Invalid calibration.
    // Bosch datasheet temperature/pressure reference vector.
    const int calibration[] = {27504,26435,-1000,36477,-10685,3024,2855,140,-7,15500,-14600,6000};
    for (int i = 0; i < 12; ++i) put16(0x88 + i*2, calibration[i]);
    // Independent humidity oracle: H1/H3..H6=0, H2=1024 => RH=raw/64.
    put16(0xe1, 1024);
    check(sensor.begin());
    put20(0xf7, 415148); put20(0xfa, 519888); Wire.regs[0xfd] = 0x0c; Wire.regs[0xfe] = 0x80;
    double t = 0, h = 0, p = 0;
    check(sensor.sample(t, h, p));
    check(std::abs(t - 25.08248) < 0.0001 && std::abs(p - 1006.5327) < 0.001);
    check(std::abs(h - 50.0) < 0.001);
    Wire.fail = true; check(!sensor.sample(t, h, p)); check(!sensor.begin());
    Wire.fail = false; Wire.shortRead = true; check(!sensor.sample(t, h, p)); check(!sensor.begin());
    Wire.shortRead = false; check(sensor.sample(t, h, p)); // Recovery after failed transfer.
    Wire.regs[0xf4] = 0; check(!sensor.sample(t, h, p)); // Sensor reset/sleep.
    Wire.regs[0xf4] = 0x27;
    put20(0xfa, 0x80000); check(!sensor.sample(t, h, p));
    put20(0xfa, 519888); put20(0xf7, 0x80000); check(!sensor.sample(t, h, p));
    put20(0xf7, 415148); Wire.regs[0xfd] = 0x80; Wire.regs[0xfe] = 0;
    check(!sensor.sample(t, h, p));
}
