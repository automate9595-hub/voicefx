#pragma once
#include <atomic>
#include <chrono>
#include <memory>
#include "Params.h"
#include "Effects.h"
#include "PitchDetector.h"
#include "PitchCorrector.h"
#include "Psola.h"

namespace vfx {

enum Meter : int {
    M_IN_PEAK = 0, M_IN_RMS, M_OUT_PEAK, M_OUT_RMS, M_COMP_GR, M_F0, M_MEAS_MIDI, M_TARGET_NOTE, M_OUT_MIDI,
    M_VOICED, M_GATE_OPEN, M_CPU, M_LATENCY_MS, M_CONF, M_CENTS, M_DEESS_GR, METER_COUNT
};

// Cadena completa de voz (mono). Sin asignaciones ni bloqueos dentro de process().
class VoiceEngine {
public:
    static constexpr int kChunk = 64;

    VoiceEngine() { for (int i = 0; i < PARAM_COUNT; i++) params[i].store(kParamInfo[i].def); for (auto& m : meters) m.store(0.f); }

    void init(double sampleRate) {
        fs = sampleRate;
        an.init(fs); det.init(fs); pc.init(fs);
        for (auto& v : voices) v.init(fs);
        for (auto& r : running) r = false;
        gate.init(fs); comp.init(fs); deess.init(fs); sat.init(fs); chorus.init(fs); flanger.init(fs);
        phaser.init(fs); vibrato.init(fs); echo.init(fs); reverb.init(fs); limiter.init(fs);
        rumble.highpass(fs, 60.0, 0.7071);
        phoneHp[0].highpass(fs, 380, 0.7071); phoneHp[1].highpass(fs, 380, 0.7071);
        phoneLp[0].lowpass(fs, 3300, 0.7071); phoneLp[1].lowpass(fs, 3300, 0.7071);
        inSm.init(fs, 20, 1.f); outSm.init(fs, 20, 1.f); stageSm.init(fs, 20, 0.f); voicedSm.init(fs, 5, 0.f);
        bypassSm.init(fs, 10, 0.f); mixSm.init(fs, 20, 1.f);
        for (auto& g : vGain) g.init(fs, 8, 0.f);
        lastEq[0] = lastEq[1] = lastEq[2] = -999.f;
        ringPhase = 0; peakIn = peakOut = 0; rmsIn = rmsOut = 0; cpu = 0; ready = true;
    }

    void setParam(int i, float v) {
        if (i < 0 || i >= PARAM_COUNT) return;
        params[i].store(clampf(v, kParamInfo[i].min, kParamInfo[i].max), std::memory_order_relaxed);
    }
    float param(int i) const { return (i < 0 || i >= PARAM_COUNT) ? 0.f : params[i].load(std::memory_order_relaxed); }
    void getMeters(float* out) const { for (int i = 0; i < METER_COUNT; i++) out[i] = meters[i].load(std::memory_order_relaxed); }
    double sampleRate() const { return fs; }
    int stageLatencySamples() const { return an.latencySamples(); }

