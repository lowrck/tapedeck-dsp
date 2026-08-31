#include "DropoutSimulator.h"

#include <algorithm>

namespace dsp {

DropoutSimulator::DropoutSimulator(uint32_t seed) : rngState_(seed == 0 ? 1u : seed) {}

void DropoutSimulator::prepare(float sampleRate) { sampleRate_ = sampleRate; }

void DropoutSimulator::setAmount(float amount01) { amount_ = amount01; }

float DropoutSimulator::nextRandom01() {
    rngState_ = rngState_ * 1664525u + 1013904223u;
    return static_cast<float>(rngState_) / 4294967295.0f;
}

float DropoutSimulator::nextGain() {
    if (amount_ <= 0.0f) {
        envelope_ = 1.0f;
        dipping_ = false;
        return 1.0f;
    }

    if (!dipping_) {
        const float probabilityPerSample = amount_ * 0.00006f;
        if (nextRandom01() < probabilityPerSample) {
            dipping_ = true;
            remainingSamples_ = static_cast<int>(sampleRate_ * (0.02f + nextRandom01() * 0.08f));
        }
    }

    if (dipping_) {
        const float target = 1.0f - amount_ * (0.6f + nextRandom01() * 0.4f);
        envelope_ += (target - envelope_) * 0.05f;
        if (--remainingSamples_ <= 0) dipping_ = false;
    } else {
        envelope_ += (1.0f - envelope_) * 0.02f;
    }

    return std::max(0.0f, envelope_);
}

} // namespace dsp
