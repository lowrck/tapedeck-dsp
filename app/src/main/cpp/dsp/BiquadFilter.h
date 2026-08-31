#pragma once

namespace dsp {

enum class FilterType { LowPass, HighPass };

class BiquadFilter {
public:
    void configure(FilterType type, float sampleRate, float cutoffHz, float q);
    float process(float input);
    void reset();

private:
    float b0_ = 1.0f, b1_ = 0.0f, b2_ = 0.0f, a1_ = 0.0f, a2_ = 0.0f;
    float z1_ = 0.0f, z2_ = 0.0f;
};

} // namespace dsp