    // in/out: mono float. Pueden ser el mismo búfer.
    void process(const float* in, float* out, int n) {
        if (!ready) { for (int i = 0; i < n; i++) out[i] = 0.f; return; }
        auto t0 = std::chrono::steady_clock::now();
        int done = 0;
        while (done < n) { int c = std::min(kChunk, n - done); chunk(in + done, out + done, c); done += c; }
        double el = std::chrono::duration<double>(std::chrono::steady_clock::now() - t0).count();
        double load = 100.0 * el / (n / fs);
        cpu += 0.1f * ((float)load - cpu);
        publish();
    }

private:
    struct P {
        float bypass, inGain, outGain, mix, gate, comp, deess, deessF, bass, mid, treble, drive, phone;
        float pitchSemi, formantSemi, atOn, atMode, atKey, atScale, atMask, atSpeed, atHum, atAmt, atFlex, atA4;
        float robotMono, robotMetal, h3, h5, hUp, hDn, dbl, dblDetune;
        float chorus, chorusRate, flanger, flangerRate, flangerFb, phaser, phaserRate, vibrato, vibratoRate;
        float echo, echoTime, echoFb, echoSync, bpm, echoDiv, reverb, reverbSize, reverbDamp, reverbPre;
    };
    void load(P& p) const {
        auto g = [&](int i) { return params[i].load(std::memory_order_relaxed); };
        p.bypass = g(BYPASS); p.inGain = g(IN_GAIN); p.outGain = g(OUT_GAIN); p.mix = g(MIX); p.gate = g(GATE);
        p.comp = g(COMPRESSION); p.deess = g(DEESS); p.deessF = g(DEESS_FREQ); p.bass = g(BASS); p.mid = g(MID);
        p.treble = g(TREBLE); p.drive = g(DRIVE); p.phone = g(PHONE); p.pitchSemi = g(PITCH_SEMI);
        p.formantSemi = g(FORMANT_SEMI); p.atOn = g(AT_ON); p.atMode = g(AT_MODE); p.atKey = g(AT_KEY);
        p.atScale = g(AT_SCALE); p.atMask = g(AT_MASK); p.atSpeed = g(AT_SPEED); p.atHum = g(AT_HUMANIZE);
        p.atAmt = g(AT_AMOUNT); p.atFlex = g(AT_FLEX); p.atA4 = g(AT_A4); p.robotMono = g(ROBOT_MONO);
        p.robotMetal = g(ROBOT_METAL); p.h3 = g(HARM_3); p.h5 = g(HARM_5); p.hUp = g(HARM_OCT_UP); p.hDn = g(HARM_OCT_DN);
        p.dbl = g(DOUBLER); p.dblDetune = g(DOUBLER_DETUNE); p.chorus = g(CHORUS); p.chorusRate = g(CHORUS_RATE);
        p.flanger = g(FLANGER); p.flangerRate = g(FLANGER_RATE); p.flangerFb = g(FLANGER_FB); p.phaser = g(PHASER);
        p.phaserRate = g(PHASER_RATE); p.vibrato = g(VIBRATO); p.vibratoRate = g(VIBRATO_RATE); p.echo = g(ECHO);
        p.echoTime = g(ECHO_TIME); p.echoFb = g(ECHO_FB); p.echoSync = g(ECHO_SYNC); p.bpm = g(BPM); p.echoDiv = g(ECHO_DIV);
        p.reverb = g(REVERB); p.reverbSize = g(REVERB_SIZE); p.reverbDamp = g(REVERB_DAMP); p.reverbPre = g(REVERB_PRE);
    }

    enum { V_MAIN = 0, V_H3, V_H5, V_HUP, V_HDN, V_D1, V_D2, V_COUNT };

    void updateEq(const P& p) {
        float gl = (p.bass - 50.f) * 0.24f, gm = (p.mid - 50.f) * 0.24f, gh = (p.treble - 50.f) * 0.24f;
        if (gl != lastEq[0]) { eqLo.lowShelf(fs, 110.0, gl); lastEq[0] = gl; }
        if (gm != lastEq[1]) { eqMid.peaking(fs, 1400.0, 0.8, gm); lastEq[1] = gm; }
        if (gh != lastEq[2]) { eqHi.highShelf(fs, 7500.0, gh); lastEq[2] = gh; }
    }

