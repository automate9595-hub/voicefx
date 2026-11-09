#include <cstdio>
#include <cmath>
#include <vector>
#include "../app/src/main/cpp/audio/WavRecorder.h"
int main() {
    vfx::WavRecorder r; if (!r.start("/tmp/rec_test.wav", 48000)) { printf("FALLO: no abre\n"); return 1; }
    std::vector<float> b(480); double ph = 0;
    for (int k = 0; k < 500; k++) { for (auto& s : b) { ph += 2 * M_PI * 440 / 48000; s = 0.5f * (float)std::sin(ph); } r.push(b.data(), 480); if (k % 50 == 0) std::this_thread::sleep_for(std::chrono::milliseconds(5)); }
    long bytes = (long)r.stop();
    FILE* f = fopen("/tmp/rec_test.wav", "rb"); fseek(f, 0, SEEK_END); long sz = ftell(f); fclose(f);
    printf("bytes de audio=%ld, tamaño de archivo=%ld, descartadas=%ld\n", bytes, sz, (long)r.droppedSamples());
    if (bytes != 500 * 480 * 2 || sz != bytes + 44 || r.droppedSamples() != 0) { printf("FALLO\n"); return 1; }
    printf("OK\n"); return 0;
}
