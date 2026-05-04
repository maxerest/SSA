package com.example.Orbiting_object.Satellite_sub_systems;

public class AntennaParameters {
    private double gain;           // dBi
    private double noiseFigure;    // dB
    private double frequency;      // GHz
    private double bandwidth;      // MHz
    private double efficiency;     // %
    private double txPowerDbm;     // dBm
    private double teta3dB;        //degrees

    public AntennaParameters() {
        this.gain = 20.0;           // dBi (satellite TX antenna)
        this.noiseFigure = 2.0;     // dB
        this.frequency = 8.0;      // GHz (Ku-band uplink/downlink)
        this.bandwidth = 100;      // MHz (typical satellite transponder)
        this.efficiency = 0.60;     // 60%
        this.txPowerDbm = 40.0;     //dB for the power
        this.teta3dB=2;
    }
    public AntennaParameters(double gain, double noiseFigure, double frequency,double bandwidth,double efficiencyn,double teta3dB) {
        this.gain = gain;
        this.noiseFigure = noiseFigure;
        this.frequency = frequency;
        this.bandwidth=bandwidth;
        this.efficiency=efficiency;
        this.teta3dB=teta3dB;
    }

    public double getGain() {
        return gain;
    }

    public double getNoiseFigure() {
        return noiseFigure;
    }

    public double getFrequency() {
        return frequency;
    }

    public double getBandwidth() {
        return bandwidth;
    }

    public double getEfficiency() {
        return efficiency;
    }
    public double getteta3dB() {
        return teta3dB;
    }
    public double getTxPowerDbm(){return txPowerDbm;}

    public void setGain(double gain) {
        this.gain = gain;
    }

    public void setNoiseFigure(double noiseFigure) {
        this.noiseFigure = noiseFigure;
    }

    public void setFrequency(double frequency) {
        this.frequency = frequency;
    }

    public void setBandwidth(double bandwidth) {
        this.bandwidth = bandwidth;
    }

    public void setEfficiency(double efficiency) {
        this.efficiency = efficiency;
    }
    public void setteta3dB(double teta3dB) {
        this.teta3dB = teta3dB;
    }
}
