#pragma once
#include <cstring>
#include <vector>
#include "Common.h"

namespace vfx {

constexpr int kAnaRing = 1 << 15, kAnaMask = kAnaRing - 1;
constexpr int kMarkRing = 2048, kMarkMask = kMarkRing - 1;
constexpr int kVoiceRing = 1 << 13, kVoiceMask = kVoiceRing - 1;
#ifndef VFX_MAX_GRAIN
#define VFX_MAX_GRAIN 1.0
#endif
constexpr double kMaxGrain = VFX_MAX_GRAIN;

struct Mark { double pos = 0; float per = 240.f; };

// Análisis PSOLA síncrono con el tono: guarda la señal de entrada y coloca marcas
// de periodo alineadas por correlación con el ciclo anterior (no por picos sueltos).
class PsolaAnalyzer {
public:
    void init(double sampleRate) {
        fs = sampleRate;
        defPeriod = (float)(fs * 0.005);
        guard = (int)(fs * 0.0125);
        latency = (int)(fs * 0.024);
        std::memset(buf, 0, sizeof(buf));
        n = 0; markN = 0; lastPos = 0; haveLast = false; lastWasVoiced = false;
        period = defPeriod; voiced = false;
    }
    inline void write(float x) { buf[n & kAnaMask] = x; ++n; }
    int64_t count() const { return n; }
    int latencySamples() const { return latency; }
    void setPitch(float periodSamples, bool isVoiced) {
        voiced = isVoiced;
        if (isVoiced) period = std::max((float)(fs / 1100.0), std::min((float)(fs / 70.0), periodSamples));
    }
    inline float at(int64_t i) const {
        if (i < 0 || i >= n || i < n - kAnaRing + 8) return 0.f;
        return buf[i & kAnaMask];
    }
    // Interpolación cúbica de Hermite (Catmull-Rom) en posición fraccionaria.
    float sample(double pos) const {
        int64_t i0 = (int64_t)std::floor(pos);
        if (i0 + 2 >= n || i0 < 1) return i0 + 1 < n && i0 >= 0 ? at(i0) : 0.f;
        float f = (float)(pos - (double)i0);
        float y0 = at(i0 - 1), y1 = at(i0), y2 = at(i0 + 1), y3 = at(i0 + 2);
        float c1 = 0.5f * (y2 - y0), c2 = y0 - 2.5f * y1 + 2.f * y2 - 0.5f * y3, c3 = 0.5f * (y3 - y0) + 1.5f * (y1 - y2);
        return ((c3 * f + c2) * f + c1) * f + y1;
    }

    // Crea marcas hasta (n - guard). Llamar tras escribir un bloque.
    void updateMarks() {
        double limit = (double)n - guard;
        if (!haveLast) { lastPos = limit - period; haveLast = true; }
        for (int guardLoop = 0; guardLoop < 64; guardLoop++) {
            float P = voiced ? period : defPeriod;
            double target = lastPos + P;
            double reach = voiced ? 0.3 * P : 0.0;
            if (target + reach + 4 > limit) break;
            double pos = target;
            if (voiced) pos = lastWasVoiced ? alignByTemplate(target, P) : alignByPeak(target, P);
            pos = std::max(pos, lastPos + 0.45 * P);
            Mark& m = marks[markN & kMarkMask]; m.pos = pos; m.per = P; ++markN;
            lastPos = pos; lastWasVoiced = voiced;
        }
    }

    // Marca más cercana al instante t (en muestras absolutas).
    bool nearest(double t, Mark& out) const {
        if (markN == 0) return false;
        int64_t cnt = std::min<int64_t>(markN, kMarkRing);
        int64_t k = markN - 1; int64_t oldest = markN - cnt;
        while (k > oldest && marks[k & kMarkMask].pos > t) --k;
        const Mark* best = &marks[k & kMarkMask];
        if (k + 1 < markN) {
            const Mark* nx = &marks[(k + 1) & kMarkMask];
            if (std::fabs(nx->pos - t) < std::fabs(best->pos - t)) best = nx;
        }
        out = *best; return true;
    }

private:
    double fs = 48000; float defPeriod = 240; int guard = 600, latency = 1152;
    float buf[kAnaRing];
    Mark marks[kMarkRing];
    int64_t n = 0, markN = 0;
    double lastPos = 0; bool haveLast = false, lastWasVoiced = false;
    float period = 240; bool voiced = false;

