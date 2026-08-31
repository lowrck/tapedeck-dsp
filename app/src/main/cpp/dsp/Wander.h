#pragma once

#include <cstdint>

namespace dsp {

// Smoothly wanders between random targets in [-1, 1], picking a new target
// every 2-5 seconds and easing towards it over ~0.5s. Used to give otherwise
// static parameters (hiss level, tape-age intensity) a subtle organic drift
// instead of a perfectly steady value - real tape varies over time too.
class Wander {
public:
    explicit Wander(uint32_t seed);
    void prepare(float sampleRate);
    float next(int32_t frames);

private:
    float nextRandom01();

    uint32_t rngState_;
    float sampleRate_ = 48000.0f;
    float value_ = 0.0f;
    float target_ = 0.0f;
    int32_t stepsRemaining_ = 0;
};

} // namespace dsp
