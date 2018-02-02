package com.projectx.clients.market.models;

public class Node {

    private String name;
    private double x, y;
    private int floorLevel;

    public Node(String name, double x, double y, int floorLevel) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.floorLevel = floorLevel;
    }

    public String getName() {
        return name;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public int getFloorLevel() {
        return floorLevel;
    }
}