#pragma once

#include <oboe/Oboe.h>

#include <atomic>
#include <memory>
#include <vector>

#include "dsp/BiquadFilter.h"
#include "dsp/DropoutSimulator.h"
#include "dsp/NoiseGenerator.h"
#include "dsp/Saturator.h"
#include "dsp/Wander.h"
#include "dsp/WowFlutter.h"

class TapeEngine : public oboe::AudioStreamCallback {
public:
    TapeEngine() = default;
    ~TapeEngine() override;

    bool loadAudio(const float *interleaved, int64_t frameCount, int channelCount, int32_t sampleRate);
    void play();
    void pause();
    void stop();
    void seekToFrame(int64_t frame);
    int64_t getPositionFrames() const;
    int64_t getDurationFrames() const;
    bool isPlaying() const;

    void setTapeAge(float value01);
    void setDustDirt(float value01);
    void setTapeType(int type);

    float getVuLeft() const;
    float getVuRight() const;

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *stream, void *audioData, int32_t numFrames) override;
    void onErrorAfterClose(oboe::AudioStream *stream, oboe::Result error) override;

private:
    void openStream(int32_t sampleRate, int32_t channelCount);
    void applyTapeTypeCurve(int32_t numFrames);
    float processChannel(float rawSample, int channel);

    std::shared_ptr<oboe::AudioStream> stream_;

    std::vector<float> pcmData_;
    std::atomic<int64_t> playheadFrames_{0};
    std::atomic<int64_t> totalFrames_{0};
    std::atomic<bool> playing_{false};
    int channelCount_ = 2;
    int32_t engineSampleRate_ = 48000;

    dsp::BiquadFilter highPass_[2];
    dsp::BiquadFilter lowPass_[2];
    dsp::Saturator saturator_[2];
    dsp::WowFlutter wowFlutter_[2];
    dsp::NoiseGenerator noise_[2]{dsp::NoiseGenerator(1111u), dsp::NoiseGenerator(2222u)};
    dsp::DropoutSimulator dropout_{3333u};
    dsp::Wander dirtWander_{4242u};
    dsp::Wander ageWander_{5151u};

    std::atomic<float> tapeAge_{0.3f};
    std::atomic<float> dustDirt_{0.2f};
    std::atomic<int> tapeType_{0};

    std::atomic<float> vuLeft_{0.0f};
    std::atomic<float> vuRight_{0.0f};
};
