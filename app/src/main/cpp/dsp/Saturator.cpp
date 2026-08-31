#include "Saturator.h"

#include <algorithm>
#include <cmath>

namespace dsp {

void Saturator::setDrive(float drive) { drive_ = std::max(0.01f, drive); }

void Saturator::setAsymmetry(float asymmetry) { asymmetry_ = asymmetry; }

float Saturator::process(float input) const {
    const float bias = asymmetry_ * 0.15f;
    const float driven = (input + bias) * drive_;
    const float shaped = std::tanh(driven);
    const float dcOffset = std::tanh(bias * drive_);
    return (shaped - dcOffset) / drive_;
}

} // namespace dsp
