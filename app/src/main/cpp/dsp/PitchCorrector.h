#pragma once
#include "Common.h"
#include "PitchDetector.h"

namespace vfx {

struct TuneSettings {
    bool correct = false;     // aplicar corrección de afinación
    int mode = 0;             // 0 = automático (tonalidad+escala), 1 = manual (notas elegidas)
    int key = 0, scale = 1;   // tónica 0..11 y escala
    int manualMask = 0xFFF;   // bits por clase de nota (C=bit0)
    float speedMs = 40.f;     // constante de tiempo de retune
    float humanize = 0.5f;    // 0..1  (1 = conserva el vibrato natural)
    float amount = 1.f;       // 0..1
    float flexCents = 6.f;    // banda muerta alrededor de la nota
    double a4 = 440.0;
    float pitchSemi = 0.f;    // cambio de pitch manual
    float monoLock = 0.f;     // 0..1 robot monótono
};

inline int scaleMask(int key, int scale) {
    static const int iv[9][12] = {
        {0,1,2,3,4,5,6,7,8,9,10,11}, {0,2,4,5,7,9,11,-1}, {0,2,3,5,7,8,10,-1}, {0,2,3,5,7,8,11,-1},
        {0,2,4,7,9,-1}, {0,3,5,7,10,-1}, {0,3,5,6,7,10,-1}, {0,2,3,5,7,9,10,-1}, {0,2,4,5,7,9,10,-1}};
    int s = scale < 0 ? 0 : (scale > 8 ? 8 : scale), mask = 0;
    for (int i = 0; i < 12 && iv[s][i] >= 0; i++) mask |= 1 << (((key % 12) + iv[s][i]) % 12);
    return mask;
}

class PitchCorrector {
public:
    void init(double sampleRate) {
        fs = sampleRate; dt = PitchDetector::kHop / fs;
        slow = m = 60.0; corr = 0.0; target = 60; haveTarget = false; voiced = false; sm = 60.0; smInit = false;
    }
    static bool inMask(int note, int mask) { return (mask >> (((note % 12) + 12) % 12)) & 1; }
    static int nearestIn(double x, int mask) {
        if (mask == 0) mask = 0xFFF;
        int best = (int)std::lround(x); double bd = 1e9;
        for (int n = (int)std::floor(x) - 7; n <= (int)std::ceil(x) + 7; n++)
            if (inMask(n, mask)) { double dd = std::fabs(n - x); if (dd < bd) { bd = dd; best = n; } }
        return best;
    }
    static int nearestClass(double x, int pc) {
        int best = pc; double bd = 1e9;
        for (int n = (int)std::floor(x) - 7; n <= (int)std::ceil(x) + 7; n++)
            if ((((n - pc) % 12) + 12) % 12 == 0) { double dd = std::fabs(n - x); if (dd < bd) { bd = dd; best = n; } }
        return best;
    }
    // Sube "steps" grados dentro de la escala; devuelve la nota resultante.
    static int stepUp(int note, int steps, int mask) {
        if (mask == 0) mask = 0xFFF;
        int n = note, left = steps;
        while (left > 0 && n < note + 48) { n++; if (inMask(n, mask)) left--; }
        return n;
    }
    // Intervalo diatónico (en semitonos) por encima de la nota objetivo: tercera=2 grados, quinta=4.
    int harmonyInterval(int degreeSteps) const {
        int pop = 0; for (int i = 0; i < 12; i++) pop += (curMask >> i) & 1;
        if (pop >= 11) return degreeSteps == 2 ? 4 : 7;   // cromática: tercera mayor / quinta justa
        return stepUp(target, degreeSteps, curMask) - target;
    }

    // Una estimación nueva del detector. Actualiza el desplazamiento de tono objetivo.
    void update(const PitchDetector::Result& r, const TuneSettings& s) {
        curMask = s.mode == 1 ? (s.manualMask & 0xFFF) : scaleMask(s.key, s.scale);
        if (curMask == 0) curMask = 0xFFF;
        if (!r.voiced || r.hz <= 0.f) {
            voiced = false; smInit = false;
            corr *= std::exp(-dt / 0.03);
            totalShift = (s.correct ? corr : 0.0) + s.pitchSemi;
            return;
        }
        double meas = hzToMidi(r.hz, s.a4);
        if (!voiced || !smInit) { slow = meas; sm = meas; smInit = true; haveTarget = false; if (!voiced) corr = 0.0; }
        voiced = true;
        sm += (meas - sm) * (1.0 - std::exp(-dt / 0.004));   // suaviza el ruido de medición (4 ms)
        m = sm;
        // Un salto de más de ~0,75 st es una nota nueva (el vibrato no pasa de ±0,5 st): re-enganche inmediato.
        if (std::fabs(m - slow) > 0.75) { slow = m; haveTarget = false; }
        slow += (m - slow) * (1.0 - std::exp(-dt / 0.130));
        int cand = nearestIn(slow, curMask);
        if (!haveTarget || !inMask(target, curMask)) { target = cand; haveTarget = true; }
        else if (cand != target && std::fabs(slow - cand) + 0.25 < std::fabs(slow - target)) target = cand;

        double dev = (double)target - slow;
        double flex = s.flexCents / 100.0;
        double ax = std::fabs(dev), xw = flex > 1e-6 ? std::min(1.0, std::max(0.0, (ax - 0.5 * flex) / flex)) : 1.0;
        double eff = dev * xw * xw * (3.0 - 2.0 * xw);   // banda de tolerancia suave (sin escalón)
        double corrT = eff * s.amount - (m - slow) * (1.0 - s.humanize) * s.amount * (std::fabs(dev) < 0.6 ? 1.0 : 0.0);
        double tau = std::max(0.0, (double)s.speedMs) * 0.001;
        if (s.monoLock > 0.f) {
            double lockT = nearestClass(slow, s.key % 12) - m;
            corrT = corrT * (1.0 - s.monoLock) + lockT * s.monoLock;
            tau *= (1.0 - s.monoLock);
        }
        if (!s.correct && s.monoLock <= 0.f) corrT = 0.0;
        double a = tau < 0.0005 ? 1.0 : 1.0 - std::exp(-dt / tau);
        corr += (corrT - corr) * a;
        totalShift = corr + s.pitchSemi;
    }

    bool isVoiced() const { return voiced; }
    double measuredMidi() const { return m; }
    double outMidi() const { return m + totalShift; }
    int targetNote() const { return target; }
    double shiftSemis() const { return totalShift; }
    double centsOff() const { return (m - target) * 100.0; }

private:
    double fs = 48000, dt = 0.00533;
    double slow = 60, m = 60, corr = 0, sm = 60, totalShift = 0;
    int target = 60, curMask = 0xFFF; bool haveTarget = false, voiced = false, smInit = false;
};

}  // namespace vfx
