package org.example;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

import java.util.ArrayList;
import java.util.List;

public class GraphPlotterFX extends Application {

    private double scale = 50; // pixels per unit
    private double offsetX = 0;
    private double offsetY = 0;

    private double dragStartX, dragStartY;

    private final List<String> functions = new ArrayList<>();

    private Canvas canvas;
    private double mouseX = -1, mouseY = -1;

    private static volatile String initialFunction = "sin(x)";
    private static volatile GraphPlotterFX instance;   // keep reference
    private static volatile boolean appLaunched = false;
    private Stage stage;

    private boolean darkMode = false;

    // Zoom smoothing
    private double zoomVelocity = 0;
    private final double zoomFriction = 0.85;
    private final double zoomSensitivity = 0.001;
    private String zoomMode = "None";
    private double zoomFactor = 1.0;

    public static synchronized void launchGraph(String func) {
        initialFunction = func;
        if (!appLaunched) {
            appLaunched = true;
            new Thread(() -> Application.launch(GraphPlotterFX.class)).start();
        } else {
            if (instance != null) {
                instance.addFunction(func);
            }
        }
    }

    public static boolean isAppLaunched() {
        return appLaunched;
    }

    public static void shutdown() {
        if (!appLaunched) return;
        Platform.runLater(() -> {
            if (instance != null && instance.stage != null) instance.stage.hide();
            Platform.exit();
        });
        appLaunched = false;
    }

    @Override
    public void start(Stage stage) {
        Platform.setImplicitExit(false);

        instance = this;
        this.stage = stage;

        canvas = new Canvas();
        GraphicsContext gc = canvas.getGraphicsContext2D();

        canvas.widthProperty().bind(stage.widthProperty());
        canvas.heightProperty().bind(stage.heightProperty().subtract(100));

        TextField functionInput = new TextField(initialFunction);
        Button addBtn = new Button("Add Function");

        addBtn.setOnAction(e -> {
            String expr = functionInput.getText().trim();
            if (!expr.isEmpty()) {
                functions.add(expr);
                redraw(gc);
            }
        });

        Button darkModeBtn = new Button("Toggle Dark Mode");
        darkModeBtn.setOnAction(e -> {
            darkMode = !darkMode;
            redraw(gc);
        });

        Button resetPosBtn = new Button("Reset Position");
        resetPosBtn.setOnAction(e -> {
            offsetX = 0;
            offsetY = 0;
            redraw(gc);
        });

        Button resetScaleBtn = new Button("Reset Scale");
        resetScaleBtn.setOnAction(e -> {
            scale = 50;
            redraw(gc);
        });

        ComboBox<String> zoomModeDropdown = new ComboBox<>();
        zoomModeDropdown.getItems().addAll("None", "Ease In-Out (Sine)", "Quadratic", "Cubic", "Exponential");
        zoomModeDropdown.setValue("None");
        zoomModeDropdown.valueProperty().addListener((obs, oldVal, newVal) -> zoomMode = newVal);

        Slider zoomFactorSlider = new Slider(0.1, 3.0, 1.0);
        zoomFactorSlider.setShowTickMarks(true);
        zoomFactorSlider.setShowTickLabels(true);
        zoomFactorSlider.setMajorTickUnit(0.5);
        zoomFactorSlider.setMinorTickCount(4);
        zoomFactorSlider.setBlockIncrement(0.1);
        zoomFactorSlider.valueProperty().addListener((obs, oldVal, newVal) -> zoomFactor = newVal.doubleValue());

        HBox controls = new HBox(10, functionInput, addBtn,
                new Label("Zoom Mode:"), zoomModeDropdown,
                new Label("Zoom Factor:"), zoomFactorSlider,
                darkModeBtn, resetPosBtn, resetScaleBtn);

        BorderPane root = new BorderPane(canvas, null, null, controls, null);
        Scene scene = new Scene(root);

        canvas.setOnMousePressed(e -> {
            dragStartX = e.getX();
            dragStartY = e.getY();
        });

        canvas.setOnMouseDragged(e -> {
            offsetX += e.getX() - dragStartX;
            offsetY += e.getY() - dragStartY;
            dragStartX = e.getX();
            dragStartY = e.getY();
            redraw(gc);
        });

        canvas.setOnScroll(e -> {
            double baseFactor = (e.getDeltaY() > 0) ? 1.1 : 0.9;

            if (zoomMode.equals("None")) {
                scale *= baseFactor;
                redraw(gc);
            } else {
                zoomVelocity += e.getDeltaY() * zoomSensitivity * zoomFactor;
            }
        });

        canvas.setOnMouseMoved(e -> {
            mouseX = e.getX();
            mouseY = e.getY();
            redraw(gc);
        });

        stage.setTitle("Modern Graph Plotter");
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> {
            e.consume();
            stage.hide();
        });

