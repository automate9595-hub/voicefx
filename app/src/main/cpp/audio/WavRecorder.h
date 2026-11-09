#pragma once
#include <atomic>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <thread>
#include <vector>
#include <chrono>

namespace vfx {

// Grabador WAV PCM16 mono. push() es seguro para el hilo de audio (cola SPSC sin bloqueos);
// un hilo aparte escribe al disco.
class WavRecorder {
public:
    ~WavRecorder() { stop(); }
    bool start(const char* path, int sampleRate) {
        if (running.load()) return false;
        file = std::fopen(path, "wb");
        if (!file) return false;
        sr = sampleRate; written = 0; dropped = 0; head = tail = 0;
        ring.assign(kRing, 0);
        writeHeader(0);
        running.store(true);
        worker = std::thread([this] { loop(); });
        return true;
    }
    inline void push(const float* x, int n) {
        if (!running.load(std::memory_order_relaxed)) return;
        uint32_t h = head.load(std::memory_order_relaxed), t = tail.load(std::memory_order_acquire);
        for (int i = 0; i < n; i++) {
            if (h - t >= kRing) { dropped.fetch_add(n - i); break; }
            float v = x[i] > 1.f ? 1.f : (x[i] < -1.f ? -1.f : x[i]);
            ring[h & (kRing - 1)] = (int16_t)(v * 32767.f); ++h;
        }
        head.store(h, std::memory_order_release);
    }
    // Devuelve los bytes de audio escritos (0 si no estaba grabando).
    int64_t stop() {
        if (!running.exchange(false)) return 0;
        if (worker.joinable()) worker.join();
        drain();
        writeHeader((uint32_t)(written * 2));
        std::fclose(file); file = nullptr;
        return written * 2;
    }
    bool isRecording() const { return running.load(); }
    double seconds() const { return sr > 0 ? (double)written / sr : 0.0; }
    int64_t droppedSamples() const { return dropped.load(); }

private:
    static constexpr uint32_t kRing = 1u << 18;
    std::FILE* file = nullptr; int sr = 48000; std::vector<int16_t> ring;
    std::atomic<uint32_t> head{0}, tail{0}; std::atomic<bool> running{false}; std::atomic<int64_t> dropped{0};
    std::atomic<int64_t> written{0}; std::thread worker;

    void drain() {
        uint32_t t = tail.load(std::memory_order_relaxed), h = head.load(std::memory_order_acquire);
        while (t != h) {
            int16_t tmp[1024]; int k = 0;
            while (t != h && k < 1024) tmp[k++] = ring[t++ & (kRing - 1)];
            std::fwrite(tmp, 2, (size_t)k, file); written += k;
        }
        tail.store(t, std::memory_order_release);
    }
    void loop() { while (running.load()) { drain(); std::this_thread::sleep_for(std::chrono::milliseconds(20)); } }
    void writeHeader(uint32_t dataBytes) {
        uint8_t h[44]; std::memcpy(h, "RIFF", 4); uint32_t v = 36 + dataBytes; std::memcpy(h + 4, &v, 4);
        std::memcpy(h + 8, "WAVEfmt ", 8); v = 16; std::memcpy(h + 16, &v, 4);
        uint16_t fmt = 1, ch = 1, bits = 16, ba = 2; std::memcpy(h + 20, &fmt, 2); std::memcpy(h + 22, &ch, 2);
        uint32_t s = (uint32_t)sr, br = s * 2; std::memcpy(h + 24, &s, 4); std::memcpy(h + 28, &br, 4);
        std::memcpy(h + 32, &ba, 2); std::memcpy(h + 34, &bits, 2); std::memcpy(h + 36, "data", 4);
        std::memcpy(h + 40, &dataBytes, 4);
        std::fseek(file, 0, SEEK_SET); std::fwrite(h, 1, 44, file); std::fseek(file, 0, SEEK_END);
    }
};

}  // namespace vfx
