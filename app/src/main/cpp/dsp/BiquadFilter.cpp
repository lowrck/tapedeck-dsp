#include "BiquadFilter.h"

#include <cmath>

namespace dsp {

void BiquadFilter::configure(FilterType type, float sampleRate, float cutoffHz, float q) {
    const float omega = 2.0f * static_cast<float>(M_PI) * cutoffHz / sampleRate;
    const float sinOmega = std::sin(omega);
    const float cosOmega = std::cos(omega);
    const float alpha = sinOmega / (2.0f * q);

    float a0;
    if (type == FilterType::LowPass) {
        b0_ = (1.0f - cosOmega) / 2.0f;
        b1_ = 1.0f - cosOmega;
        b2_ = (1.0f - cosOmega) / 2.0f;
        a0 = 1.0f + alpha;
        a1_ = -2.0f * cosOmega;
        a2_ = 1.0f - alpha;
    } else {
        b0_ = (1.0f + cosOmega) / 2.0f;
        b1_ = -(1.0f + cosOmega);
        b2_ = (1.0f + cosOmega) / 2.0f;
        a0 = 1.0f + alpha;
        a1_ = -2.0f * cosOmega;
        a2_ = 1.0f - alpha;
    }

    b0_ /= a0;
    b1_ /= a0;
    b2_ /= a0;
    a1_ /= a0;
    a2_ /= a0;
}

float BiquadFilter::process(float input) {
    const float output = b0_ * input + z1_;
    z1_ = b1_ * input - a1_ * output + z2_;
    z2_ = b2_ * input - a2_ * output;
    return output;
}

void BiquadFilter::reset() {
    z1_ = 0.0f;
    z2_ = 0.0f;
}

} // namespace dsp
