package org.example;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Text;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

import java.util.ArrayList;
import java.util.List;

public class GraphRenderer {
    private static final Color[] COLORS = {Color.web("#ff6b6b"), Color.web("#4da6ff"), Color.web("#7bffb2"), Color.web("#ffb86b"), Color.web("#c087ff")};
    private static final double HOVER_RADIUS = 15;
    private static final double DISCONTINUITY_THRESHOLD = 0.5;

    private final GraphLogic logic;
    private final GraphThemeManager themeManager;
    private double mouseX = -1, mouseY = -1;
    private volatile String previewExpr = "";
    private volatile int previewReplaceIndex = -1;

    public GraphRenderer(GraphLogic logic, GraphThemeManager themeManager) {
        this.logic = logic;
        this.themeManager = themeManager;
    }

    public void setMousePosition(double x, double y) { this.mouseX = x; this.mouseY = y; }
    public void setPreviewExpr(String expr) { this.previewExpr = expr; }
    public void setPreviewReplaceIndex(int index) { this.previewReplaceIndex = index; }
    public String getPreviewExpr() { return previewExpr; }
    public int getPreviewReplaceIndex() { return previewReplaceIndex; }

    public void redraw(GraphicsContext gc, Canvas canvas, List<String> functions) {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        double overscan = 2; // pixels

        Paint bgPaint = themeManager.getBackgroundPaint();
        Color gridColor = themeManager.getGridColor();
        Color axisColor = themeManager.getAxisColor();
        Color textColor = themeManager.getTextColor();

        gc.setFill(bgPaint);
        gc.fillRect(0, 0, w, h);

        double pixelsPerUnit = logic.getScale();
        double minPixelSpacing = 50;
        double rawStep = minPixelSpacing / pixelsPerUnit;
        double step = logic.chooseNiceStep(rawStep);
        int pxStep = 1;

        gc.setStroke(gridColor);
        gc.setLineWidth(1);

        // Draw vertical grid lines (with overscan)
        for (double worldX = Math.floor((-w / 2 - overscan - logic.getOffsetX()) / logic.getScale() / step) * step;
             worldX * logic.getScale() + w / 2 + logic.getOffsetX() < w + overscan;
             worldX += step) {

            double screenX = w / 2 + worldX * logic.getScale() + logic.getOffsetX();
            gc.strokeLine(screenX, 0, screenX, h);

            if (Math.abs(worldX) > 1e-6) {
                gc.setFill(textColor);
                gc.fillText(String.format("%.2f", worldX), screenX + 2, h / 2 + logic.getOffsetY() - 2);
            }
        }

        // Draw horizontal grid lines (with overscan)
        for (double worldY = Math.floor((-h / 2 - overscan - logic.getOffsetY()) / logic.getScale() / step) * step;
             worldY * logic.getScale() + h / 2 + logic.getOffsetY() < h + overscan;
             worldY += step) {

            double screenY = h / 2 - worldY * logic.getScale() + logic.getOffsetY();
            gc.strokeLine(0, screenY, w, screenY);

            if (Math.abs(worldY) > 1e-6) {
                gc.setFill(textColor);
                gc.fillText(String.format("%.2f", worldY), w / 2 + logic.getOffsetX() + 2, screenY - 2);
            }
        }

        // Draw axes
        gc.setStroke(axisColor);
        gc.setLineWidth(2);
        gc.strokeLine(0, h / 2 + logic.getOffsetY(), w, h / 2 + logic.getOffsetY());
        gc.strokeLine(w / 2 + logic.getOffsetX(), 0, w / 2 + logic.getOffsetX(), h);

        List<double[]> intersections = new ArrayList<>();
        List<List<double[]>> functionPoints = new ArrayList<>();
        List<Double> verticalLines = new ArrayList<>();
        double threshold = (h / logic.getScale()) * DISCONTINUITY_THRESHOLD;

        // Process functions
        for (int fi = 0; fi < functions.size(); fi++) {
            String expr = (previewReplaceIndex == fi && !previewExpr.isEmpty()) ? previewExpr : functions.get(fi);
            processExpression(expr, functionPoints, verticalLines, w, h, overscan, pxStep, threshold);
        }

        // Handle standalone preview
        boolean drewStandalonePreview = false;
        if (!previewExpr.isEmpty() && previewReplaceIndex < 0) {
            int sizeBefore = functionPoints.size();
            processExpression(previewExpr, functionPoints, verticalLines, w, h, overscan, pxStep, threshold);
            drewStandalonePreview = functionPoints.size() > sizeBefore;
        }

        // Find intersections between functions
        for (int i = 0; i < functionPoints.size(); i++) {
            for (int j = i + 1; j < functionPoints.size(); j++) {
                List<double[]> f1 = functionPoints.get(i);
                List<double[]> f2 = functionPoints.get(j);

                for (int k = 1; k < Math.min(f1.size(), f2.size()); k++) {
                    double y1a = f1.get(k - 1)[3];
                    double y1b = f1.get(k)[3];
                    double y2a = f2.get(k - 1)[3];
                    double y2b = f2.get(k)[3];

                    if ((y1a - y2a) * (y1b - y2b) < 0) {
                        double t = Math.abs(y1a - y2a) / (Math.abs((y1a - y2a)) + Math.abs((y1b - y2b)));
                        double x = f1.get(k - 1)[2] * (1 - t) + f1.get(k)[2] * t;
                        double y = f1.get(k - 1)[3] * (1 - t) + f1.get(k)[3] * t;
                        double screenX = w / 2 + x * logic.getScale() + logic.getOffsetX();
                        double screenY = h / 2 - y * logic.getScale() + logic.getOffsetY();
                        intersections.add(new double[]{screenX, screenY, x, y});
                    }
                }
            }
        }

        // Find axis intersections and extrema
        for (List<double[]> pts : functionPoints) {
            for (int k = 1; k < pts.size(); k++) {
                double[] p1 = pts.get(k - 1);
                double[] p2 = pts.get(k);

                // y = 0
                if (p1[3] * p2[3] < 0) {
                    double t = p1[3] / (p1[3] - p2[3]);
                    double x = p1[2] + t * (p2[2] - p1[2]);
                    double screenX = w / 2 + x * logic.getScale() + logic.getOffsetX();
                    double screenY = h / 2 + logic.getOffsetY();
                    intersections.add(new double[]{screenX, screenY, x, 0});
                }

                // x = 0
                if (p1[2] * p2[2] < 0) {
                    double t = p1[2] / (p1[2] - p2[2]);
                    double y = p1[3] + t * (p2[3] - p1[3]);
                    double screenX = w / 2 + logic.getOffsetX();
                    double screenY = h / 2 - y * logic.getScale() + logic.getOffsetY();
                    intersections.add(new double[]{screenX, screenY, 0, y});
                }

                // Extrema (slope sign change)
                if (k > 1) {
                    double slopePrev = pts.get(k - 1)[3] - pts.get(k - 2)[3];
                    double slopeCurr = p2[3] - p1[3];
                    if (slopePrev * slopeCurr < 0) {
                        double x = p1[2];
                        double y = p1[3];
                        double screenX = p1[0];
                        double screenY = p1[1];
                        intersections.add(new double[]{screenX, screenY, x, y});
                    }
                }
            }
        }

        // Origin (0,0)
        double originX = w / 2 + logic.getOffsetX();
        double originY = h / 2 + logic.getOffsetY();
        intersections.add(new double[]{originX, originY, 0, 0});

        boolean snapped = false;

        // Draw functions
        for (int i = 0; i < functionPoints.size(); i++) {
            boolean isPreview = drewStandalonePreview && i == functionPoints.size() - 1;
            setupGraphicsContext(gc, isPreview, i);
            drawSegments(gc, functionPoints.get(i), threshold);
            gc.setLineDashes(null);
            gc.setGlobalAlpha(1.0);
        }

        // Vertical line intersections
        for (Double vx : verticalLines) {
            for (List<double[]> pts : functionPoints) {
                for (int k = 1; k < pts.size(); k++) {
                    double[] p1 = pts.get(k - 1), p2 = pts.get(k);
                    if ((p1[2] - vx) * (p2[2] - vx) <= 0) {
                        double t = Math.abs(p2[2] - p1[2]) < 1e-9 ? 0 : Math.max(0, Math.min(1, (vx - p1[2]) / (p2[2] - p1[2])));
                        intersections.add(toScreen(vx, p1[3] * (1 - t) + p2[3] * t, w, h));
                    }
                }
            }
            intersections.add(toScreen(vx, 0, w, h));
        }

        // Hover detection
        Color bg = (bgPaint instanceof Color) ? (Color) bgPaint : Color.BLACK;
        for (double[] inter : intersections) {
            if (Math.hypot(mouseX - inter[0], mouseY - inter[1]) < HOVER_RADIUS) {
                drawHoverPoint(gc, inter[0], inter[1], inter[2], inter[3], axisColor, bg);
                return;
            }
        }

        for (List<double[]> pts : functionPoints) {
            for (int k = 1; k < pts.size(); k++) {
                double[] p1 = pts.get(k - 1), p2 = pts.get(k);
                double dx = p2[0] - p1[0], dy = p2[1] - p1[1];
                if (Math.abs(dx) < 1e-6) {
                    if (Math.abs(mouseX - p1[0]) < HOVER_RADIUS && mouseY >= Math.min(p1[1], p2[1]) - HOVER_RADIUS && mouseY <= Math.max(p1[1], p2[1]) + HOVER_RADIUS) {
                        drawHoverPoint(gc, p1[0], mouseY, p1[2], (h / 2 + logic.getOffsetY() - mouseY) / logic.getScale(), axisColor, bg);
                        return;
                    }
                } else if (mouseX >= Math.min(p1[0], p2[0]) && mouseX <= Math.max(p1[0], p2[0])) {
                    double t = (mouseX - p1[0]) / dx;
                    double lineY = p1[1] + t * dy;
                    if (Math.abs(mouseY - lineY) < HOVER_RADIUS) {
                        drawHoverPoint(gc, mouseX, lineY, p1[2] * (1 - t) + p2[2] * t, p1[3] * (1 - t) + p2[3] * t, axisColor, bg);
                        return;
                    }
                }
            }
        }
    }

