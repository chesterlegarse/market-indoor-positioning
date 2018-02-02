package com.projectx.clients.market.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Position {

    private int floor;
    private double x;
    private double y;

    private Map<String, List<Integer>> values;

    public Position(int floor, double x, double y) {
        values = new HashMap<>();
        this.floor = floor;
        this.x = x;
        this.y = y;
    }

    public int getFloor() {
        return floor;
    }

    public void setFloor(int floor) {
        this.floor = floor;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public Map<String, List<Integer>> getValues() {
        return (values != null) ? values : new HashMap<String, List<Integer>>();
    }

    public void add(String bssid, int rssi) {
        List<Integer> rssiValues = new ArrayList<>();

        if (values.containsKey(bssid.toLowerCase())) {
            rssiValues = values.get(bssid.toLowerCase());
        }

        rssiValues.add(rssi);
        values.put(bssid.toLowerCase(), rssiValues);
    }

}
