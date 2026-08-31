#include "WowFlutter.h"

#include <algorithm>
#include <cmath>

namespace dsp {

namespace {
constexpr float kTwoPi = 2.0f * static_cast<float>(M_PI);
}

void WowFlutter::prepare(float sampleRate, float phaseOffset) {
    sampleRate_ = sampleRate;
    phaseOffset_ = phaseOffset;
    wowPhase_ = phaseOffset;
    flutterPhase_ = phaseOffset;
    const int bufferSize = static_cast<int>(sampleRate_ * (kMaxDelayMs / 1000.0f)) + 8;
    buffer_.assign(static_cast<size_t>(bufferSize), 0.0f);
    writePos_ = 0;
}

void WowFlutter::setWow(float rateHz, float depthMs) {
    wowRate_ = rateHz;
    wowDepthMs_ = depthMs;
}

void WowFlutter::setFlutter(float rateHz, float depthMs) {
    flutterRate_ = rateHz;
    flutterDepthMs_ = depthMs;
}

float WowFlutter::process(float input) {
    const int bufferSize = static_cast<int>(buffer_.size());
    buffer_[static_cast<size_t>(writePos_)] = input;

    const float wow = std::sin(wowPhase_) * wowDepthMs_;
    const float flutter = std::sin(flutterPhase_) * flutterDepthMs_;
    const float baseDelayMs = kMaxDelayMs / 2.0f;
    const float delayMs = baseDelayMs + wow + flutter;
    const float delaySamples = (delayMs / 1000.0f) * sampleRate_;

    float readPos = static_cast<float>(writePos_) - delaySamples;
    while (readPos < 0.0f) readPos += static_cast<float>(bufferSize);

    const int readIndex0 = static_cast<int>(readPos) % bufferSize;
    const int readIndex1 = (readIndex0 + 1) % bufferSize;
    const float frac = readPos - std::floor(readPos);
    const float output = buffer_[static_cast<size_t>(readIndex0)] * (1.0f - frac) +
                          buffer_[static_cast<size_t>(readIndex1)] * frac;

    writePos_ = (writePos_ + 1) % bufferSize;

    wowPhase_ += kTwoPi * wowRate_ / sampleRate_;
    if (wowPhase_ > kTwoPi) wowPhase_ -= kTwoPi;
    flutterPhase_ += kTwoPi * flutterRate_ / sampleRate_;
    if (flutterPhase_ > kTwoPi) flutterPhase_ -= kTwoPi;

    return output;
}

void WowFlutter::reset() {
    std::fill(buffer_.begin(), buffer_.end(), 0.0f);
    wowPhase_ = phaseOffset_;
    flutterPhase_ = phaseOffset_;
    writePos_ = 0;
}

} // namespace dsp