    private void processExpression(String expr, List<List<double[]>> functionPoints, List<Double> verticalLines, 
                                   double w, double h, double overscan, int pxStep, double threshold) {
        if (expr.contains("=")) {
            int before = verticalLines.size();
            handleEquation(expr, verticalLines, w, h);
            for (int i = before; i < verticalLines.size(); i++) {
                double vx = verticalLines.get(i);
                double worldYMin = (-h / 2 - logic.getOffsetY()) / logic.getScale();
                double worldYMax = (h / 2 - logic.getOffsetY()) / logic.getScale();
                double screenX = w / 2 + vx * logic.getScale() + logic.getOffsetX();
                List<double[]> vpts = new ArrayList<>();
                vpts.add(new double[]{screenX, 0, vx, worldYMax});
                vpts.add(new double[]{screenX, h, vx, worldYMin});
                functionPoints.add(vpts);
            }
        } else {
            try {
                Expression expression = new ExpressionBuilder(expr).variable("x").build();
                List<double[]> pts = new ArrayList<>();
                for (double px = -w / 2 - overscan; px < w / 2 + overscan; px += pxStep) {
                    double x = (px - logic.getOffsetX()) / logic.getScale();
                    double y = expression.setVariable("x", x).evaluate();
                    if (Double.isFinite(y)) {
                        pts.add(new double[]{w / 2 + x * logic.getScale() + logic.getOffsetX(), 
                                             h / 2 - y * logic.getScale() + logic.getOffsetY(), x, y});
                    }
                }
                if (!pts.isEmpty()) functionPoints.add(pts);
            } catch (Exception ignored) {}
        }
    }

