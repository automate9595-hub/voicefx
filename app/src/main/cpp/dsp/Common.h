#pragma once
#include <cmath>
#include <cstdint>
#include <algorithm>

namespace vfx {

constexpr double kPi = 3.14159265358979323846;

inline float dbToLin(float db) { return std::pow(10.0f, db * 0.05f); }
inline float linToDb(float lin) { return 20.0f * std::log10(std::max(lin, 1e-9f)); }
inline float clampf(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }
inline double midiToHz(double m, double a4) { return a4 * std::pow(2.0, (m - 69.0) / 12.0); }
inline double hzToMidi(double hz, double a4) { return 69.0 + 12.0 * std::log2(hz / a4); }

// Suavizador de un polo (para parámetros: evita "zipper noise").
struct Smoother {
    float v = 0.f, a = 0.f;
    void init(double fs, double ms, float start) { v = start; a = 1.0f - std::exp(-1.0 / (fs * ms * 0.001)); }
    inline float step(float target) { v += a * (target - v); return v; }
};

// Elimina valores denormales / NaN que podrían congelar un filtro recursivo.
inline float sanitize(float x) {
    if (!(std::fabs(x) < 1e6f)) return 0.f;
    return x;
}

}  // namespace vfx
