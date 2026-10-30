#pragma once
#include <cstddef>
#include <cstdint>
struct TestWire {
    uint8_t regs[256] = {};
    uint8_t pointer = 0;
    bool first = true, fail = false, shortRead = false;
    void beginTransmission(int) { first = true; }
    void write(uint8_t v) { if (first) { pointer = v; first = false; } else regs[pointer++] = v; }
    int endTransmission(bool = true) { return fail ? 2 : 0; }
    size_t requestFrom(uint8_t, size_t n) { return shortRead ? n - 1 : n; }
    uint8_t read() { return regs[pointer++]; }
};
extern TestWire Wire;