    double alignByPeak(double target, float P) const {
        int lo = (int)std::floor(target - 0.3 * P), hi = (int)std::ceil(target + 0.3 * P);
        int bi = lo; float bv = -1.f;
        for (int i = lo; i <= hi; i++) {
            float v = std::fabs(0.25f * at(i - 1) + 0.5f * at(i) + 0.25f * at(i + 1));
            if (v > bv) { bv = v; bi = i; }
        }
        return bi;
    }
    // Busca el desplazamiento que maximiza la correlación normalizada con el ciclo previo.
    double alignByTemplate(double target, float P) const {
        int T = std::max(8, (int)(P * 0.28f));
        int c0 = (int)std::lround(lastPos);
        int lo = (int)std::floor(target - 0.3 * P), hi = (int)std::ceil(target + 0.3 * P);
        double scores[2048]; int cnt = hi - lo + 1; if (cnt > 2040 || cnt < 3) return target;
        double tEn = 1e-9; for (int j = -T; j <= T; j++) { float v = at(c0 + j); tEn += v * v; }
        int bi = 0;
        for (int c = lo; c <= hi; c++) {
            double cr = 0, en = 1e-9;
            for (int j = -T; j <= T; j++) { float v = at(c + j); cr += at(c0 + j) * v; en += v * v; }
            double s = cr / std::sqrt(en * tEn);
            scores[c - lo] = s; if (s > scores[bi]) bi = c - lo;
        }
        double off = 0;
        if (bi > 0 && bi < cnt - 1) {
            double a = scores[bi - 1], b = scores[bi], c = scores[bi + 1], den = a - 2 * b + c;
            if (std::fabs(den) > 1e-12) off = std::max(-0.5, std::min(0.5, 0.5 * (a - c) / den));
        }
        return lo + bi + off;
    }
};

// Control de una voz de síntesis (se actualiza antes de cada render).
struct VoiceCtl {
    double fOut = 200.0;        // frecuencia de salida deseada (Hz) cuando hay tono
    double formant = 1.0;       // razón de formantes (1 = sin cambio)
    int extraDelay = 0;         // retardo adicional en muestras (dobles)
    bool voiced = false;
};

// Síntesis PSOLA (overlap-add de granos de 2 periodos con ventana de Hann).
// El periodo de salida es 1/fOut, independiente del de entrada: cambia el tono sin cambiar los formantes.
class PsolaVoice {
public:
    void init(double sampleRate) { fs = sampleRate; defPeriod = fs * 0.005; reset(0); }
    void reset(int64_t nowAbs) {
        std::memset(acc, 0, sizeof(acc)); std::memset(nrm, 0, sizeof(nrm));
        outPos = nowAbs; tsNext = (double)nowAbs + defPeriod; 
    }
    // Renderiza todos los granos que afectan a muestras < nEnd.
    void render(const PsolaAnalyzer& an, const VoiceCtl& c, int64_t nEnd) {
        const int D = an.latencySamples();
        for (int guardLoop = 0; guardLoop < 256; guardLoop++) {
            double Ps = c.voiced ? fs / std::max(30.0, c.fOut) : defPeriod;
            Mark mk;
            if (!an.nearest(tsNext - D - c.extraDelay, mk)) { if (tsNext - Ps > (double)nEnd) break; tsNext += Ps; continue; }
            // Grano de ~2 periodos de análisis. En bajadas de tono se ensancha sólo hasta kMaxGrain
            // periodos para que no entren ciclos repetidos que mantendrían el tono original.
            double Pa = (double)mk.per;
            double H = std::max(Pa, std::min(Ps, kMaxGrain * Pa));
            H = std::min(H, 2000.0);
            if (tsNext - H > (double)nEnd) break;
            grain(an, mk.pos, tsNext, H, c.formant);
            tsNext += Ps;
        }
    }
    // Extrae la muestra de salida del instante m (m debe incrementarse de 1 en 1).
    inline float pop() {
        int i = (int)(outPos & kVoiceMask);
        float v = nrm[i] > 0.02f ? acc[i] / nrm[i] : 0.f;
        // fuera de zona cubierta: atenuación suave para evitar clicks
        if (nrm[i] < 0.5f) v *= nrm[i] * 2.f;
        acc[i] = 0.f; nrm[i] = 0.f; ++outPos;
        return v;
    }
    int64_t position() const { return outPos; }

private:
    double fs = 48000, defPeriod = 240;
    float acc[kVoiceRing], nrm[kVoiceRing];
    int64_t outPos = 0; double tsNext = 0;

    void grain(const PsolaAnalyzer& an, double ta, double ts, double H, double rho) {
        int64_t i0 = (int64_t)std::ceil(ts - H), i1 = (int64_t)std::floor(ts + H);
        if (i1 < outPos) return;                 // ya emitido: no se puede escribir
        if (i0 < outPos) i0 = outPos;
        double invH = kPi / H;
        for (int64_t i = i0; i <= i1; i++) {
            double dd = (double)i - ts;
            float w = 0.5f * (1.0f + (float)std::cos(dd * invH));
            float v = an.sample(ta + dd * rho);
            int k = (int)(i & kVoiceMask);
            acc[k] += w * v; nrm[k] += w;
        }
    }
};

}  // namespace vfx
