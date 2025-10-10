package org.example;

import javafx.scene.canvas.Canvas;

public class GraphLogic {

    private double scale = 50; // pixels per unit
    private double offsetX = 0;
    private double offsetY = 0;

    private double zoomVelocity = 0;
    private final double zoomFriction = 0.85;
    private final double zoomSensitivity = 0.001;
    private String zoomMode = "None";
    private double zoomFactor = 1.0;
    private boolean zoomToMouse = false;

    public double getScale() {
        return scale;
    }

    public void setScale(double scale) {
        this.scale = scale;
    }

    public double getOffsetX() {
        return offsetX;
    }

    public void setOffsetX(double offsetX) {
        this.offsetX = offsetX;
    }

    public double getOffsetY() {
        return offsetY;
    }

    public void setOffsetY(double offsetY) {
        this.offsetY = offsetY;
    }

    public double getZoomVelocity() {
        return zoomVelocity;
    }

    public void setZoomVelocity(double zoomVelocity) {
        this.zoomVelocity = zoomVelocity;
    }

    public double getZoomFriction() {
        return zoomFriction;
    }

    public double getZoomSensitivity() {
        return zoomSensitivity;
    }

    public String getZoomMode() {
        return zoomMode;
    }

    public void setZoomMode(String zoomMode) {
        this.zoomMode = zoomMode;
    }

    public double getZoomFactor() {
        return zoomFactor;
    }

    public void setZoomFactor(double zoomFactor) {
        this.zoomFactor = zoomFactor;
    }

    public boolean isZoomToMouse() {
        return zoomToMouse;
    }

    public void setZoomToMouse(boolean zoomToMouse) {
        this.zoomToMouse = zoomToMouse;
    }

    public void applyScale(double factor, double screenX, double screenY, boolean zoomToMouse, Canvas canvas) {
        if (!zoomToMouse) {
            scale *= factor;
            return;
        }
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        double worldX = (screenX - w / 2.0 - offsetX) / scale;
        double worldY = (h / 2.0 + offsetY - screenY) / scale;
        double newScale = scale * factor;

        offsetX = screenX - w / 2.0 - worldX * newScale;
        offsetY = screenY - h / 2.0 + worldY * newScale;
        scale = newScale;
    }

    public void resetPosition() {
        offsetX = 0;
        offsetY = 0;
    }

    public void resetScale() {
        scale = 50;
    }

    public double chooseNiceStep(double rawStep) {
        double exp = Math.floor(Math.log10(rawStep));
        double base = Math.pow(10, exp);
        double fraction = rawStep / base;

        if (fraction < 1.5) return base;
        else if (fraction < 3) return 2 * base;
        else if (fraction < 7) return 5 * base;
        else return 10 * base;
    }

    public double easeInOutSine(double t) {
        return 1 - Math.cos((t * Math.PI) / 2);
    }

    public double quadraticEase(double t) {
        return t * t;
    }

    public double cubicEase(double t) {
        return t * t * t;
    }

    public double exponentialEase(double t) {
        return (t == 0) ? 0 : Math.pow(2, 10 * (t - 1));
    }

    public double applyEasing(double factor) {
        return switch (zoomMode) {
            case "Ease In-Out (Sine)" -> easeInOutSine(factor);
            case "Quadratic" -> quadraticEase(factor);
            case "Cubic" -> cubicEase(factor);
            case "Exponential" -> exponentialEase(factor);
            default -> factor;
        };
    }
}
