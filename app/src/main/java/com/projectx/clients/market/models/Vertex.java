package com.projectx.clients.market.models;


@SuppressWarnings("unused")
public class Vertex {

    private double x;
    private double y;

    public Vertex(double x, double y) {
        this.x = x;
        this.y = y;
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

    public double[] toArray() {
        return new double[] {x, y};
    }

    public boolean equals(Vertex vertex) {
        return (this.x == vertex.x && this.y == vertex.y);
    }
}