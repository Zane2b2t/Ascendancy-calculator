// FDTDSimulation.java
package org.example.physics;

import java.util.List;

public class FDTDSimulation {
    public final int width;
    public final int height;

    // Fields (Yee grid)
    private final double[][] Ez;  // z-directed E
    private final double[][] Hx;  // x-directed H
    private final double[][] Hy;  // y-directed H

    // Material properties per cell
    private final double[][] epsilonR; // relative permittivity
    private final double[][] sigma;    // conductivity (S/m)
    private final boolean[][] isObjectMask;

    // Mur ABC storage
    private final double[] ezLeft, ezRight, ezTop, ezBottom;

    // Simulation parameters
    private int timeStep = 0;
    private double frequencyGHz = 1.0; // GHz
    private String sourceType = "Point Source";
    private String visualizationMode = "Electric Field";

    private final double dx; // meters
    private final double dt; // seconds

    private int sourceX;
    private int sourceY;

    // Physical constants
    private static final double C = 299_792_458.0;
    private static final double EPS0 = 8.8541878128e-12;
    private static final double MU0 = 4.0 * Math.PI * 1e-7;
    private static final double COURANT = 0.5;

    public FDTDSimulation(int width, int height) {
        this.width = width;
        this.height = height;

        Ez = new double[width][height];
        Hx = new double[width][height];
        Hy = new double[width][height];

        epsilonR = new double[width][height];
        sigma = new double[width][height];
        isObjectMask = new boolean[width][height];

        ezLeft = new double[height];
        ezRight = new double[height];
        ezTop = new double[width];
        ezBottom = new double[width];

        // default grid spacing (1 mm) - consistent with earlier code
        this.dx = 1e-3;
        this.dt = COURANT * dx / (C * Math.sqrt(2.0)); // 2D Courant approx

        // default source at center
        sourceX = width / 2;
        sourceY = height / 2;

        // initialize
        resetMaterials();
        reset();
    }

    /**
     * One FDTD time step (Yee algorithm with simple lossy term).
     */
    public void update() {
        timeStep++;

        // Save near-boundary Ez for Mur ABC
        for (int j = 0; j < height; j++) {
            ezLeft[j] = Ez[1][j];
            ezRight[j] = Ez[width - 2][j];
        }
        for (int i = 0; i < width; i++) {
            ezTop[i] = Ez[i][1];
            ezBottom[i] = Ez[i][height - 2];
        }

        // Update H fields
        double hCoef = dt / (MU0 * dx);
        for (int i = 0; i < width - 1; i++) {
            for (int j = 0; j < height - 1; j++) {
                Hx[i][j] += hCoef * (Ez[i][j] - Ez[i][j + 1]);
                Hy[i][j] += hCoef * (Ez[i + 1][j] - Ez[i][j]);
            }
        }

        // Update E field with material (sigma) included
        for (int i = 1; i < width - 1; i++) {
            for (int j = 1; j < height - 1; j++) {
                double epsR = epsilonR[i][j];
                double s = sigma[i][j];

                double eps = Math.max(epsR * EPS0, EPS0 * 1e-6); // avoid zero
                double denom = 1.0 + (s * dt) / (2.0 * eps);
                double ca = (1.0 - (s * dt) / (2.0 * eps)) / denom;
                double cb = (dt / (eps * dx)) / denom;

                double curlH = (Hy[i][j] - Hy[i - 1][j]) - (Hx[i][j] - Hx[i][j - 1]);
                Ez[i][j] = ca * Ez[i][j] + cb * curlH;

                // Clip very large values
                if (!Double.isFinite(Ez[i][j]) || Math.abs(Ez[i][j]) > 1e4) {
                    Ez[i][j] = Math.signum(Ez[i][j]) * 1e4;
                }
            }
        }

        // Inject source using physical time t = timeStep * dt
        applySource();

        // Mur absorbing boundary
        applyMurABC();
    }