    void chunk(const float* in, float* out, int c) {
        P p; load(p); updateEq(p);
        TuneSettings ts;
        ts.mode = (int)p.atMode; ts.key = (int)p.atKey; ts.scale = (int)p.atScale; ts.manualMask = (int)p.atMask;
        ts.speedMs = p.atSpeed; ts.humanize = p.atHum * 0.01f; ts.amount = p.atAmt * 0.01f; ts.flexCents = p.atFlex;
        ts.a4 = p.atA4; ts.pitchSemi = p.pitchSemi; ts.monoLock = p.robotMono * 0.01f;
        ts.correct = p.atOn > 0.5f;
        const bool bypass = p.bypass > 0.5f;
        const bool mainProc = ts.correct || std::fabs(p.pitchSemi) > 0.005f || std::fabs(p.formantSemi) > 0.005f || p.robotMono > 0.5f;
        float lvl[V_COUNT] = {1.f, p.h3 * 0.01f, p.h5 * 0.01f, p.hUp * 0.01f, p.hDn * 0.01f, p.dbl * 0.01f, p.dbl * 0.01f};
        const bool anyVoice = lvl[V_H3] > 0.005f || lvl[V_H5] > 0.005f || lvl[V_HUP] > 0.005f || lvl[V_HDN] > 0.005f || lvl[V_D1] > 0.005f;
        const bool stageOn = (mainProc || anyVoice) && !bypass;

        // 1) entrada: ganancia, filtro de rumble, puerta; alimenta análisis de tono
        float pre[kChunk], raw[kChunk];
        for (int i = 0; i < c; i++) {
            float x = in[i] * inSm.step(dbToLin(p.inGain));
            raw[i] = x;
            float a = std::fabs(x); peakIn = std::max(peakIn * 0.9997f, a); rmsIn += 0.0005f * (x * x - rmsIn);
            x = rumble.process(sanitize(x));
            x = gate.process(x, p.gate);
            pre[i] = x;
            an.write(x);
            if (det.push(x)) {
                const auto& r = det.result();
                pc.update(r, ts);
                an.setPitch(r.hz > 0.f ? (float)(fs / r.hz) : (float)(fs * 0.005), r.voiced);
            }
        }
        an.updateMarks();
        const int64_t endN = an.count(), baseN = endN - c;
        const int D = an.latencySamples();

        // 2) voces PSOLA (principal, armonías, dobles)
        const bool voicedNow = pc.isVoiced();
        double outM = pc.outMidi();
        bool want[V_COUNT] = {stageOn || stageSm.v > 0.002f, false, false, false, false, false, false};
        want[V_MAIN] = stageOn || stageSm.v > 0.002f;
        for (int v = 1; v < V_COUNT; v++) want[v] = want[V_MAIN] && lvl[v] > 0.005f;
        VoiceCtl ctl[V_COUNT];
        double fMain = midiToHz(outM, p.atA4);
        double rho = std::pow(2.0, p.formantSemi / 12.0);
        for (int v = 0; v < V_COUNT; v++) { ctl[v].voiced = voicedNow; ctl[v].formant = rho; ctl[v].extraDelay = 0; ctl[v].fOut = fMain; }
        if (voicedNow) {
            int i3 = pc.harmonyInterval(2), i5 = pc.harmonyInterval(4);
            double iv[5] = {0, (double)i3, (double)i5, 12.0, -12.0};
            for (int v = 1; v <= 4; v++) { ctl[v].fOut = midiToHz(outM + iv[v], p.atA4); ctl[v].formant = rho * std::pow(2.0, iv[v] * 0.25 / 12.0); }
        }
        double det1 = std::pow(2.0, (double)p.dblDetune / 1200.0);
        ctl[V_D1].fOut = fMain * det1;       ctl[V_D1].extraDelay = (int)(fs * 0.017);
        ctl[V_D2].fOut = fMain / det1;       ctl[V_D2].extraDelay = (int)(fs * 0.029);
        for (int v = 0; v < V_COUNT; v++) {
            if (want[v] && !running[v]) { voices[v].reset(baseN); running[v] = true; }
            if (!want[v]) running[v] = false;
            if (running[v]) voices[v].render(an, ctl[v], endN);
        }

        // 3) mezcla de la etapa de tono y cadena posterior
        const float lvlScale[V_COUNT] = {1.f, 0.7f, 0.7f, 0.6f, 0.6f, 0.75f, 0.75f};
        const float wet = p.mix * 0.01f;
        const float delayMs = (float)(60000.0 / p.bpm);
        const float divMul[4] = {1.f, 0.5f, 0.75f, 0.25f};  // 1/4, 1/8, 1/8 con puntillo, 1/16 (en pulsos)
        float echoMs = p.echoSync > 0.5f ? delayMs * divMul[(int)clampf(p.echoDiv, 0.f, 3.f)] : p.echoTime;
        for (int i = 0; i < c; i++) {
            const int64_t m = baseN + i;
            float del = an.at(m - D);
            float vm = voicedSm.step(voicedNow ? 1.f : 0.f);
            float mainV = running[V_MAIN] ? voices[V_MAIN].pop() : del;
            float mainOut = mainProc ? (vm * mainV + (1.f - vm) * del) : del;
            float stage = mainOut;
            for (int v = 1; v < V_COUNT; v++) {
                float g = vGain[v].step((voicedNow && want[v]) ? lvl[v] * lvlScale[v] : 0.f);
                if (running[v]) { float s = voices[v].pop(); stage += s * g; }
            }
            float sm = stageSm.step(stageOn ? 1.f : 0.f);
            float s = raw_mix(pre[i], stage, sm);
            float dryRef = raw_mix(pre[i], del, sm);

            // robot metálico (modulación en anillo)
            if (p.robotMetal > 0.5f) {
                ringPhase += 110.0 / fs; if (ringPhase >= 1.0) ringPhase -= 1.0;
                float rm = s * (float)std::sin(2 * kPi * ringPhase);
                float k = p.robotMetal * 0.01f; s = s * (1.f - k) + rm * k * 1.4f;
            }
            s = eqHi.process(eqMid.process(eqLo.process(s)));
            if (p.phone > 0.5f) {
                float t = phoneLp[1].process(phoneLp[0].process(phoneHp[1].process(phoneHp[0].process(s))));
                float k = p.phone * 0.01f; s = s * (1.f - k) + std::tanh(t * 1.6f) * 0.8f * k;
            }
            s = deess.process(s, p.deess, p.deessF);
            s = comp.process(s, p.comp);
            s = sat.process(s, p.drive);
            s = chorus.process(s, p.chorus, p.chorusRate);
            s = flanger.process(s, p.flanger, p.flangerRate, p.flangerFb);
            s = phaser.process(s, p.phaser, p.phaserRate);
            s = vibrato.process(s, p.vibrato, p.vibratoRate);
            float ew = echo.process(s, p.echo, echoMs, p.echoFb);
            float rv = reverb.process(s + ew * 0.5f, p.reverb, p.reverbSize, p.reverbDamp, p.reverbPre);
            float fx = s + ew + rv;
            float mw = mixSm.step(wet);
            float y = dryRef * (1.f - mw) + fx * mw;
            y *= outSm.step(dbToLin(p.outGain));
            float bp = bypassSm.step(bypass ? 1.f : 0.f);
            y = y * (1.f - bp) + raw[i] * bp * outSm.v;
            y = limiter.process(sanitize(y));
            out[i] = y;
            float a = std::fabs(y); peakOut = std::max(peakOut * 0.9997f, a); rmsOut += 0.0005f * (y * y - rmsOut);
        }
        stageActiveNow = stageSm.v > 0.5f;
    }
    static inline float raw_mix(float a, float b, float t) { return a + (b - a) * t; }

