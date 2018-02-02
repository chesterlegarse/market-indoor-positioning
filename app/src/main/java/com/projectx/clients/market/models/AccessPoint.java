package com.projectx.clients.market.models;

import android.support.annotation.NonNull;

public class AccessPoint implements Comparable<AccessPoint>{

    private double x;
    private double y;
    private int floor;
    private String BSSID;
    private int RSSI;
    private int frequency;
    private static final double DISTANCE_MHZ_M = 27.55;

    public AccessPoint(double x, double y, int floor, String BSSID) {
        this.x = x;
        this.y = y;
        this.floor = floor;
        this.BSSID = BSSID;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public int getRSSI() {
        return RSSI;
    }

    public void setRSSI(int RSSI) {
        this.RSSI = RSSI;
    }

    public int getFrequency() {
        return frequency;
    }

    public void setFrequency(int frequency) {
        this.frequency = frequency;
    }

    @Override
    public String toString() {
        return this.BSSID + " " + this.RSSI + " dBm ";
    }

    @Override
    public int compareTo(@NonNull AccessPoint accessPoint) {
        int compareLevel  = accessPoint.RSSI;
        return compareLevel - this.RSSI;
    }

    /* Static methods */
    public static double calculateDistance(int frequency, int RSSI) {
        return Math.pow(10.0, (DISTANCE_MHZ_M - (20 * Math.log10(frequency)) + Math.abs(RSSI)) / 20.0);
    }
}
