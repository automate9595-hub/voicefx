// Prueba de estrés del motor: parámetros aleatorios en cada bloque, señales extremas, varios sample rates y tamaños de bloque.
#include <cstdio>
#include <cstdlib>
#include <random>
#include <vector>
#include "../app/src/main/cpp/dsp/VoiceEngine.h"
using namespace vfx;

static int fails = 0;
#define CHECK(cond, msg) do { if (!(cond)) { printf("FALLO: %s\n", msg); fails++; } } while (0)

static void run(double fs, int block, int seconds, unsigned seed, bool randomParams) {
    static VoiceEngine eng; eng.init(fs);
    std::mt19937 rng(seed); std::uniform_real_distribution<float> u(0.f, 1.f);
    std::vector<float> in(block), out(block);
    long total = (long)(fs * seconds); double ph = 0; long bad = 0; float maxAbs = 0; double cpuMax = 0, cpuSum = 0; long calls = 0;
    int mode = 0;
    for (long pos = 0; pos < total; pos += block, calls++) {
        if (randomParams && calls % 8 == 0)
            for (int p = 0; p < PARAM_COUNT; p++) {
                float r = u(rng);
                if (r < 0.15f) eng.setParam(p, kParamInfo[p].min);
                else if (r < 0.30f) eng.setParam(p, kParamInfo[p].max);
                else if (r < 0.60f) eng.setParam(p, kParamInfo[p].min + u(rng) * (kParamInfo[p].max - kParamInfo[p].min));
            }
        if (calls % 300 == 0) mode = (int)(u(rng) * 6);
        float f0 = 100.f + 400.f * (0.5f + 0.5f * std::sin((float)pos / (float)fs));
        for (int i = 0; i < block; i++) {
            float x = 0.f;
            switch (mode) {
                case 0: ph += 2 * kPi * f0 / fs; x = 0.4f * (float)std::sin(ph) + 0.2f * (float)std::sin(2 * ph) + 0.1f * (float)std::sin(3 * ph); break;
                case 1: x = 0.3f * (u(rng) * 2.f - 1.f); break;
                case 2: x = 0.f; break;
                case 3: ph += 2 * kPi * f0 / fs; x = 3.0f * (float)std::sin(ph); break;           // saturación de entrada (>0 dBFS)
                case 4: x = (calls % 2) ? 1.f : -1.f; break;                                          // ondas cuadradas extremas
                default: ph += 2 * kPi * 180.0 / fs; x = 0.02f * (float)std::sin(ph) + 1e-9f * (u(rng) - 0.5f); break; // señal muy baja
            }
            in[i] = x;
        }
        auto t0 = std::chrono::steady_clock::now();
        eng.process(in.data(), out.data(), block);
        double el = std::chrono::duration<double>(std::chrono::steady_clock::now() - t0).count() / (block / fs);
        cpuMax = std::max(cpuMax, el); cpuSum += el;
        for (int i = 0; i < block; i++) { if (!std::isfinite(out[i])) bad++; else maxAbs = std::max(maxAbs, std::fabs(out[i])); }
    }
    float m[METER_COUNT]; eng.getMeters(m);
    bool finiteMeters = true; for (int i = 0; i < METER_COUNT; i++) if (!std::isfinite(m[i])) finiteMeters = false;
    printf("fs=%6.0f bloque=%4d %s: muestras no finitas=%ld, máx |y|=%.3f, CPU media %.2f%% (pico de bloque %.1f%%)\n", fs, block, randomParams ? "parámetros aleatorios" : "parámetros por defecto  ", bad, maxAbs, 100 * cpuSum / calls, 100 * cpuMax);
    CHECK(bad == 0, "salida con NaN/Inf"); CHECK(maxAbs <= 1.0f, "salida fuera de rango"); CHECK(finiteMeters, "medidores no finitos");
}
int main() {
    for (double fs : {48000.0, 44100.0, 16000.0}) for (int blk : {64, 192, 240, 480, 1024}) run(fs, blk, 20, 7 + blk, true);
    run(48000, 192, 20, 1, false);
    // Todos los efectos al máximo a la vez (peor caso de CPU)
    { static VoiceEngine e; e.init(48000);
      for (int p = 0; p < PARAM_COUNT; p++) e.setParam(p, kParamInfo[p].max); e.setParam(BYPASS, 0); e.setParam(AT_SPEED, 20);
      std::vector<float> in(192), out(192); double ph = 0, tot = 0, worst = 0; int n = 0;
      for (int k = 0; k < 48000 * 10 / 192; k++) { for (auto& s : in) { ph += 2 * kPi * 220.0 / 48000; s = 0.4f * (float)std::sin(ph); }
        auto t0 = std::chrono::steady_clock::now(); e.process(in.data(), out.data(), 192);
        double el = std::chrono::duration<double>(std::chrono::steady_clock::now() - t0).count() / (192 / 48000.0); tot += el; worst = std::max(worst, el); n++; }
      printf("PEOR CASO (todos los efectos y todas las voces al máximo): CPU media %.2f%%, pico de bloque %.1f%%\n", 100 * tot / n, 100 * worst);
      CHECK(100 * tot / n < 25.0, "CPU media demasiado alta"); }
    printf(fails ? "RESULTADO: %d FALLOS\n" : "RESULTADO: todas las pruebas de estrés correctas\n", fails);
    return fails ? 1 : 0;
}
