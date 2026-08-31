#include "NoiseGenerator.h"

namespace dsp {

NoiseGenerator::NoiseGenerator(uint32_t seed) : rngState_(seed == 0 ? 1u : seed) {}

void NoiseGenerator::setLevel(float level) { level_ = level; }

float NoiseGenerator::nextWhite() {
    rngState_ = rngState_ * 1664525u + 1013904223u;
    return static_cast<float>(static_cast<int32_t>(rngState_)) / 2147483648.0f;
}

float NoiseGenerator::process() {
    if (level_ <= 0.0f) return 0.0f;

    const float white = nextWhite();
    b0_ = 0.99765f * b0_ + white * 0.0990460f;
    b1_ = 0.96300f * b1_ + white * 0.2965164f;
    b2_ = 0.57000f * b2_ + white * 1.0526913f;
    const float pink = b0_ + b1_ + b2_ + white * 0.1848f;

    return pink * 0.03f * level_;
}

} // namespace dsp
