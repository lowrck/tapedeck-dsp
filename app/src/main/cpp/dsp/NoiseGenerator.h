#pragma once

#include <cstdint>

namespace dsp {

// Paul Kellet's "economy" pink-noise filter driven by an xorshift-style LCG,
// used to simulate tape hiss.
class NoiseGenerator {
public:
    explicit NoiseGenerator(uint32_t seed);
    void setLevel(float level);
    float process();

private:
    float nextWhite();

    uint32_t rngState_;
    float level_ = 0.0f;
    float b0_ = 0.0f, b1_ = 0.0f, b2_ = 0.0f;
};

} // namespace dsp
