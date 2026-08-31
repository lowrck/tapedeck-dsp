#pragma once

#include <cstdint>

namespace dsp {

// Emulates momentary volume dips caused by damaged/oxide-shed patches of
// magnetic tape losing contact with the playback head.
class DropoutSimulator {
public:
    explicit DropoutSimulator(uint32_t seed);
    void prepare(float sampleRate);
    void setAmount(float amount01);
    float nextGain();

private:
    float nextRandom01();

    uint32_t rngState_;
    float sampleRate_ = 48000.0f;
    float amount_ = 0.0f;
    float envelope_ = 1.0f;
    int remainingSamples_ = 0;
    bool dipping_ = false;
};

} // namespace dsp
