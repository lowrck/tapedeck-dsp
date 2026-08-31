#pragma once

namespace dsp {

class Saturator {
public:
    void setDrive(float drive);
    void setAsymmetry(float asymmetry);
    float process(float input) const;

private:
    float drive_ = 1.0f;
    float asymmetry_ = 0.0f;
};

} // namespace dsp
