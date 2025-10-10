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

        gc.setStroke(gridColor);
        gc.setLineWidth(1);

        // Draw vertical grid lines
        for (double worldX = Math.floor((-w / 2 - logic.getOffsetX()) / logic.getScale() / step) * step;
             worldX * logic.getScale() + w / 2 + logic.getOffsetX() < w;
             worldX += step) {

            double screenX = w / 2 + worldX * logic.getScale() + logic.getOffsetX();
            gc.strokeLine(screenX, 0, screenX, h);

            if (Math.abs(worldX) > 1e-6) {
                gc.setFill(textColor);
                gc.fillText(String.format("%.2f", worldX), screenX + 2, h / 2 + logic.getOffsetY() - 2);
            }
        }

        // Draw horizontal grid lines
        for (double worldY = Math.floor((-h / 2 - logic.getOffsetY()) / logic.getScale() / step) * step;
             worldY * logic.getScale() + h / 2 + logic.getOffsetY() < h;
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

        // Calculate function points
        for (int fi = 0; fi < functions.size(); fi++) {
            String exprString = functions.get(fi);
            if (previewReplaceIndex == fi && previewExpr != null && !previewExpr.isEmpty()) {
                exprString = previewExpr;
            }

            List<double[]> pts = new ArrayList<>();
            try {
                Expression expr = new ExpressionBuilder(exprString).variable("x").build();

                for (double px = -w / 2; px < w / 2; px++) {
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
                Expression expr = new ExpressionBuilder(previewExpr).variable("x").build();
                for (double px = -w / 2; px < w / 2; px++) {
                    double x = (px - logic.getOffsetX()) / logic.getScale();
                    double y = expr.setVariable("x", x).evaluate();
                    double screenX = w / 2 + x * logic.getScale() + logic.getOffsetX();
                    double screenY = h / 2 - y * logic.getScale() + logic.getOffsetY();
                    pts.add(new double[]{screenX, screenY, x, y});
                }

                functionPoints.add(pts);
                drewStandalonePreview = true;
            } catch (Exception ignored) {
            }
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

        // Hover / snap-to points (intersections)
        for (double[] inter : intersections) {
            if (Math.hypot(mouseX - inter[0], mouseY - inter[1]) < hoverRadius) {
                drawHoverPoint(gc, inter[0], inter[1], inter[2], inter[3], axisColor, 
                    (bgPaint instanceof Color) ? (Color) bgPaint : Color.BLACK);
                snapped = true;
                break;
            }
        }

        // Hover on curve
        if (!snapped) {
            for (List<double[]> pts : functionPoints) {
                for (int k = 1; k < pts.size(); k++) {
                    double[] p1 = pts.get(k - 1);
                    double[] p2 = pts.get(k);
                    if (mouseX >= Math.min(p1[0], p2[0]) && mouseX <= Math.max(p1[0], p2[0])) {
                        double t = (mouseX - p1[0]) / (p2[0] - p1[0] + 1e-9);
                        double lineY = p1[1] + t * (p2[1] - p1[1]);
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
}
