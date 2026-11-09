#pragma once
#include <atomic>
#include <memory>
#include <mutex>
#include <thread>
#include <vector>
#include <oboe/Oboe.h>
#include "../dsp/VoiceEngine.h"
#include "WavRecorder.h"

namespace vfx {

// Estado público del motor de audio (lo lee Kotlin por JNI).
enum Status : int {
    S_STATE = 0,        // 0 detenido, 1 activo, 2 error, 3 reiniciando
    S_SAMPLE_RATE, S_BURST, S_BUFFER, S_XRUNS, S_INPUT_SILENT, S_API, S_EXCLUSIVE, S_RESTARTS,
    S_IN_DEVICE, S_OUT_DEVICE, S_SESSION_ID, S_LAST_ERROR, S_LATENCY_OUT_US, S_RECORDING, S_COUNT
};

// Captura y reproducción full-duplex de baja latencia (AAudio/OpenSL a través de Oboe).
class OboeEngine : public oboe::AudioStreamDataCallback,
                   public oboe::AudioStreamErrorCallback,
                   public std::enable_shared_from_this<OboeEngine> {
public:
    VoiceEngine dsp;
    WavRecorder recorder;

    int start(int inDeviceId, int outDeviceId);      // 0 = correcto, otro = código de error de Oboe
    void stop();
    void getStatus(int* out) const;
    void setMonitor(bool on, float volume) { monitorOn.store(on); monitorVol.store(volume); }

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream* stream, void* audioData, int32_t numFrames) override;
    void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) override;

private:
    std::mutex mtx;
    std::shared_ptr<oboe::AudioStream> inStream, outStream;
    std::unique_ptr<oboe::LatencyTuner> tuner;
    std::vector<float> inBuf, scratch;
    std::atomic<int> state{0}, lastError{0}, restarts{0}, silentFlag{0};
    std::atomic<bool> monitorOn{true}, restarting{false}, wantRunning{false};
    std::atomic<float> monitorVol{0.85f};
    int reqIn = 0, reqOut = 0;
    int64_t zeroRun = 0; float monSmooth = 0.f; int callbacks = 0;

    int openStreams(int inId, int outId);
    void closeStreams();
    void restartAsync();
};

}  // namespace vfx