    void publish() {
        meters[M_IN_PEAK].store(peakIn); meters[M_IN_RMS].store(std::sqrt(std::max(0.f, rmsIn)));
        meters[M_OUT_PEAK].store(peakOut); meters[M_OUT_RMS].store(std::sqrt(std::max(0.f, rmsOut)));
        meters[M_COMP_GR].store(comp.grDbNow()); meters[M_DEESS_GR].store(linToDb(deess.gainNow()));
        const auto& r = det.result();
        meters[M_F0].store(pc.isVoiced() ? r.hz : 0.f); meters[M_CONF].store(r.conf);
        meters[M_MEAS_MIDI].store(pc.isVoiced() ? (float)pc.measuredMidi() : -1.f);
        meters[M_TARGET_NOTE].store(pc.isVoiced() ? (float)pc.targetNote() : -1.f);
        meters[M_OUT_MIDI].store(pc.isVoiced() ? (float)pc.outMidi() : -1.f);
        meters[M_CENTS].store((float)pc.centsOff());
        meters[M_VOICED].store(pc.isVoiced() ? 1.f : 0.f); meters[M_GATE_OPEN].store(gate.isOpen() ? 1.f : 0.f);
        meters[M_CPU].store(cpu);
        meters[M_LATENCY_MS].store(stageActiveNow ? (float)(1000.0 * an.latencySamples() / fs) : 0.f);
    }

    double fs = 48000; bool ready = false, stageActiveNow = false;
    std::atomic<float> params[PARAM_COUNT];
    std::atomic<float> meters[METER_COUNT];
    PsolaAnalyzer an; PitchDetector det; PitchCorrector pc;
    PsolaVoice voices[V_COUNT]; bool running[V_COUNT];
    NoiseGate gate; Compressor comp; DeEsser deess; Saturator sat; Chorus chorus; Flanger flanger; Phaser phaser;
    Vibrato vibrato; Echo echo; Reverb reverb; Limiter limiter;
    Biquad rumble, eqLo, eqMid, eqHi, phoneHp[2], phoneLp[2];
    Smoother inSm, outSm, stageSm, voicedSm, bypassSm, mixSm, vGain[V_COUNT];
    float lastEq[3]; double ringPhase = 0;
    float peakIn = 0, peakOut = 0, rmsIn = 0, rmsOut = 0, cpu = 0;
};

}  // namespace vfx