        stage.show();

        functions.add(initialFunction);
        redraw(gc);

        new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (!zoomMode.equals("None") && Math.abs(zoomVelocity) > 0.0001) {
                    double factor = Math.exp(zoomVelocity);

                    switch (zoomMode) {
                        case "Ease In-Out (Sine)" -> factor = easeInOutSine(factor);
                        case "Quadratic" -> factor = quadraticEase(factor);
                        case "Cubic" -> factor = cubicEase(factor);
                        case "Exponential" -> factor = exponentialEase(factor);
                    }

                    scale *= factor;
                    zoomVelocity *= zoomFriction;
                    redraw(gc);
                }
            }
        }.start();
    }

    private void redraw(GraphicsContext gc) {
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        Color bg = darkMode ? Color.BLACK : Color.WHITE;
        Color gridColor = darkMode ? Color.DARKGRAY : Color.LIGHTGRAY;
        Color axisColor = darkMode ? Color.WHITE : Color.BLACK;
        Color textColor = darkMode ? Color.LIGHTGRAY : Color.GRAY;

        gc.setFill(bg);
        gc.fillRect(0, 0, w, h);

        double pixelsPerUnit = scale;
        double minPixelSpacing = 50;
        double rawStep = minPixelSpacing / pixelsPerUnit;
        double step = chooseNiceStep(rawStep);

        gc.setStroke(gridColor);
        gc.setLineWidth(1);

        for (double worldX = Math.floor((-w / 2 - offsetX) / scale / step) * step;
             worldX * scale + w / 2 + offsetX < w;
             worldX += step) {

            double screenX = w / 2 + worldX * scale + offsetX;
            gc.strokeLine(screenX, 0, screenX, h);

            if (Math.abs(worldX) > 1e-6) {
                gc.setFill(textColor);
                gc.fillText(String.format("%.2f", worldX), screenX + 2, h / 2 + offsetY - 2);
            }
        }

        for (double worldY = Math.floor((-h / 2 - offsetY) / scale / step) * step;
             worldY * scale + h / 2 + offsetY < h;
             worldY += step) {

            double screenY = h / 2 - worldY * scale + offsetY;
            gc.strokeLine(0, screenY, w, screenY);

            if (Math.abs(worldY) > 1e-6) {
                gc.setFill(textColor);
                gc.fillText(String.format("%.2f", worldY), w / 2 + offsetX + 2, screenY - 2);
            }
        }

        gc.setStroke(axisColor);
        gc.setLineWidth(2);
        gc.strokeLine(0, h / 2 + offsetY, w, h / 2 + offsetY);
        gc.strokeLine(w / 2 + offsetX, 0, w / 2 + offsetX, h);

        Color[] colors = {Color.RED, Color.BLUE, Color.GREEN, Color.ORANGE, Color.PURPLE};
        double hoverRadius = 8;

        List<double[]> intersections = new ArrayList<>();
        List<List<double[]>> functionPoints = new ArrayList<>();

        for (String exprString : functions) {
            List<double[]> pts = new ArrayList<>();
            try {
                Expression expr = new ExpressionBuilder(exprString).variable("x").build();

                for (double px = -w / 2; px < w / 2; px++) {
                    double x = (px - offsetX) / scale;
                    double y = expr.setVariable("x", x).evaluate();

                    double screenX = w / 2 + x * scale + offsetX;
                    double screenY = h / 2 - y * scale + offsetY;

                    pts.add(new double[]{screenX, screenY, x, y});
                }
            } catch (Exception ignored) {}
            functionPoints.add(pts);
        }

        // Function intersections
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
                        double screenX = w / 2 + x * scale + offsetX;
                        double screenY = h / 2 - y * scale + offsetY;
                        intersections.add(new double[]{screenX, screenY, x, y});
                    }
                }
            }
        }

        // Axis intersections + extrema
        for (List<double[]> pts : functionPoints) {
            for (int k = 1; k < pts.size(); k++) {
                double[] p1 = pts.get(k - 1);
                double[] p2 = pts.get(k);

                // y = 0
                if (p1[3] * p2[3] < 0) {
                    double t = p1[3] / (p1[3] - p2[3]);
                    double x = p1[2] + t * (p2[2] - p1[2]);
                    double screenX = w / 2 + x * scale + offsetX;
                    double screenY = h / 2 + offsetY;
                    intersections.add(new double[]{screenX, screenY, x, 0});
                }

                // x = 0
                if (p1[2] * p2[2] < 0) {
                    double t = p1[2] / (p1[2] - p2[2]);
                    double y = p1[3] + t * (p2[3] - p1[3]);
                    double screenX = w / 2 + offsetX;
                    double screenY = h / 2 - y * scale + offsetY;
                    intersections.add(new double[]{screenX, screenY, 0, y});
                }

                // Extrema (slope sign change)
                if (k > 1) {
                    double slopePrev = pts.get(k - 1)[3] - pts.get(k - 2)[3];
                    double slopeCurr = p2[3] - p1[3];
                    if (slopePrev * slopeCurr < 0) { // slope sign change
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
        double originX = w / 2 + offsetX;
        double originY = h / 2 + offsetY;
        intersections.add(new double[]{originX, originY, 0, 0});

        boolean snapped = false;

        for (int i = 0; i < functionPoints.size(); i++) {
            gc.setStroke(colors[i % colors.length]);
            gc.setLineWidth(2);
            List<double[]> pts = functionPoints.get(i);

            for (int k = 1; k < pts.size(); k++) {
                double[] p1 = pts.get(k - 1);
                double[] p2 = pts.get(k);
                gc.strokeLine(p1[0], p1[1], p2[0], p2[1]);
            }
        }

        for (double[] inter : intersections) {
            if (Math.hypot(mouseX - inter[0], mouseY - inter[1]) < hoverRadius) {
                drawHoverPoint(gc, inter[0], inter[1], inter[2], inter[3], axisColor, bg);
                snapped = true;
                break;
            }
        }

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
                            drawHoverPoint(gc, mouseX, lineY, worldX, worldY, axisColor, bg);
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

    private double chooseNiceStep(double rawStep) {
        double exp = Math.floor(Math.log10(rawStep));
        double base = Math.pow(10, exp);
        double fraction = rawStep / base;

        if (fraction < 1.5) return base;
        else if (fraction < 3) return 2 * base;
        else if (fraction < 7) return 5 * base;
        else return 10 * base;
    }

    private double easeInOutSine(double t) {
        return 1 - Math.cos((t * Math.PI) / 2);
    }

    private double quadraticEase(double t) {
        return t * t;
    }

    private double cubicEase(double t) {
        return t * t * t;
    }

    private double exponentialEase(double t) {
        return (t == 0) ? 0 : Math.pow(2, 10 * (t - 1));
    }

    private void addFunction(String func) {
        Platform.runLater(() -> {
            functions.clear();
            functions.add(func);
            redraw(canvas.getGraphicsContext2D());
            if (stage != null && !stage.isShowing()) {
                stage.show();
            }
        });
    }

    @Override
    public void stop() {
        instance = null;
        appLaunched = false;
    }
}
