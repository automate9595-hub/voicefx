#include "OboeEngine.h"
#include <android/log.h>
#include <cmath>
#include <cstring>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "VoiceFX", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "VoiceFX", __VA_ARGS__)

namespace vfx {

int OboeEngine::openStreams(int inId, int outId) {
    oboe::AudioStreamBuilder ob;
    ob.setDirection(oboe::Direction::Output)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Exclusive)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(oboe::ChannelCount::Mono)
        ->setUsage(oboe::Usage::Media)
        ->setContentType(oboe::ContentType::Music)
        ->setDeviceId(outId)
        ->setDataCallback(shared_from_this())
        ->setErrorCallback(shared_from_this());
    oboe::Result r = ob.openStream(outStream);
    if (r != oboe::Result::OK) { LOGE("Salida: %s", oboe::convertToText(r)); return (int)r; }

    const int sr = outStream->getSampleRate();
    // Entrada: preferimos UNPROCESSED (sin AGC/NS/eco del sistema); si el dispositivo no lo admite, probamos otras.
    const oboe::InputPreset presets[3] = {oboe::InputPreset::Unprocessed, oboe::InputPreset::VoiceRecognition, oboe::InputPreset::Generic};
    r = oboe::Result::ErrorInternal;
    for (int i = 0; i < 3 && r != oboe::Result::OK; i++) {
        oboe::AudioStreamBuilder ib;
        ib.setDirection(oboe::Direction::Input)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(oboe::ChannelCount::Mono)
            ->setSampleRate(sr)
            ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
            ->setInputPreset(presets[i])
            ->setDeviceId(inId);
        r = ib.openStream(inStream);
    }
    if (r != oboe::Result::OK) { LOGE("Entrada: %s", oboe::convertToText(r)); outStream->close(); outStream.reset(); return (int)r; }

    const int burst = std::max(32, outStream->getFramesPerBurst());
    outStream->setBufferSizeInFrames(burst * 2);
    tuner = std::make_unique<oboe::LatencyTuner>(*outStream);
    inBuf.assign((size_t)std::max(4096, burst * 16), 0.f);
    scratch.assign(inBuf.size(), 0.f);
    dsp.init((double)sr);
    zeroRun = 0; silentFlag = 0; monSmooth = 0.f; callbacks = 0;

    r = inStream->requestStart();
    if (r != oboe::Result::OK) { closeStreams(); return (int)r; }
    r = outStream->requestStart();
    if (r != oboe::Result::OK) { closeStreams(); return (int)r; }
    LOGI("Audio abierto: %d Hz, ráfaga %d, API %s", sr, burst, oboe::convertToText(outStream->getAudioApi()));
    return 0;
}

void OboeEngine::closeStreams() {
    if (outStream) { outStream->stop(); outStream->close(); }
    if (inStream) { inStream->stop(); inStream->close(); }
    tuner.reset(); outStream.reset(); inStream.reset();
}

int OboeEngine::start(int inDeviceId, int outDeviceId) {
    std::lock_guard<std::mutex> lock(mtx);
    wantRunning = true; reqIn = inDeviceId; reqOut = outDeviceId;
    closeStreams();
    int r = openStreams(inDeviceId, outDeviceId);
    if (r != 0 && (inDeviceId != 0 || outDeviceId != 0)) {   // el dispositivo elegido ya no está: probar los del sistema
        closeStreams(); r = openStreams(0, 0);
    }
    lastError = r; state = r == 0 ? 1 : 2;
    if (r != 0) wantRunning = false;
    return r;
}

void OboeEngine::stop() {
    std::lock_guard<std::mutex> lock(mtx);
    wantRunning = false; recorder.stop(); closeStreams(); state = 0;
}

