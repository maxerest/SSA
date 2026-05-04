package com.example.RevisitFrequency;

import org.orekit.time.AbsoluteDate;

public class EO_observations {
    private AbsoluteDate startDate;
    private AbsoluteDate endDate;
    private double total_data;
    public EO_observations(AbsoluteDate startDate, AbsoluteDate endDate, double total_data) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.total_data = total_data;
    }
    public AbsoluteDate getStartDate() {
        return startDate;
    }
    public AbsoluteDate getEndDate() {
        return endDate;
    }
    public double getTotal_data() {
        return total_data;
    }
    public void setStartDate(AbsoluteDate startDate) {
        this.startDate = startDate;
    }
    public void setEndDate(AbsoluteDate endDate) {
        this.endDate = endDate;
    }
    public void setTotal_data(double total_data) {
        this.total_data = total_data;
    }
    public void reduce_total_data(double total_data) {
        this.total_data -= total_data;
    }
}
