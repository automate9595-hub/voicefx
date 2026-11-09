// Arnés de prueba: lee float32 mono (stdin o archivo), aplica detector+corrector+PSOLA, escribe float32.
#include <cstdio>
#include <cstdlib>
#include <vector>
#include <string>
#include "../app/src/main/cpp/dsp/PitchCorrector.h"
#include "../app/src/main/cpp/dsp/Psola.h"
using namespace vfx;
int main(int argc, char** argv) {
    const double fs = 48000;
    TuneSettings s; s.correct = true;
    int key = 0, scale = 1; double speed = 40, hum = 0.5, semi = 0, formant = 0;
    for (int i = 3; i < argc; i++) {
        std::string a = argv[i]; auto v = [&](const char* k, double& o) { std::string p = std::string(k) + "="; if (a.rfind(p, 0) == 0) o = atof(a.c_str() + p.size()); };
        double t = 0; v("speed", speed); v("hum", hum); v("semi", semi); v("formant", formant);
        t = key; v("key", t); key = (int)t; t = scale; v("scale", t); scale = (int)t;
        t = s.correct; v("correct", t); s.correct = t > 0.5;
    }
    s.key = key; s.scale = scale; s.speedMs = (float)speed; s.humanize = (float)hum; s.pitchSemi = (float)semi;
    FILE* f = fopen(argv[1], "rb"); std::vector<float> in; float buf[4096]; size_t r;
    while ((r = fread(buf, 4, 4096, f)) > 0) in.insert(in.end(), buf, buf + r); fclose(f);
    PitchDetector det; det.init(fs); PsolaAnalyzer an; an.init(fs); PitchCorrector pc; pc.init(fs); PsolaVoice v; v.init(fs); v.reset(0);
    std::vector<float> out(in.size()), f0(in.size() / 256 + 2), tgt(in.size() / 256 + 2);
    VoiceCtl ctl; ctl.formant = std::pow(2.0, formant / 12.0);
    size_t fi = 0;
    for (size_t base = 0; base < in.size(); base += 64) {
        size_t nb = std::min<size_t>(64, in.size() - base);
        for (size_t i = 0; i < nb; i++) {
            an.write(in[base + i]);
            if (det.push(in[base + i])) {
                const auto& r2 = det.result();
                pc.update(r2, s);
                an.setPitch(r2.hz > 0 ? (float)(fs / r2.hz) : 240.f, r2.voiced);
                if (fi < f0.size()) { f0[fi] = r2.voiced ? r2.hz : 0.f; tgt[fi] = pc.isVoiced() ? (float)midiToHz(pc.outMidi(), 440.0) : 0.f; fi++; }
            }
        }
        an.updateMarks();
        ctl.voiced = pc.isVoiced(); ctl.fOut = midiToHz(pc.outMidi(), 440.0);
        v.render(an, ctl, an.count());
        for (size_t i = 0; i < nb; i++) {
            float ps = v.pop();
            int64_t m = base + i; float dry = an.at(m - an.latencySamples());
            out[base + i] = ctl.voiced ? ps : dry;
        }
    }
    FILE* o = fopen(argv[2], "wb"); fwrite(out.data(), 4, out.size(), o); fclose(o);
    std::string p2 = std::string(argv[2]) + ".f0"; o = fopen(p2.c_str(), "wb"); fwrite(f0.data(), 4, fi, o); fwrite(tgt.data(), 4, fi, o); fclose(o);
    printf("%zu %zu\n", in.size(), fi);
    return 0;
}
