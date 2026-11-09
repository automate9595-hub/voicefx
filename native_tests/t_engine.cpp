// Procesa un archivo float32 mono con VoiceEngine. Uso: t_engine in.f32 out.f32 [clave=valor ...]
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>
#include "../app/src/main/cpp/dsp/VoiceEngine.h"
using namespace vfx;
int main(int argc, char** argv) {
    static VoiceEngine eng; eng.init(48000);
    for (int i = 3; i < argc; i++) {
        std::string a = argv[i]; size_t e = a.find('='); if (e == std::string::npos) continue;
        std::string k = a.substr(0, e); float v = (float)atof(a.c_str() + e + 1);
        bool found = false; for (int p = 0; p < PARAM_COUNT; p++) if (k == kParamInfo[p].key) { eng.setParam(p, v); found = true; }
        if (!found) { fprintf(stderr, "parámetro desconocido: %s\n", k.c_str()); return 2; }
    }
    FILE* f = fopen(argv[1], "rb"); std::vector<float> in; float buf[4096]; size_t r;
    while ((r = fread(buf, 4, 4096, f)) > 0) in.insert(in.end(), buf, buf + r);
    fclose(f);
    std::vector<float> out(in.size());
    size_t blk = 192;  // simula callbacks de audio de 192 frames
    for (size_t b = 0; b < in.size(); b += blk) eng.process(in.data() + b, out.data() + b, (int)std::min(blk, in.size() - b));
    FILE* o = fopen(argv[2], "wb"); fwrite(out.data(), 4, out.size(), o); fclose(o);
    float m[METER_COUNT]; eng.getMeters(m);
    printf("cpu=%.2f%% latency=%.1fms inPeak=%.3f outPeak=%.3f f0=%.1f\n", m[M_CPU], m[M_LATENCY_MS], m[M_IN_PEAK], m[M_OUT_PEAK], m[M_F0]);
    return 0;
}
