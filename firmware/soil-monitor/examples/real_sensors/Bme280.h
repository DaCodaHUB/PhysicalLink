#pragma once
#include <Wire.h>
#include <cmath>

// Checked I2C transfers: a failed/short read must never become a numeric sample.
// BME280 datasheet floating-point compensation, normal mode, x1 oversampling.
class Bme280 {
public:
    bool begin() {
        uint8_t id, a[26], h[7];
        if (!read(0xd0, &id, 1) || id != 0x60 ||
            !read(0x88, a, sizeof(a)) || !read(0xe1, h, sizeof(h))) return false;
        t1 = u16(a); t2 = s16(a+2); t3 = s16(a+4);
        p1 = u16(a+6);
        for (int i = 0; i < 8; ++i) p[i] = s16(a+8+2*i);
        h1 = a[25]; h2 = s16(h); h3 = h[2];
        h4 = signed12((uint16_t(h[3]) << 4) | (h[4] & 15));
        h5 = signed12((uint16_t(h[5]) << 4) | (h[4] >> 4));
        h6 = static_cast<int8_t>(h[6]);
        if (!t1 || t1 == 0xffff || !p1 || p1 == 0xffff) return false;
        return write(0xf2, 1) && write(0xf5, 0xa0) && write(0xf4, 0x27);
    }

    bool sample(double& temperature, double& humidity, double& pressure) {
        uint8_t b[8], control;
        // Detect loss/reset of the configured device as well as bus failures.
        if (!read(0xf4, &control, 1) || control != 0x27 || !read(0xf7, b, 8)) return false;
        const uint32_t rp = (uint32_t(b[0]) << 12) | (uint32_t(b[1]) << 4) | (b[2] >> 4);
        const uint32_t rt = (uint32_t(b[3]) << 12) | (uint32_t(b[4]) << 4) | (b[5] >> 4);
        const uint16_t rh = (uint16_t(b[6]) << 8) | b[7];
        if (rt == 0x80000 || rp == 0x80000 || rh == 0x8000) return false;
        double v1 = (rt / 16384.0 - t1 / 1024.0) * t2;
        double v2 = (rt / 131072.0 - t1 / 8192.0);
        v2 = v2 * v2 * t3;
        const double fine = v1 + v2;
        temperature = fine / 5120.0;
        v1 = fine / 2.0 - 64000.0;
        v2 = v1 * v1 * p[4] / 32768.0 + v1 * p[3] * 2.0;
        v2 = v2 / 4.0 + p[2] * 65536.0;
        v1 = (p[1] * v1 * v1 / 524288.0 + p[0] * v1) / 524288.0;
        v1 = (1.0 + v1 / 32768.0) * p1;
        if (v1 == 0) return false;
        double pa = (1048576.0 - rp - v2 / 4096.0) * 6250.0 / v1;
        v1 = p[7] * pa * pa / 2147483648.0;
        v2 = pa * p[6] / 32768.0;
        pressure = (pa + (v1 + v2 + p[5]) / 16.0) / 100.0;
        double h = fine - 76800.0;
        h = (rh - (h4 * 64.0 + h5 / 16384.0 * h)) *
            (h2 / 65536.0 * (1.0 + h6 / 67108864.0 * h * (1.0 + h3 / 67108864.0 * h)));
        h *= 1.0 - h1 * h / 524288.0;
        humidity = h < 0 ? 0 : (h > 100 ? 100 : h);
        return std::isfinite(temperature) && std::isfinite(humidity) && std::isfinite(pressure) &&
            temperature >= -40 && temperature <= 85 && pressure >= 300 && pressure <= 1100;
    }
private:
    static uint16_t u16(const uint8_t* b) { return b[0] | (uint16_t(b[1]) << 8); }
    static int16_t s16(const uint8_t* b) { return static_cast<int16_t>(u16(b)); }
    static int16_t signed12(uint16_t v) { return v & 0x800 ? int(v) - 4096 : v; }
    static bool read(uint8_t reg, uint8_t* out, size_t count) {
        Wire.beginTransmission(0x76);
        Wire.write(reg);
        if (Wire.endTransmission(false) != 0 || Wire.requestFrom(uint8_t(0x76), count) != count) return false;
        for (size_t i = 0; i < count; ++i) out[i] = Wire.read();
        return true;
    }
    static bool write(uint8_t reg, uint8_t value) {
        Wire.beginTransmission(0x76); Wire.write(reg); Wire.write(value);
        return Wire.endTransmission() == 0;
    }
    uint16_t t1 = 0, p1 = 0;
    int16_t t2 = 0, t3 = 0, p[8] = {}, h2 = 0, h4 = 0, h5 = 0;
    uint8_t h1 = 0, h3 = 0;
    int8_t h6 = 0;
};
