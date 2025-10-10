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

    private final GraphLogic logic;
    private final GraphThemeManager themeManager;

    private double mouseX = -1;
    private double mouseY = -1;

    private volatile String previewExpr = "";
    private volatile int previewReplaceIndex = -1;

    public GraphRenderer(GraphLogic logic, GraphThemeManager themeManager) {
        this.logic = logic;
        this.themeManager = themeManager;
    }

    public void setMousePosition(double x, double y) {
        this.mouseX = x;
        this.mouseY = y;
    }

    public void setPreviewExpr(String expr) {
        this.previewExpr = expr;
    }

    public void setPreviewReplaceIndex(int index) {
        this.previewReplaceIndex = index;
    }

    public String getPreviewExpr() {
        return previewExpr;
    }

    public int getPreviewReplaceIndex() {
        return previewReplaceIndex;
    }

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

        Color[] colors = {Color.web("#ff6b6b"), Color.web("#4da6ff"), Color.web("#7bffb2"), Color.web("#ffb86b"), Color.web("#c087ff")};
        double hoverRadius = 8;

        List<double[]> intersections = new ArrayList<>();
        List<List<double[]>> functionPoints = new ArrayList<>();
        List<Double> verticalLines = new ArrayList<>(); // world x positions for vertical lines

        // Calculate function points and collect vertical lines for equation inputs
        for (int fi = 0; fi < functions.size(); fi++) {
            String exprString = functions.get(fi);
            if (previewReplaceIndex == fi && previewExpr != null && !previewExpr.isEmpty()) {
                exprString = previewExpr;
            }

            // If the input contains an '=', treat it as an equation and draw x = roots
            if (exprString.contains("=")) {
                int before = verticalLines.size();
                handleEquation(exprString, verticalLines, w, h);
                int after = verticalLines.size();
                // For any new root found, add a vertical polyline (two points across the viewport)
                for (int ri = before; ri < after; ri++) {
                    double vx = verticalLines.get(ri);
                    List<double[]> vpts = new ArrayList<>();
                    // Use visible world bounds for hover data but clamp drawing to canvas [0,h]
                    double worldYMin = (-h / 2 - logic.getOffsetY()) / logic.getScale();
                    double worldYMax = (h / 2 - logic.getOffsetY()) / logic.getScale();
                    double screenX = w / 2 + vx * logic.getScale() + logic.getOffsetX();
                    double screenYTop = 0;           // clamp to top edge
                    double screenYBottom = h;
                    vpts.add(new double[]{screenX, screenYTop, vx, worldYMax});
                    vpts.add(new double[]{screenX, screenYBottom, vx, worldYMin});
                    functionPoints.add(vpts);
                }
                continue;
            }

            List<double[]> pts = new ArrayList<>();
            try {
                Expression expr = new ExpressionBuilder(exprString).variable("x").build();

                for (double px = -w / 2 - overscan; px < w / 2 + overscan; px += pxStep) {
                    double x = (px - logic.getOffsetX()) / logic.getScale();
                    double y = expr.setVariable("x", x).evaluate();

                    double screenX = w / 2 + x * logic.getScale() + logic.getOffsetX();
                    double screenY = h / 2 - y * logic.getScale() + logic.getOffsetY();

                    pts.add(new double[]{screenX, screenY, x, y});
                }
            } catch (Exception ignored) {
            }
            functionPoints.add(pts);
        }
        // Handle standalone preview
        boolean drewStandalonePreview = false;
        if ((previewExpr != null && !previewExpr.isEmpty()) && previewReplaceIndex < 0) {
            List<double[]> pts = new ArrayList<>();
            try {
                if (previewExpr.contains("=")) {
                    int before = verticalLines.size();
                    handleEquation(previewExpr, verticalLines, w, h);
                    int after = verticalLines.size();
                    for (int ri = before; ri < after; ri++) {
                        double vx = verticalLines.get(ri);
                        List<double[]> vpts = new ArrayList<>();
                        // Use visible world bounds for hover data but clamp drawing to canvas [0,h]
                        double worldYMin = (-h / 2 - logic.getOffsetY()) / logic.getScale();
                        double worldYMax = (h / 2 - logic.getOffsetY()) / logic.getScale();
                        double screenX = w / 2 + vx * logic.getScale() + logic.getOffsetX();
                        double screenYTop = 0;           // clamp to top edge
                        double screenYBottom = h;        // clamp to bottom edge
                        vpts.add(new double[]{screenX, screenYTop, vx, worldYMax});
                        vpts.add(new double[]{screenX, screenYBottom, vx, worldYMin});
                        functionPoints.add(vpts);
                    }
                    // do not mark drewStandalonePreview; we just added as a function-like line
                } else {
                    Expression expr = new ExpressionBuilder(previewExpr).variable("x").build();
                    for (double px = -w / 2 - overscan; px < w / 2 + overscan; px += pxStep) {
                        double x = (px - logic.getOffsetX()) / logic.getScale();
                        double y = expr.setVariable("x", x).evaluate();
                        double screenX = w / 2 + x * logic.getScale() + logic.getOffsetX();
                        double screenY = h / 2 - y * logic.getScale() + logic.getOffsetY();
                        pts.add(new double[]{screenX, screenY, x, y});
                    }
                    functionPoints.add(pts);
                    drewStandalonePreview = true;
                }
            } catch (Exception ignored) {}
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
            boolean isPreviewStandalone = drewStandalonePreview && i == functionPoints.size() - 1;

            if (isPreviewStandalone) {
                gc.setLineDashes(8);
                gc.setGlobalAlpha(0.6);
                gc.setLineWidth(2);
                gc.setStroke(Color.gray(0.8));
            } else {
                gc.setLineDashes(null);
                gc.setGlobalAlpha(1.0);
                gc.setLineWidth(2);
                gc.setStroke(colors[i % colors.length]);
            }

            List<double[]> pts = functionPoints.get(i);
            for (int k = 1; k < pts.size(); k++) {
                double[] p1 = pts.get(k - 1);
                double[] p2 = pts.get(k);
                gc.strokeLine(p1[0], p1[1], p2[0], p2[1]);
            }

            gc.setLineDashes(null);
            gc.setGlobalAlpha(1.0);
        }

        // (No separate drawing for vertical lines; they are included in functionPoints and rendered like normal curves)

        // Compute intersections between vertical lines and function curves
        if (!verticalLines.isEmpty()) {
            for (Double vx : verticalLines) {
                for (List<double[]> pts : functionPoints) {
                    if (pts.size() < 2) continue;
                    for (int k = 1; k < pts.size(); k++) {
                        double[] p1 = pts.get(k - 1);
                        double[] p2 = pts.get(k);
                        double x1 = p1[2];
                        double x2 = p2[2];
                        if ((x1 - vx) * (x2 - vx) <= 0) { // segment crosses or touches x = vx
                            double t = (Math.abs(x2 - x1) < 1e-9) ? 0.0 : (vx - x1) / (x2 - x1);
                            t = Math.max(0.0, Math.min(1.0, t));
                            double worldY = p1[3] * (1 - t) + p2[3] * t;
                            double screenX = w / 2 + vx * logic.getScale() + logic.getOffsetX();
                            double screenY = h / 2 - worldY * logic.getScale() + logic.getOffsetY();
                            intersections.add(new double[]{screenX, screenY, vx, worldY});
                        }
                    }
                }
                // x-axis intersection at (vx, 0)
                double screenX = w / 2 + vx * logic.getScale() + logic.getOffsetX();
                double screenY = h / 2 + logic.getOffsetY();
                intersections.add(new double[]{screenX, screenY, vx, 0});
            }
        }

        // Hover / snap-to points (intersections)
        for (double[] inter : intersections) {
            if (Math.hypot(mouseX - inter[0], mouseY - inter[1]) < hoverRadius) {
                drawHoverPoint(gc, inter[0], inter[1], inter[2], inter[3], axisColor, 
                    (bgPaint instanceof Color) ? (Color) bgPaint : Color.BLACK);
                snapped = true;
                break;
            }
        }

        // Hover on curve (including vertical segments)
        if (!snapped) {
            for (List<double[]> pts : functionPoints) {
                for (int k = 1; k < pts.size(); k++) {
                    double[] p1 = pts.get(k - 1);
                    double[] p2 = pts.get(k);
                    double dx = p2[0] - p1[0];
                    double dy = p2[1] - p1[1];
                    if (Math.abs(dx) < 1e-6) {
                        // vertical segment: check horizontal distance and y range
                        if (Math.abs(mouseX - p1[0]) < hoverRadius &&
                                mouseY >= Math.min(p1[1], p2[1]) - hoverRadius &&
                                mouseY <= Math.max(p1[1], p2[1]) + hoverRadius) {
                            double worldX = p1[2];
                            double worldY = (h / 2.0 + logic.getOffsetY() - mouseY) / logic.getScale();
                            drawHoverPoint(gc, p1[0], mouseY, worldX, worldY, axisColor,
                                    (bgPaint instanceof Color) ? (Color) bgPaint : Color.BLACK);
                            return;
                        }
                    } else if (mouseX >= Math.min(p1[0], p2[0]) && mouseX <= Math.max(p1[0], p2[0])) {
                        double t = (mouseX - p1[0]) / (dx + 1e-9);
                        double lineY = p1[1] + t * dy;
                        if (Math.abs(mouseY - lineY) < hoverRadius) {
                            double worldX = p1[2] * (1 - t) + p2[2] * t;
                            double worldY = p1[3] * (1 - t) + p2[3] * t;
                            drawHoverPoint(gc, mouseX, lineY, worldX, worldY, axisColor,
                                    (bgPaint instanceof Color) ? (Color) bgPaint : Color.BLACK);
                            return;
                        }
                    }
                }
            }
        }
    }

    private void drawHoverPoint(GraphicsContext gc, double screenX, double screenY,
                                double worldX, double worldY, Color textColor, Color bg) {
        gc.setFill(textColor);
        gc.fillOval(screenX - 4, screenY - 4, 8, 8);

        String label = String.format("(%.2f, %.2f)", worldX, worldY);
        double padding = 4;

        Text textNode = new Text(label);
        textNode.setFont(gc.getFont());
        double textWidth = textNode.getLayoutBounds().getWidth();
        double textHeight = textNode.getLayoutBounds().getHeight();

        gc.setFill(bg.deriveColor(0, 1, 1, 0.8));
        gc.fillRect(screenX + 8, screenY - textHeight,
                textWidth + padding * 2, textHeight + padding);

        gc.setFill(textColor);
        gc.fillText(label, screenX + 8 + padding, screenY - 2);
    }

    // Parse an equation string like "2x+5 = 10" and append x-roots (world coordinates) to verticalLines.
    // If the equation is of form "x = c" or "c = x", add c directly. Otherwise solve left(x) - right(x) = 0
    // by scanning over the visible x-range and detecting sign changes with lerp
    private void handleEquation(String equation, List<Double> verticalLines, double w, double h) {
        if (equation == null) return;
        String eq = equation.replaceAll("\\s+", "");
        int idx = eq.indexOf('=');
        if (idx <= 0 || idx >= eq.length() - 1) return;

        String left = eq.substring(0, idx);
        String right = eq.substring(idx + 1);

        // Direct x = constant handling
        try {
            if (left.equals("x")) {
                double c = new ExpressionBuilder(right).build().evaluate();
                addUnique(verticalLines, c);
                return;
            }
            if (right.equals("x")) {
                double c = new ExpressionBuilder(left).build().evaluate();
                addUnique(verticalLines, c);
                return;
            }
        } catch (Exception ignored) {}

        // General case: solve F(x) = left(x) - right(x) = 0 via sign changes across the current viewport
        Expression lExpr;
        Expression rExpr;
        try {
            lExpr = new ExpressionBuilder(left).variable("x").build();
            rExpr = new ExpressionBuilder(right).variable("x").build();
        } catch (Exception e) {
            return;
        }

        // Determine visible world x-range
        double worldXMin = (-w / 2 - logic.getOffsetX()) / logic.getScale();
        double worldXMax = (w / 2 - logic.getOffsetX()) / logic.getScale();
        if (worldXMin > worldXMax) {
            double tmp = worldXMin; worldXMin = worldXMax; worldXMax = tmp;
        }

        // Step in world units so that we get approx 1px in screen units
        double step = 1.0 / logic.getScale();
        step = Math.max(step, 1e-3); // avoid too tiny steps when highly zoomed in

        double prevX = worldXMin;
        double prevF;
        try { prevF = lExpr.setVariable("x", prevX).evaluate() - rExpr.setVariable("x", prevX).evaluate(); }
        catch (Exception e) { prevF = Double.NaN; }

        for (double x = worldXMin + step; x <= worldXMax; x += step) {
            double f;
            try { f = lExpr.setVariable("x", x).evaluate() - rExpr.setVariable("x", x).evaluate(); }
            catch (Exception e) { prevX = x; prevF = f = Double.NaN; continue; }

            if (!Double.isNaN(prevF) && !Double.isNaN(f)) {
                // Exact zero
                if (Math.abs(f) < 1e-9) {
                    addUnique(verticalLines, x);
                } else if (prevF * f < 0) {
                    // Linear interpolation between prevX and x
                    double t = Math.abs(prevF) / (Math.abs(prevF) + Math.abs(f));
                    double root = prevX * (1 - t) + x * t;
                    addUnique(verticalLines, root);
                }
            }
            prevX = x; prevF = f;
        }
    }

    private void addUnique(List<Double> xs, double x) {
        for (Double v : xs) {
            if (Math.abs(v - x) < 1e-4) return;
        }
        xs.add(x);
    }
}
