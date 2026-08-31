#include "TapeEngine.h"

#include <algorithm>
#include <cmath>

TapeEngine::~TapeEngine() {
    if (stream_) {
        stream_->stop();
        stream_->close();
    }
}

void TapeEngine::openStream(int32_t sampleRate, int32_t channelCount) {
    if (stream_) {
        stream_->stop();
        stream_->close();
        stream_.reset();
    }

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Exclusive)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(channelCount)
        ->setSampleRate(sampleRate)
        ->setDataCallback(this)
        ->setErrorCallback(this);

    builder.openStream(stream_);
}

bool TapeEngine::loadAudio(const float *interleaved, int64_t frameCount, int channelCount, int32_t sampleRate) {
    playing_.store(false, std::memory_order_relaxed);

    channelCount_ = channelCount;
    engineSampleRate_ = sampleRate;
    pcmData_.assign(interleaved, interleaved + frameCount * channelCount);
    totalFrames_.store(frameCount, std::memory_order_relaxed);
    playheadFrames_.store(0, std::memory_order_relaxed);

    for (int ch = 0; ch < 2; ++ch) {
        wowFlutter_[ch].prepare(static_cast<float>(sampleRate), ch == 1 ? 0.7f : 0.0f);
        highPass_[ch].reset();
        lowPass_[ch].reset();
    }
    dropout_.prepare(static_cast<float>(sampleRate));
    dirtWander_.prepare(static_cast<float>(sampleRate));
    ageWander_.prepare(static_cast<float>(sampleRate));

    openStream(sampleRate, 2);
    return true;
}

void TapeEngine::play() {
    if (playheadFrames_.load(std::memory_order_relaxed) >= totalFrames_.load(std::memory_order_relaxed)) {
        playheadFrames_.store(0, std::memory_order_relaxed);
    }
    playing_.store(true, std::memory_order_relaxed);
    if (stream_) stream_->requestStart();
}

void TapeEngine::pause() {
    playing_.store(false, std::memory_order_relaxed);
    if (stream_) stream_->requestPause();
}

void TapeEngine::stop() {
    playing_.store(false, std::memory_order_relaxed);
    playheadFrames_.store(0, std::memory_order_relaxed);
    if (stream_) stream_->requestStop();
}

void TapeEngine::seekToFrame(int64_t frame) {
    const int64_t total = totalFrames_.load(std::memory_order_relaxed);
    playheadFrames_.store(std::clamp<int64_t>(frame, 0, total), std::memory_order_relaxed);
}

int64_t TapeEngine::getPositionFrames() const { return playheadFrames_.load(std::memory_order_relaxed); }

int64_t TapeEngine::getDurationFrames() const { return totalFrames_.load(std::memory_order_relaxed); }

bool TapeEngine::isPlaying() const { return playing_.load(std::memory_order_relaxed); }

void TapeEngine::setTapeAge(float value01) {
    tapeAge_.store(std::clamp(value01, 0.0f, 1.0f), std::memory_order_relaxed);
}

void TapeEngine::setDustDirt(float value01) {
    dustDirt_.store(std::clamp(value01, 0.0f, 1.0f), std::memory_order_relaxed);
}

void TapeEngine::setTapeType(int type) { tapeType_.store(type, std::memory_order_relaxed); }

float TapeEngine::getVuLeft() const { return vuLeft_.load(std::memory_order_relaxed); }

float TapeEngine::getVuRight() const { return vuRight_.load(std::memory_order_relaxed); }

