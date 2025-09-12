package org.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.TextField;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
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

    /**
     * Call this to open (or re-open) the graph window and plot 'func'.
     * This will call Application.launch(...) only once.
     */
    public static synchronized void launchGraph(String func) {
        initialFunction = func;
        if (!appLaunched) {
            appLaunched = true;
            // Launch JavaFX on a new thread (only once per JVM)
            new Thread(() -> Application.launch(GraphPlotterFX.class)).start();
        } else {
            // If the FX app/instance is already up, add the function (it will show the stage if hidden)
            if (instance != null) {
                instance.addFunction(func);
            } else {
                // If instance not yet created but app launched, initialFunction was updated,
                // start() will pick it up when it runs.
            }
        }
    }

    public static boolean isAppLaunched() {
        return appLaunched;
    }

    /**
     * Request JavaFX to shutdown. Call when your whole program is exiting.
     */
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
        // Important: keep FX runtime alive when last window is hidden/closed
        Platform.setImplicitExit(false);

        instance = this;
        this.stage = stage;

        canvas = new Canvas(900, 600);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        TextField functionInput = new TextField(initialFunction);
        Button addBtn = new Button("Add Function");

        addBtn.setOnAction(e -> {
            String expr = functionInput.getText().trim();
            if (!expr.isEmpty()) {
                functions.add(expr);
                redraw(gc);
            }
        });

        HBox controls = new HBox(10, functionInput, addBtn);
        BorderPane root = new BorderPane(canvas, null, null, controls, null);
        Scene scene = new Scene(root);

        // Mouse dragging (panning)
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

        // Zooming with scroll
        canvas.setOnScroll(e -> {
            double zoomFactor = (e.getDeltaY() > 0) ? 1.1 : 0.9;
            scale *= zoomFactor;
            redraw(gc);
        });

        // Track mouse for hover display
        canvas.setOnMouseMoved(e -> {
            mouseX = e.getX();
            mouseY = e.getY();
            redraw(gc);
        });

        stage.setTitle("Modern Graph Plotter");
        stage.setScene(scene);

        // Instead of letting the stage be disposed, consume the close request and hide the window.
        // This prevents the Stage from being destroyed and avoids exiting the FX runtime.
        stage.setOnCloseRequest(e -> {
            e.consume();  // prevent default close behavior
            stage.hide(); // just hide the window so it can be shown again later
        });

        stage.show();

        // add starting function from the latest requested initialFunction
        functions.add(initialFunction);
        redraw(gc);
    }

    private void redraw(GraphicsContext gc) {
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        double w = canvas.getWidth();
        double h = canvas.getHeight();

        // Draw grid + axis numbers
        gc.setStroke(Color.LIGHTGRAY);
        gc.setLineWidth(1);

        double step = scale; // one unit in pixels
        // vertical grid lines
        for (double x = offsetX % step; x < w; x += step) {
            gc.strokeLine(x, 0, x, h);
            double worldX = (x - w / 2 - offsetX) / scale;
            if (Math.abs(worldX) > 0.01) {
                gc.setFill(Color.GRAY);
                gc.fillText(String.format("%.1f", worldX), x + 2, h / 2 + offsetY - 2);
            }
        }
        // horizontal grid lines
        for (double y = offsetY % step; y < h; y += step) {
            gc.strokeLine(0, y, w, y);
            double worldY = (h / 2 - y + offsetY) / scale;
            if (Math.abs(worldY) > 0.01) {
                gc.setFill(Color.GRAY);
                gc.fillText(String.format("%.1f", worldY), w / 2 + offsetX + 2, y - 2);
            }
        }

        // Axes
        gc.setStroke(Color.BLACK);
        gc.setLineWidth(2);
        gc.strokeLine(0, h / 2 + offsetY, w, h / 2 + offsetY); // x-axis
        gc.strokeLine(w / 2 + offsetX, 0, w / 2 + offsetX, h); // y-axis

        // Plot functions
        Color[] colors = {Color.RED, Color.BLUE, Color.GREEN, Color.ORANGE, Color.PURPLE};
        int colorIndex = 0;

        double hoverRadius = 5; // px for detecting hover
        boolean pointShown = false;

        for (String exprString : functions) {
            try {
                Expression expr = new ExpressionBuilder(exprString).variable("x").build();
                gc.setStroke(colors[colorIndex % colors.length]);
                gc.setLineWidth(2);

                double prevX = -w / 2 / scale;
                double prevY = expr.setVariable("x", prevX).evaluate();

                for (double px = -w / 2; px < w / 2; px++) {
                    double x = px / scale;
                    double y = expr.setVariable("x", x).evaluate();

                    double screenX1 = w / 2 + prevX * scale + offsetX;
                    double screenY1 = h / 2 - prevY * scale + offsetY;
                    double screenX2 = w / 2 + x * scale + offsetX;
                    double screenY2 = h / 2 - y * scale + offsetY;

                    gc.strokeLine(screenX1, screenY1, screenX2, screenY2);

                    // Hover detection
                    if (mouseX >= Math.min(screenX1, screenX2) &&
                            mouseX <= Math.max(screenX1, screenX2)) {
                        double t = (mouseX - screenX1) / (screenX2 - screenX1 + 1e-9);
                        double lineY = screenY1 + t * (screenY2 - screenY1);
                        if (Math.abs(mouseY - lineY) < hoverRadius) {
                            gc.setFill(Color.BLACK);
                            gc.fillOval(mouseX - 4, lineY - 4, 8, 8);

                            double worldX = (mouseX - w / 2 - offsetX) / scale;
                            double worldY = expr.setVariable("x", worldX).evaluate();

                            gc.fillText(String.format("(%.2f, %.2f)", worldX, worldY),
                                    mouseX + 10, lineY - 10);
                            pointShown = true;
                        }
                    }

                    prevX = x;
                    prevY = y;
                }
            } catch (Exception ignored) {}
            colorIndex++;
        }

        if (!pointShown) {
            // nothing special to draw when not hovering
        }
    }

    private void addFunction(String func) {
        Platform.runLater(() -> {
            // replace with desired behavior:
            // if you want to keep old functions comment out the clear() line.
            functions.clear(); // reset if you want only the new one
            functions.add(func);
            redraw(canvas.getGraphicsContext2D());
            if (stage != null && !stage.isShowing()) {
                stage.show(); // re-show if hidden
            }
        });
    }

    @Override
    public void stop() {
        // called when FX runtime actually stops
        instance = null;
        appLaunched = false;
    }
}