    private void setupGraphicsContext(GraphicsContext gc, boolean isPreview, int index) {
        if (isPreview) {
            gc.setLineDashes(8);
            gc.setGlobalAlpha(0.6);
            gc.setStroke(Color.gray(0.8));
        } else {
            gc.setLineDashes(null);
            gc.setGlobalAlpha(1.0);
            gc.setStroke(COLORS[index % COLORS.length]);
        }
        gc.setLineWidth(2);
    }

    private void drawSegments(GraphicsContext gc, List<double[]> pts, double threshold) {
        for (int k = 1; k < pts.size(); k++) {
            double[] p1 = pts.get(k - 1), p2 = pts.get(k);
            if (Math.abs(p2[3] - p1[3]) < threshold || Math.abs(p2[2] - p1[2]) < 1e-9) {
                gc.strokeLine(p1[0], p1[1], p2[0], p2[1]);
            }
        }
    }

    private double[] toScreen(double worldX, double worldY, double w, double h) {
        return new double[]{
            w / 2 + worldX * logic.getScale() + logic.getOffsetX(),
            h / 2 - worldY * logic.getScale() + logic.getOffsetY(),
            worldX, worldY
        };
    }

    private void drawHoverPoint(GraphicsContext gc, double screenX, double screenY, double worldX, double worldY, Color textColor, Color bg) {
        gc.setFill(textColor);
        gc.fillOval(screenX - 4, screenY - 4, 8, 8);
        String label = String.format("(%.2f, %.2f)", worldX, worldY);
        Text textNode = new Text(label);
        textNode.setFont(gc.getFont());
        double tw = textNode.getLayoutBounds().getWidth(), th = textNode.getLayoutBounds().getHeight();
        gc.setFill(bg.deriveColor(0, 1, 1, 0.8));
        gc.fillRect(screenX + 8, screenY - th, tw + 8, th + 4);
        gc.setFill(textColor);
        gc.fillText(label, screenX + 12, screenY - 2);
    }