void TapeEngine::applyTapeTypeCurve(int32_t numFrames) {
    const float age = tapeAge_.load(std::memory_order_relaxed);
    const int type = tapeType_.load(std::memory_order_relaxed);
    const float dirt = dustDirt_.load(std::memory_order_relaxed);

    // Square the sliders so the bottom of the range stays subtle and the
    // more extreme "trashed tape" character is reserved for the top - a
    // linear mapping made every stage sound overdone well before halfway.
    // A slow "wander" then rides on top of each curve so the amount of hiss
    // and degradation drifts gently over time rather than sitting dead
    // still, the way a real tape's physical imperfections would vary.
    const float dirtWander = 1.0f + dirtWander_.next(numFrames) * 0.2f;
    const float ageWander = 1.0f + ageWander_.next(numFrames) * 0.12f;
    const float ageCurve = std::clamp(age * age * ageWander, 0.0f, 1.0f);
    const float dirtCurve = std::clamp(dirt * dirt * dirtWander, 0.0f, 1.0f);

    float baseLowCutoff;
    float baseHighCutoff;
    float baseDrive;
    float baseAsymmetry;
    float driveAgeGain;
    float asymmetryAgeGain;

    switch (type) {
        case 1: // Type II - Chrome: brighter, tighter
            baseLowCutoff = 15000.0f;
            baseHighCutoff = 35.0f;
            baseDrive = 0.35f;
            baseAsymmetry = 0.03f;
            driveAgeGain = 0.9f;
            asymmetryAgeGain = 0.10f;
            break;
        case 2: // Type IV - Metal: most extended, cleanest
            baseLowCutoff = 16500.0f;
            baseHighCutoff = 25.0f;
            baseDrive = 0.25f;
            baseAsymmetry = 0.015f;
            driveAgeGain = 0.8f;
            asymmetryAgeGain = 0.08f;
            break;
        default: // Type I - Normal: warmest baseline
            baseLowCutoff = 13000.0f;
            baseHighCutoff = 45.0f;
            baseDrive = 0.5f;
            baseAsymmetry = 0.05f;
            driveAgeGain = 1.0f;
            asymmetryAgeGain = 0.12f;
            break;
    }

    const float lowCutoff = std::max(2500.0f, baseLowCutoff - ageCurve * 9000.0f);
    const float drive = baseDrive + ageCurve * driveAgeGain;
    const float asymmetry = baseAsymmetry + ageCurve * asymmetryAgeGain;

    const float wowDepthMs = ageCurve * 1.0f;
    const float flutterDepthMs = ageCurve * 0.3f;

    for (int ch = 0; ch < 2; ++ch) {
        highPass_[ch].configure(dsp::FilterType::HighPass, static_cast<float>(engineSampleRate_), baseHighCutoff, 0.707f);
        lowPass_[ch].configure(dsp::FilterType::LowPass, static_cast<float>(engineSampleRate_), lowCutoff, 0.707f);
        saturator_[ch].setDrive(drive);
        saturator_[ch].setAsymmetry(asymmetry);
        wowFlutter_[ch].setWow(0.9f, wowDepthMs);
        wowFlutter_[ch].setFlutter(22.0f, flutterDepthMs);
        noise_[ch].setLevel(dirtCurve);
    }
    dropout_.setAmount(dirtCurve * dirtCurve * 0.5f);
}

float TapeEngine::processChannel(float rawSample, int channel) {
    float sample = wowFlutter_[channel].process(rawSample);
    sample = highPass_[channel].process(sample);
    sample = lowPass_[channel].process(sample);
    sample = saturator_[channel].process(sample);
    return sample;
}

oboe::DataCallbackResult TapeEngine::onAudioReady(oboe::AudioStream *stream, void *audioData, int32_t numFrames) {
    auto *output = static_cast<float *>(audioData);
    const int outputChannels = stream->getChannelCount();

    applyTapeTypeCurve(numFrames);

    const int64_t total = totalFrames_.load(std::memory_order_relaxed);
    int64_t head = playheadFrames_.load(std::memory_order_relaxed);
    const bool active = playing_.load(std::memory_order_relaxed) && total > 0;

    float peakL = 0.0f;
    float peakR = 0.0f;

    for (int32_t frame = 0; frame < numFrames; ++frame) {
        float rawL = 0.0f;
        float rawR = 0.0f;

        if (active && head < total) {
            const int64_t base = head * channelCount_;
            rawL = pcmData_[static_cast<size_t>(base)];
            rawR = (channelCount_ > 1) ? pcmData_[static_cast<size_t>(base + 1)] : rawL;
            ++head;
        } else if (active) {
            playing_.store(false, std::memory_order_relaxed);
        }

        float outL = processChannel(rawL, 0);
        float outR = processChannel(rawR, 1);

        if (active) {
            outL += noise_[0].process();
            outR += noise_[1].process();
            const float dropGain = dropout_.nextGain();
            outL *= dropGain;
            outR *= dropGain;
        }

        outL = std::clamp(outL, -1.0f, 1.0f);
        outR = std::clamp(outR, -1.0f, 1.0f);

        peakL = std::max(peakL, std::fabs(outL));
        peakR = std::max(peakR, std::fabs(outR));

        if (outputChannels >= 2) {
            output[frame * outputChannels] = outL;
            output[frame * outputChannels + 1] = outR;
            for (int c = 2; c < outputChannels; ++c) output[frame * outputChannels + c] = 0.0f;
        } else {
            output[frame] = 0.5f * (outL + outR);
        }
    }

    playheadFrames_.store(head, std::memory_order_relaxed);
    vuLeft_.store(peakL, std::memory_order_relaxed);
    vuRight_.store(peakR, std::memory_order_relaxed);

    return oboe::DataCallbackResult::Continue;
}

void TapeEngine::onErrorAfterClose(oboe::AudioStream * /*stream*/, oboe::Result error) {
    // The output device changed (e.g. Bluetooth/wired headphones connected or
    // disconnected) and Oboe tore the stream down entirely - just calling
    // play() again would resume a dead stream with no audible output. Reopen
    // a fresh stream targeting whatever route is now active, and restart it
    // if we were mid-playback.
    if (error != oboe::Result::ErrorDisconnected) return;

    const bool wasPlaying = playing_.load(std::memory_order_relaxed);
    openStream(engineSampleRate_, 2);
    if (wasPlaying && stream_) {
        stream_->requestStart();
    }
}
