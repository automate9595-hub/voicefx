#pragma once
#include "Common.h"

namespace vfx {

// Filtro bicuadrático RBJ, forma transpuesta II.
struct Biquad {
    float b0 = 1, b1 = 0, b2 = 0, a1 = 0, a2 = 0, z1 = 0, z2 = 0;

    inline float process(float x) {
        float y = b0 * x + z1;
        z1 = b1 * x - a1 * y + z2;
        z2 = b2 * x - a2 * y;
        return y;
    }
    void reset() { z1 = z2 = 0; }

    void set(double nb0, double nb1, double nb2, double na0, double na1, double na2) {
        b0 = (float)(nb0 / na0); b1 = (float)(nb1 / na0); b2 = (float)(nb2 / na0);
        a1 = (float)(na1 / na0); a2 = (float)(na2 / na0);
    }
    static void trig(double fs, double f, double q, double& w0, double& cs, double& alpha) {
        f = std::min(std::max(f, 10.0), fs * 0.49);
        w0 = 2.0 * kPi * f / fs; cs = std::cos(w0); alpha = std::sin(w0) / (2.0 * q);
    }
    void lowpass(double fs, double f, double q = 0.7071) {
        double w0, cs, al; trig(fs, f, q, w0, cs, al);
        set((1 - cs) / 2, 1 - cs, (1 - cs) / 2, 1 + al, -2 * cs, 1 - al);
    }
    void highpass(double fs, double f, double q = 0.7071) {
        double w0, cs, al; trig(fs, f, q, w0, cs, al);
        set((1 + cs) / 2, -(1 + cs), (1 + cs) / 2, 1 + al, -2 * cs, 1 - al);
    }
    void bandpass(double fs, double f, double q) {  // ganancia 0 dB en el pico
        double w0, cs, al; trig(fs, f, q, w0, cs, al);
        set(al, 0, -al, 1 + al, -2 * cs, 1 - al);
    }
    void peaking(double fs, double f, double q, double gainDb) {
        double w0, cs, al; trig(fs, f, q, w0, cs, al);
        double A = std::pow(10.0, gainDb / 40.0);
        set(1 + al * A, -2 * cs, 1 - al * A, 1 + al / A, -2 * cs, 1 - al / A);
    }
    void lowShelf(double fs, double f, double gainDb) {
        double w0, cs, al; trig(fs, f, 0.7071, w0, cs, al);
        double A = std::pow(10.0, gainDb / 40.0), sq = 2 * std::sqrt(A) * al;
        set(A * ((A + 1) - (A - 1) * cs + sq), 2 * A * ((A - 1) - (A + 1) * cs), A * ((A + 1) - (A - 1) * cs - sq),
            (A + 1) + (A - 1) * cs + sq, -2 * ((A - 1) + (A + 1) * cs), (A + 1) + (A - 1) * cs - sq);
    }
    void highShelf(double fs, double f, double gainDb) {
        double w0, cs, al; trig(fs, f, 0.7071, w0, cs, al);
        double A = std::pow(10.0, gainDb / 40.0), sq = 2 * std::sqrt(A) * al;
        set(A * ((A + 1) + (A - 1) * cs + sq), -2 * A * ((A - 1) + (A + 1) * cs), A * ((A + 1) + (A - 1) * cs - sq),
            (A + 1) - (A - 1) * cs + sq, 2 * ((A - 1) - (A + 1) * cs), (A + 1) - (A - 1) * cs - sq);
    }
};

}  // namespace vfx
