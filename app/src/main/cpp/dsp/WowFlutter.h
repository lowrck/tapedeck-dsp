#pragma once

#include <vector>

namespace dsp {

// Simulates tape transport pitch instability by reading a delayed copy of the
// signal through a fractional delay line whose offset is modulated by two
// independent LFOs: a slow "wow" (warped tape / uneven motor) and a fast
// "flutter" (capstan/pinch-roller vibration).
class WowFlutter {
public:
    void prepare(float sampleRate, float phaseOffset = 0.0f);
    void setWow(float rateHz, float depthMs);
    void setFlutter(float rateHz, float depthMs);
    float process(float input);
    void reset();

private:
    float sampleRate_ = 48000.0f;
    std::vector<float> buffer_;
    int writePos_ = 0;

    float wowRate_ = 0.8f;
    float wowDepthMs_ = 0.0f;
    float flutterRate_ = 20.0f;
    float flutterDepthMs_ = 0.0f;

    float wowPhase_ = 0.0f;
    float flutterPhase_ = 0.0f;
    float phaseOffset_ = 0.0f;

    static constexpr float kMaxDelayMs = 30.0f;
};

} // namespace dsp