oboe::DataCallbackResult OboeEngine::onAudioReady(oboe::AudioStream*, void* audioData, int32_t numFrames) {
    float* out = static_cast<float*>(audioData);
    auto in = inStream;   // copia local del shared_ptr
    if (!in || numFrames <= 0) { std::memset(out, 0, sizeof(float) * (size_t)std::max(0, numFrames)); return oboe::DataCallbackResult::Continue; }
    int32_t done = 0;
    while (done < numFrames) {
        int32_t n = std::min<int32_t>(numFrames - done, (int32_t)inBuf.size());
        auto r = in->read(inBuf.data(), n, 0);
        int32_t got = r ? r.value() : 0;
        if (got < n) std::memset(inBuf.data() + got, 0, sizeof(float) * (size_t)(n - got));
        // Si la entrada acumuló retraso (p. ej. tras un corte), descartar el exceso para no aumentar la latencia.
        int64_t backlog = in->getFramesWritten() - in->getFramesRead();
        if (backlog > 4 * (int64_t)numFrames) {
            int64_t drop = backlog - 2 * (int64_t)numFrames;
            while (drop > 0) { int32_t k = (int32_t)std::min<int64_t>(drop, (int64_t)scratch.size()); auto rr = in->read(scratch.data(), k, 0); if (!rr || rr.value() <= 0) break; drop -= rr.value(); }
        }
        // Detección de silenciamiento: ceros digitales exactos durante >1,2 s = el sistema silenció/bloqueó el micrófono.
        bool allZero = true; for (int32_t i = 0; i < n; i++) if (inBuf[(size_t)i] != 0.f) { allZero = false; break; }
        zeroRun = allZero ? zeroRun + n : 0;
        silentFlag.store(zeroRun > (int64_t)(1.2 * dsp.sampleRate()) ? 1 : 0, std::memory_order_relaxed);
        dsp.process(inBuf.data(), out + done, n);
        done += n;
    }
    recorder.push(out, numFrames);
    // Monitoreo: el motor sigue procesando (medidores, grabación) aunque la escucha esté apagada.
    float target = monitorOn.load(std::memory_order_relaxed) ? monitorVol.load(std::memory_order_relaxed) : 0.f;
    for (int32_t i = 0; i < numFrames; i++) { monSmooth += 0.002f * (target - monSmooth); out[i] *= monSmooth; }
    if (tuner && (++callbacks & 7) == 0) tuner->tune();
    return oboe::DataCallbackResult::Continue;
}

void OboeEngine::onErrorAfterClose(oboe::AudioStream*, oboe::Result error) {
    LOGE("Error de audio: %s", oboe::convertToText(error));
    lastError = (int)error;
    if (wantRunning.load()) restartAsync();   // desconexión de auriculares/USB/Bluetooth, cambio de ruta, etc.
}

void OboeEngine::restartAsync() {
    bool expected = false;
    if (!restarting.compare_exchange_strong(expected, true)) return;
    state = 3;
    auto self = shared_from_this();
    std::thread([self] {
        for (int attempt = 0; attempt < 5 && self->wantRunning.load(); attempt++) {
            std::this_thread::sleep_for(std::chrono::milliseconds(150 + 250 * attempt));
            std::lock_guard<std::mutex> lock(self->mtx);
            self->closeStreams();
            int r = self->openStreams(attempt < 2 ? self->reqIn : 0, attempt < 2 ? self->reqOut : 0);
            if (r == 0) { self->state = 1; self->lastError = 0; self->restarts++; self->restarting = false; return; }
        }
        self->state = 2; self->restarting = false;
    }).detach();
}

void OboeEngine::getStatus(int* o) const {
    for (int i = 0; i < S_COUNT; i++) o[i] = 0;
    o[S_STATE] = state.load(); o[S_LAST_ERROR] = lastError.load(); o[S_RESTARTS] = restarts.load();
    o[S_INPUT_SILENT] = silentFlag.load(); o[S_RECORDING] = recorder.isRecording() ? 1 : 0;
    auto out = outStream; auto in = inStream;
    if (out && state.load() == 1) {
        o[S_SAMPLE_RATE] = out->getSampleRate(); o[S_BURST] = out->getFramesPerBurst(); o[S_BUFFER] = out->getBufferSizeInFrames();
        auto x = out->getXRunCount(); o[S_XRUNS] = x ? x.value() : 0;
        o[S_API] = out->getAudioApi() == oboe::AudioApi::AAudio ? 1 : 2;
        o[S_EXCLUSIVE] = out->getSharingMode() == oboe::SharingMode::Exclusive ? 1 : 0;
        o[S_OUT_DEVICE] = out->getDeviceId();
        auto lat = out->calculateLatencyMillis(); o[S_LATENCY_OUT_US] = lat ? (int)(lat.value() * 1000.0) : -1;
    }
    if (in && state.load() == 1) { o[S_IN_DEVICE] = in->getDeviceId(); o[S_SESSION_ID] = (int)in->getSessionId(); }
}

}  // namespace vfx
