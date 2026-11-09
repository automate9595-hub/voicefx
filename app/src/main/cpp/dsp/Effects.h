#pragma once
#include <vector>
#include "Biquad.h"

namespace vfx {

// ---------- Línea de retardo con lectura fraccionaria (interpolación lineal) ----------
class DelayLine {
public:
    void init(int maxSamples) {
        int n = 16; while (n < maxSamples + 4) n <<= 1;
        buf.assign((size_t)n, 0.f); mask = n - 1; w = 0;
    }
    inline void write(float x) { buf[(size_t)(w & mask)] = x; ++w; }
    // d = muestras de retardo (>=1) respecto de la última escrita
    inline float read(float d) const {
        float pos = (float)w - 1.0f - d + 1.0f;
        int i0 = (int)std::floor(pos); float f = pos - (float)i0;
        float a = buf[(size_t)(i0 & mask)], b = buf[(size_t)((i0 + 1) & mask)];
        return a + (b - a) * f;
    }
    inline float readInt(int d) const { return buf[(size_t)((w - d) & mask)]; }
    int capacity() const { return mask + 1; }
private:
    std::vector<float> buf; int mask = 15; int w = 0;
};

// ---------- Puerta de ruido / expansor ----------
class NoiseGate {
public:
    void init(double fs_) { fs = fs_; env = 0; gain = 1; hold = 0; open = true; }
    // amount 0..100 (0 = apagada). Umbral de -75 a -30 dBFS.
    inline float process(float x, float amount) {
        if (amount <= 0.5f) { gain += (1.f - gain) * 0.01f; env = 0; return x * (gain > 0.999f ? 1.f : gain); }
        float thr = dbToLin(-75.f + amount * 0.45f);
        float a = std::fabs(x);
        env += (a - env) * (a > env ? 1.f - (float)std::exp(-1.0 / (fs * 0.001)) : 1.f - (float)std::exp(-1.0 / (fs * 0.03)));
        float closedGain = dbToLin(-40.f);
        if (env > thr) { hold = (int)(fs * 0.06); open = true; }
        else if (hold > 0) { --hold; } else open = false;
        float tgt = open ? 1.f : closedGain;
        float coef = tgt > gain ? 1.f - (float)std::exp(-1.0 / (fs * 0.002)) : 1.f - (float)std::exp(-1.0 / (fs * 0.12));
        gain += (tgt - gain) * coef;
        return x * gain;
    }
    bool isOpen() const { return open; }
private:
    double fs = 48000; float env = 0, gain = 1; int hold = 0; bool open = true;
};

// ---------- Compresor con rodilla suave y makeup automático ----------
class Compressor {
public:
    void init(double fs_) { fs = fs_; envDb = -90.f; grDb = 0.f; }
    inline float process(float x, float amount) {   // amount 0..100
        if (amount < 0.5f) { grDb *= 0.995f; return x; }
        float c = amount * 0.01f;
        float thr = -10.f - 24.f * c, ratio = 1.5f + 5.5f * c, knee = 8.f;
        float lvl = linToDb(std::fabs(x));
        float aA = 1.f - (float)std::exp(-1.0 / (fs * 0.004)), aR = 1.f - (float)std::exp(-1.0 / (fs * 0.100));
        envDb += (lvl > envDb ? aA : aR) * (lvl - envDb);
        float over = envDb - thr, out;
        if (over <= -knee * 0.5f) out = 0.f;
        else if (over >= knee * 0.5f) out = over * (1.f - 1.f / ratio);
        else { float t = over + knee * 0.5f; out = (1.f - 1.f / ratio) * t * t / (2.f * knee); }
        grDb += (out - grDb) * 0.5f;
        float makeup = (-thr) * (1.f - 1.f / ratio) * 0.45f;
        return x * dbToLin(makeup - grDb);
    }
    float grDbNow() const { return grDb; }
private:
    double fs = 48000; float envDb = -90.f, grDb = 0.f;
};

// ---------- De-esser (reducción de banda sibilante controlada por sidechain) ----------
class DeEsser {
public:
    void init(double fs_) { fs = fs_; env = 0; g = 1; lastF = -1; }
    inline float process(float x, float amount, float freq) {
        if (amount < 0.5f) return x;
        if (freq != lastF) { lastF = freq; bp.bandpass(fs, freq, 1.4); lpA.lowpass(fs, freq * 0.8, 0.5412); lpB.lowpass(fs, freq * 0.8, 1.3066); }
        // división complementaria: low = LP(x), band = x - low (suman exactamente x cuando g = 1)
        float sc = bp.process(x), low = lpB.process(lpA.process(x)), band = x - low;
        float a = std::fabs(sc);
        env += (a > env ? 1.f - (float)std::exp(-1.0 / (fs * 0.0005)) : 1.f - (float)std::exp(-1.0 / (fs * 0.02))) * (a - env);
        float thr = dbToLin(-18.f - amount * 0.28f);
        float tgt = 1.f;
        if (env > thr) tgt = std::pow(thr / env, 0.75f);
        g += (tgt - g) * (tgt < g ? 0.2f : 0.02f);
        return low + band * g;
    }
    float gainNow() const { return g; }
private:
    double fs = 48000; float env = 0, g = 1, lastF = -1; Biquad bp, lpA, lpB;
};

// ---------- Saturación con sobremuestreo x2 (reduce aliasing) ----------
class Saturator {
public:
    void init(double fs) {
        for (int i = 0; i < 2; i++) { up[i].lowpass(fs * 2, fs * 0.45, i ? 1.3066 : 0.5412); dn[i].lowpass(fs * 2, fs * 0.45, i ? 1.3066 : 0.5412); }
    }
    inline float process(float x, float drive) {  // drive 0..100
        if (drive < 0.5f) return x;
        float k = 1.f + drive * 0.09f, norm = 1.f / std::tanh(k);
        float s0 = up[1].process(up[0].process(2.f * x)), s1 = up[1].process(up[0].process(0.f));
        s0 = std::tanh(s0 * k + 0.08f * k * s0 * s0) * norm; s1 = std::tanh(s1 * k + 0.08f * k * s1 * s1) * norm;
        float y0 = dn[1].process(dn[0].process(s0)); dn[1].process(dn[0].process(s1));
        float comp = 1.f / (1.f + drive * 0.004f);
        return y0 * comp;
    }
private:
    Biquad up[2], dn[2];
};

// ---------- Chorus (dos voces moduladas) ----------
class Chorus {
public:
    void init(double fs_) { fs = fs_; d.init((int)(fs * 0.06)); ph = 0; }
    inline float process(float x, float amount, float rate) {
        d.write(x);
        if (amount < 0.5f) { return x; }
        ph += rate / fs; if (ph >= 1.0) ph -= 1.0;
        float depth = (0.0016f + 0.0030f * amount * 0.01f) * (float)fs;
        float base = 0.016f * (float)fs;
        float l1 = (float)std::sin(2 * kPi * ph), l2 = (float)std::sin(2 * kPi * (ph + 0.33) * 1.13);
        float v = 0.5f * (d.read(base + depth * l1) + d.read(base * 1.25f + depth * l2));
        float mix = amount * 0.01f;
        return x * (1.f - 0.35f * mix) + v * (0.35f + 0.65f * mix) * mix;
    }
private:
    double fs = 48000; DelayLine d; double ph = 0;
};

// ---------- Flanger (retardo corto + realimentación) ----------
class Flanger {
public:
    void init(double fs_) { fs = fs_; d.init((int)(fs * 0.01)); ph = 0; fb = 0; }
    inline float process(float x, float amount, float rate, float feedback) {
        if (amount < 0.5f) { d.write(x); fb = 0; return x; }
        ph += rate / fs; if (ph >= 1.0) ph -= 1.0;
        float lfo = 0.5f + 0.5f * (float)std::sin(2 * kPi * ph);
        float del = (0.0004f + 0.0032f * lfo) * (float)fs;
        float w = d.read(del);
        d.write(x + fb);
        fb = clampf(w * feedback * 0.01f, -0.95f, 0.95f);
        return x + w * (amount * 0.01f) * 0.85f;
    }
private:
    double fs = 48000; DelayLine d; double ph = 0; float fb = 0;
};

// ---------- Phaser (4 etapas all-pass con barrido exponencial) ----------
class Phaser {
public:
    void init(double fs_) { fs = fs_; ph = 0; for (float& z : zs) z = 0; fb = 0; }
    inline float process(float x, float amount, float rate) {
        if (amount < 0.5f) { return x; }
        ph += rate / fs; if (ph >= 1.0) ph -= 1.0;
        float lfo = 0.5f + 0.5f * (float)std::sin(2 * kPi * ph);
        float fc = 300.f * std::pow(3600.f / 300.f, lfo);
        float t = (float)std::tan(kPi * fc / fs), a = (t - 1.f) / (t + 1.f);
        float s = x + fb * 0.6f;
        for (int i = 0; i < 4; i++) { float y = a * s + zs[i]; zs[i] = s - a * y; s = y; }
        fb = s;
        float mix = amount * 0.01f;
        return x * (1.f - 0.5f * mix) + s * 0.5f * mix * 1.4f;
    }
private:
    double fs = 48000; double ph = 0; float zs[4]; float fb = 0;
};

// ---------- Vibrato (modulación de retardo, 100 % húmedo) ----------
class Vibrato {
public:
    void init(double fs_) { fs = fs_; d.init((int)(fs * 0.03)); ph = 0; }
    inline float process(float x, float depth, float rate) {  // depth 0..100 -> hasta ±60 cents
        d.write(x);
        if (depth < 0.5f) return x;
        ph += rate / fs; if (ph >= 1.0) ph -= 1.0;
        float cents = 0.6f * depth;                      // 0..60 cents
        float amp = (std::pow(2.f, cents / 1200.f) - 1.f) * (float)fs / (2.f * (float)kPi * rate);
        float base = amp + 2.f;
        return d.read(base + amp * (float)std::sin(2 * kPi * ph));
    }
private:
    double fs = 48000; DelayLine d; double ph = 0;
};

// ---------- Echo / Delay (con filtro en la realimentación) ----------
class Echo {
public:
    void init(double fs_) { fs = fs_; d.init((int)(fs * 1.3)); cur = (float)(fs * 0.24); lp = 0; }
    inline float process(float x, float mix, float timeMs, float fbPct) {
        float target = clampf(timeMs * 0.001f * (float)fs, 8.f, (float)(fs * 1.2));
        cur += (target - cur) * 0.0008f;
        float w = d.read(cur);
        lp += 0.45f * (w - lp);
        d.write(x + clampf(lp * fbPct * 0.01f, -1.2f, 1.2f));
        return mix > 0.5f ? w * (mix * 0.01f) : 0.f;   // sólo la parte húmeda
    }
private:
    double fs = 48000; DelayLine d; float cur = 11520, lp = 0;
};

// ---------- Reverb (Schroeder/Freeverb con pre-delay) ----------
class Reverb {
public:
    void init(double fs_) {
        fs = fs_;
        static const int ct[8] = {1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617}, at[4] = {556, 441, 341, 225};
        double sc = fs / 44100.0;
        for (int i = 0; i < 8; i++) { cl[i] = std::max(8, (int)(ct[i] * sc)); cb[i].assign((size_t)cl[i], 0.f); ci[i] = 0; cs[i] = 0; }
        for (int i = 0; i < 4; i++) { al[i] = std::max(8, (int)(at[i] * sc)); ab[i].assign((size_t)al[i], 0.f); ai[i] = 0; }
        pre.init((int)(fs * 0.1)); hp.highpass(fs, 160, 0.7071); lpOut.lowpass(fs, 9000, 0.7071);
    }
    // devuelve sólo la señal húmeda
    inline float process(float x, float mix, float size, float damp, float preMs) {
        pre.write(x);
        if (mix < 0.5f) return 0.f;
        float in = pre.read(std::max(1.f, preMs * 0.001f * (float)fs)) * 0.015f;
        float feedback = 0.70f + 0.27f * size * 0.01f, dp = 0.08f + 0.80f * damp * 0.01f;
        float sum = 0.f;
        for (int i = 0; i < 8; i++) {
            float o = cb[i][(size_t)ci[i]];
            cs[i] = o * (1.f - dp) + cs[i] * dp;
            cb[i][(size_t)ci[i]] = sanitize(in + cs[i] * feedback);
            if (++ci[i] >= cl[i]) ci[i] = 0;
            sum += o;
        }
        for (int i = 0; i < 4; i++) {
            float b = ab[i][(size_t)ai[i]], v = sum + b * 0.5f;
            ab[i][(size_t)ai[i]] = sanitize(v); sum = b - sum * 0.5f;
            if (++ai[i] >= al[i]) ai[i] = 0;
        }
        return lpOut.process(hp.process(sum)) * (mix * 0.01f) * 1.6f;
    }
private:
    double fs = 48000; int cl[8], ci[8], al[4], ai[4]; std::vector<float> cb[8], ab[4]; float cs[8] = {0};
    DelayLine pre; Biquad hp, lpOut;
};

// ---------- Limitador de salida (protección contra saturación digital) ----------
class Limiter {
public:
    void init(double fs_) { fs = fs_; g = 1.f; }
    inline float process(float x) {
        float a = std::fabs(x), need = a > 0.95f ? 0.95f / a : 1.f;
        if (need < g) g = need; else g += (1.f - g) * (1.f - (float)std::exp(-1.0 / (fs * 0.08)));
        float y = x * g;
        return clampf(y, -0.98f, 0.98f);
    }
private:
    double fs = 48000; float g = 1.f;
};

}  // namespace vfx