    private void applySource() {
        double t = timeStep * dt;
        double omega = 2.0 * Math.PI * frequencyGHz * 1e9; // GHz -> Hz
        double sourceValue;

        // Smooth gaussian startup for first N steps
        if (timeStep < 100) {
            double envelope = Math.exp(-Math.pow((t - 50.0 * dt) / (10.0 * dt), 2));
            sourceValue = Math.sin(omega * t) * envelope * 0.1;
        } else {
            sourceValue = Math.sin(omega * t) * 0.1;
        }

        switch (sourceType) {
            case "Point Source":
                if (inBounds(sourceX, sourceY)) Ez[sourceX][sourceY] += sourceValue;
                break;
            case "Line Source":
                for (int i = 1; i < width - 1; i++) Ez[i][sourceY] += sourceValue * 0.1;
                break;
            case "Plane Wave":
                int waveY = Math.min(height - 2, Math.max(1, 50 + (timeStep / 2) % (height - 100)));
                for (int i = 1; i < width - 1; i++) Ez[i][waveY] += sourceValue * 0.5;
                break;
            case "Gaussian Pulse":
                double sigma_spatial = 20.0;
                for (int i = Math.max(1, sourceX - 30); i < Math.min(width - 1, sourceX + 30); i++) {
                    for (int j = Math.max(1, sourceY - 30); j < Math.min(height - 1, sourceY + 30); j++) {
                        double r = Math.hypot(i - sourceX, j - sourceY);
                        double spatial = Math.exp(-Math.pow(r / sigma_spatial, 2));
                        Ez[i][j] += sourceValue * spatial * 0.05;
                    }
                }
                break;
            default:
                if (inBounds(sourceX, sourceY)) Ez[sourceX][sourceY] += sourceValue;
                break;
        }
    }

    private void applyMurABC() {
        double c = C * dt / dx;
        double coef = (c - 1.0) / (c + 1.0);

        // Left/right
        for (int j = 1; j < height - 1; j++) {
            Ez[0][j] = ezLeft[j] + coef * (Ez[1][j] - Ez[0][j]);
            Ez[width - 1][j] = ezRight[j] + coef * (Ez[width - 2][j] - Ez[width - 1][j]);
        }

        // Top/bottom
        for (int i = 1; i < width - 1; i++) {
            Ez[i][0] = ezTop[i] + coef * (Ez[i][1] - Ez[i][0]);
            Ez[i][height - 1] = ezBottom[i] + coef * (Ez[i][height - 2] - Ez[i][height - 1]);
        }

        // Corners
        Ez[0][0] = 0.5 * (Ez[1][0] + Ez[0][1]);
        Ez[width - 1][0] = 0.5 * (Ez[width - 2][0] + Ez[width - 1][1]);
        Ez[0][height - 1] = 0.5 * (Ez[1][height - 1] + Ez[0][height - 2]);
        Ez[width - 1][height - 1] = 0.5 * (Ez[width - 2][height - 1] + Ez[width - 1][height - 2]);
    }

