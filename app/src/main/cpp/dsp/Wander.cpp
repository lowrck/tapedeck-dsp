#include "Wander.h"

#include <algorithm>

namespace dsp {

Wander::Wander(uint32_t seed) : rngState_(seed == 0 ? 1u : seed) {}

void Wander::prepare(float sampleRate) {
    sampleRate_ = sampleRate;
    stepsRemaining_ = 0;
}

float Wander::nextRandom01() {
    rngState_ = rngState_ * 1664525u + 1013904223u;
    return static_cast<float>(rngState_) / 4294967295.0f;
}

float Wander::next(int32_t frames) {
    stepsRemaining_ -= frames;
    if (stepsRemaining_ <= 0) {
        target_ = nextRandom01() * 2.0f - 1.0f;
        stepsRemaining_ = static_cast<int32_t>(sampleRate_ * (2.0f + nextRandom01() * 3.0f));
    }

    const float glideRate = std::min(1.0f, static_cast<float>(frames) / (sampleRate_ * 0.5f));
    value_ += (target_ - value_) * glideRate;
    return value_;
}

} // namespace dsp
