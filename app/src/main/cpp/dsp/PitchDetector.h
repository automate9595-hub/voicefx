#pragma once
#include <cstring>
#include "Biquad.h"

namespace vfx {

// Detector de tono monofónico YIN (de Cheveigné & Kawahara) sobre señal diezmada x4,
// con refinamiento a la frecuencia de muestreo completa (precisión sub-cent en tonos estables).
// Sin asignaciones de memoria en el hilo de audio.
class PitchDetector {
public:
    struct Result {
        float hz = 0.f;        // frecuencia fundamental (0 si no hay tono)
        float conf = 0.f;      // 0..1
        float rms = 0.f;       // nivel de la ventana de análisis
        bool voiced = false;   // tono estable detectado (con histéresis)
        bool fresh = false;    // true si se acaba de calcular una estimación nueva
    };
    static constexpr int kHop = 256;  // muestras entre estimaciones (~5,3 ms a 48 kHz)

    void init(double sampleRate) {
        fs = sampleRate;
        dec = 4;
        fd = fs / dec;
        tauMin = std::max(2, (int)(fd / 1000.0));
        tauMax = std::min(kMaxTau - 2, (int)(fd / 75.0) + 1);
        win = (int)(0.0215 * fd);
        lp1.lowpass(fs, fs / dec * 0.42, 0.5412);
        lp2.lowpass(fs, fs / dec * 0.42, 1.3066);
        std::memset(full, 0, sizeof(full)); std::memset(dbuf, 0, sizeof(dbuf));
        fullW = dW = 0; sinceHop = 0; decCount = 0;
        voicedRun = unvoicedRun = 0; res = Result(); histN = 0;
    }

    // Procesa una muestra; devuelve true cuando hay una estimación nueva en result().
    bool push(float x) {
        full[fullW] = x; fullW = (fullW + 1) & kFullMask;
        float y = lp2.process(lp1.process(x));
        if (++decCount == dec) { decCount = 0; dbuf[dW] = y; dW = (dW + 1) & kDecMask; }
        if (++sinceHop >= kHop) { sinceHop = 0; analyze(); return true; }
        res.fresh = false;
        return false;
    }
    const Result& result() const { return res; }

private:
    static constexpr int kFullSize = 4096, kFullMask = kFullSize - 1;
    static constexpr int kDecSize = 2048, kDecMask = kDecSize - 1;
    static constexpr int kMaxTau = 512;

    double fs = 48000, fd = 12000;
    int dec = 4, tauMin = 12, tauMax = 160, win = 258;
    Biquad lp1, lp2;
    float full[kFullSize], dbuf[kDecSize];
    int fullW = 0, dW = 0, sinceHop = 0, decCount = 0;
    float tmp[kDecSize];
    float d[kMaxTau], cm[kMaxTau];
    int voicedRun = 0, unvoicedRun = 0;
    float hist[3]; int histN = 0;
    Result res;

    inline float fullAt(int back) const { return full[(fullW - 1 - back) & kFullMask]; }  // back=0 -> última muestra

    void analyze() {
        res.fresh = true;
        // RMS de las últimas 1024 muestras
        double e = 0; for (int i = 0; i < 1024; i++) { float v = fullAt(i); e += v * v; }
        res.rms = (float)std::sqrt(e / 1024.0);
        bool raw = false; float hzRaw = 0.f, conf = 0.f;
        if (res.rms > 0.0025f) {
            int need = win + tauMax;
            for (int i = 0; i < need; i++) tmp[i] = dbuf[(dW - need + i) & kDecMask];
            // d(tau) y diferencia normalizada acumulada (CMND)
            cm[0] = 1.f; double run = 0;
            for (int tau = 1; tau <= tauMax; tau++) {
                double s = 0;
                for (int j = 0; j < win; j++) { float df = tmp[j] - tmp[j + tau]; s += df * df; }
                d[tau] = (float)s; run += s;
                cm[tau] = run > 1e-12 ? (float)(s * tau / run) : 1.f;
            }
            int best = -1;
            for (int tau = tauMin; tau < tauMax; tau++) {
                if (cm[tau] < 0.14f) { while (tau + 1 < tauMax && cm[tau + 1] < cm[tau]) tau++; best = tau; break; }
            }
            if (best < 0) {
                int arg = tauMin; for (int tau = tauMin; tau < tauMax; tau++) if (cm[tau] < cm[arg]) arg = tau;
                if (cm[arg] < 0.30f) best = arg;
            }
            if (best > tauMin && best < tauMax) {
                float a = cm[best - 1], b = cm[best], c = cm[best + 1];
                float den = a - 2 * b + c;
                double shift = std::fabs(den) > 1e-9f ? 0.5 * (a - c) / den : 0.0;
                double tauD = (best + std::max(-1.0, std::min(1.0, shift))) * dec;  // en muestras a fs
                conf = 1.f - cm[best];
                double lag = refine(tauD);
                if (lag > 0) { hzRaw = (float)(fs / lag); raw = hzRaw >= 70.f && hzRaw <= 1100.f; }
            }
        }
        // Mediana de 3 para eliminar saltos de octava aislados
        if (raw) {
            if (histN < 3) hist[histN++] = hzRaw; else { hist[0] = hist[1]; hist[1] = hist[2]; hist[2] = hzRaw; }
            if (histN == 3) {
                float a = hist[0], b = hist[1], c = hist[2];
                hzRaw = std::max(std::min(a, b), std::min(std::max(a, b), c));
            }
            voicedRun++; unvoicedRun = 0;
        } else { unvoicedRun++; voicedRun = 0; if (unvoicedRun > 2) histN = 0; }
        if (!res.voiced && voicedRun >= 2) res.voiced = true;
        if (res.voiced && unvoicedRun >= 3) res.voiced = false;
        if (raw) { res.hz = hzRaw; res.conf = conf; }
        else if (!res.voiced) { res.hz = 0.f; res.conf = 0.f; }
    }

    // Afina el periodo en la señal sin diezmar (mínimo de d(lag) con interpolación parabólica).
    double refine(double lag0) {
        const int W = 1024;
        int c = (int)std::lround(lag0), lo = std::max(8, c - 6), hi = c + 6;
        if (hi + W + 2 > kFullSize - 8) return lag0;
        double dv[16]; int n = hi - lo + 1; int bi = 0;
        for (int L = lo; L <= hi; L++) {
            double s = 0, en = 1e-12;
            for (int j = 0; j < W; j++) {
                float a = fullAt(j + L), b = fullAt(j);
                float df = a - b; s += df * df; en += a * a + b * b;
            }
            dv[L - lo] = s / en;
            if (dv[L - lo] < dv[bi]) bi = L - lo;
        }
        if (bi == 0 || bi == n - 1) return lo + bi;
        double a = dv[bi - 1], b = dv[bi], cc = dv[bi + 1], den = a - 2 * b + cc;
        double sh = std::fabs(den) > 1e-12 ? 0.5 * (a - cc) / den : 0.0;
        return lo + bi + std::max(-0.5, std::min(0.5, sh));
    }
};

}  // namespace vfx