    private void handleEquation(String equation, List<Double> verticalLines, double w, double h) {
        if (equation == null) return;
        String eq = equation.replaceAll("\\s+", "");
        int idx = eq.indexOf('=');
        if (idx <= 0 || idx >= eq.length() - 1) return;

        String left = eq.substring(0, idx), right = eq.substring(idx + 1);

        try {
            if (left.equals("x")) { addUnique(verticalLines, new ExpressionBuilder(right).build().evaluate()); return; }
            if (right.equals("x")) { addUnique(verticalLines, new ExpressionBuilder(left).build().evaluate()); return; }
        } catch (Exception ignored) {}

        try {
            Expression lExpr = new ExpressionBuilder(left).variable("x").build();
            Expression rExpr = new ExpressionBuilder(right).variable("x").build();
            double worldXMin = (-w / 2 - logic.getOffsetX()) / logic.getScale();
            double worldXMax = (w / 2 - logic.getOffsetX()) / logic.getScale();
            if (worldXMin > worldXMax) { double tmp = worldXMin; worldXMin = worldXMax; worldXMax = tmp; }
            double step = Math.max(1.0 / logic.getScale(), 1e-3);
            double prevX = worldXMin, prevF = evalDiff(lExpr, rExpr, prevX);

            for (double x = worldXMin + step; x <= worldXMax; x += step) {
                double f = evalDiff(lExpr, rExpr, x);
                if (!Double.isNaN(prevF) && !Double.isNaN(f)) {
                    if (Math.abs(f) < 1e-9) addUnique(verticalLines, x);
                    else if (prevF * f < 0) addUnique(verticalLines, prevX + (Math.abs(prevF) / (Math.abs(prevF) + Math.abs(f))) * (x - prevX));
                }
                prevX = x; prevF = f;
            }
        } catch (Exception ignored) {}
    }

    private double evalDiff(Expression lExpr, Expression rExpr, double x) {
        try { return lExpr.setVariable("x", x).evaluate() - rExpr.setVariable("x", x).evaluate(); }
        catch (Exception e) { return Double.NaN; }
    }

    private void addUnique(List<Double> xs, double x) {
        for (Double v : xs) if (Math.abs(v - x) < 1e-4) return;
        xs.add(x);
    }
}