    public void reset() {
        timeStep = 0;
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                Ez[i][j] = 0.0;
                Hx[i][j] = 0.0;
                Hy[i][j] = 0.0;
            }
        }
    }

    private void resetMaterials() {
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                epsilonR[i][j] = 1.0;
                sigma[i][j] = 0.0;
                isObjectMask[i][j] = false;
            }
        }
    }

    /**
     * Rasterize a list of visualizer SimObjects into per-cell epsilon/sigma/isObjectMask.
     * Must be called whenever simObjects in the visualizer change.
     */
    /**
     * Rasterize a list of visualizer SimObjects into per-cell epsilon/sigma/isObjectMask.
     * Must be called whenever simObjects in the visualizer change.
     */
    public void rasterizeMaterials(List<FDTDVisualizer.SimObject> objects) {
        resetMaterials();

        if (objects == null || objects.isEmpty()) return;

        for (FDTDVisualizer.SimObject obj : objects) {
            double epsR = 1.0;
            double cond = 0.0;
            boolean mark = true;

            switch (obj.type) {
                case "Metal Sphere":
                case "Metal Box":
                case "Corner Reflector":
                    cond = 1e5; // almost perfect conductor
                    break;
                case "Dielectric Sphere":
                    epsR = 2.0 + obj.conductivity * 8.0;
                    break;
                case "Absorber":
                    cond = obj.conductivity * 5.0;
                    break;
                case "Stealth Wedge":
                case "RAM Layer":
                case "Corner Deflector":
                    // Handled per-cell below
                    break;
                default:
                    break;
            }

            // Bounding box with rotation ignored for speed (approx)
            int halfW = Math.max(1, obj.sizeX / 2);
            int halfH = Math.max(1, obj.sizeY / 2);
            int minI = Math.max(0, obj.x - halfW);
            int maxI = Math.min(width - 1, obj.x + halfW);
            int minJ = Math.max(0, obj.y - halfH);
            int maxJ = Math.min(height - 1, obj.y + halfH);

            for (int i = minI; i <= maxI; i++) {
                for (int j = minJ; j <= maxJ; j++) {

                    boolean inside = false;

                    switch (obj.type) {
                        case "Metal Sphere":
                        case "Dielectric Sphere":
                        case "Absorber":
                            double rx = i - obj.x;
                            double ry = j - obj.y;
                            double radius = Math.max(obj.sizeX, obj.sizeY) / 2.0;
                            inside = (rx * rx + ry * ry) <= (radius * radius);
                            break;

                        case "Metal Box":
                            inside = true; // box bounding box already aligned
                            break;

                        case "Corner Reflector":
                            int relx = i - obj.x + halfW;
                            int rely = j - obj.y + halfH;
                            int thickness = Math.max(1, Math.min(obj.sizeX, obj.sizeY) / 5);
                            boolean hor = (rely >= 0 && rely < thickness && relx >= 0 && relx <= obj.sizeX);
                            boolean ver = (relx >= 0 && relx < thickness && rely >= 0 && rely <= obj.sizeY);
                            inside = hor || ver;
                            break;

                        case "Stealth Wedge":
                            // Tapered wedge with gradual absorption
                            int startY = obj.y - obj.sizeY / 2;
                            int endY = obj.y + obj.sizeY / 2;
                            if (j >= startY && j <= endY) {
                                double rel = (j - startY) / (double)obj.sizeY; // 0->1
                                int rowWidth = (int)((1 - rel) * obj.sizeX);
                                if (i >= obj.x - rowWidth / 2 && i <= obj.x + rowWidth / 2) {
                                    inside = true;
                                    cond = obj.conductivity * rel * 10.0;
                                    epsR = 1.0;
                                }
                            }
                            break;

                        case "RAM Layer":
                            // Thin absorber layer, gradually increasing conductivity
                            int layerStartY = obj.y - obj.sizeY / 2;
                            int layerEndY = obj.y + obj.sizeY / 2;
                            if (j >= layerStartY && j <= layerEndY) {
                                inside = true;
                                double rel = (j - layerStartY) / (double)obj.sizeY;
                                cond = obj.conductivity * rel * 15.0; // gradually stronger absorption
                                epsR = 1.0;
                            }
                            break;

                        case "Corner Deflector":
                            // L-shaped ramp to deflect waves
                            int dx = i - obj.x + halfW;
                            int dy = j - obj.y + halfH;
                            int rampThickness = Math.max(1, Math.min(obj.sizeX, obj.sizeY) / 4);
                            boolean horRamp = dx >= 0 && dx < obj.sizeX && dy >= 0 && dy < rampThickness;
                            boolean verRamp = dy >= 0 && dy < obj.sizeY && dx >= 0 && dx < rampThickness;
                            if (horRamp || verRamp) {
                                inside = true;
                                cond = obj.conductivity * 5.0;
                                epsR = 1.0;
                            }
                            break;
                    }

                    if (inside) {
                        epsilonR[i][j] = epsR;
                        sigma[i][j] = cond;
                        isObjectMask[i][j] = mark;
                    }
                }
            }
        }
    }


    // Used by visualizer to draw object overlays
    public boolean isObject(int i, int j) {
        if (i < 0 || i >= width || j < 0 || j >= height) return false;
        return isObjectMask[i][j];
    }

    private boolean inBounds(int i, int j) {
        return i >= 1 && i < width - 1 && j >= 1 && j < height - 1;
    }

    // Getters & setters
    public double[][] getEz() { return Ez; }
    public double[][] getHx() { return Hx; }
    public double[][] getHy() { return Hy; }
    public int getTimeStep() { return timeStep; }
    public int getSourceX() { return sourceX; }
    public int getSourceY() { return sourceY; }
    public void setFrequency(double freqGHz) { this.frequencyGHz = freqGHz; }
    public void setSourceType(String type) { this.sourceType = type; }
    public void setVisualizationMode(String mode) { this.visualizationMode = mode; }
    public String getVisualizationMode() { return visualizationMode; }

    /**
     * Return maximum absolute Ez on grid (for display/statistics).
     */
    public double getMaxField() {
        double max = 0.0;
        for (int i = 0; i < width; i++) for (int j = 0; j < height; j++) {
            double v = Ez[i][j];
            if (Double.isFinite(v)) max = Math.max(max, Math.abs(v));
        }
        return max;
    }
}
