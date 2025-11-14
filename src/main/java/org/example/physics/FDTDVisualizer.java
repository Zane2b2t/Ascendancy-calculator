// FDTDVisualizer.java
package org.example.physics;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FDTDVisualizer extends Application {
    private static final int GRID_SIZE = 400;

    private FDTDSimulation simulation;
    private FDTDSimulation incidentSim;
    private Canvas canvas;
    private Canvas canvas2;
    private SplitPane viewBox;
    private AnimationTimer timer;
    private boolean isRunning = false;
    private boolean sideBySide = false;
    private String colorScheme = "Fire";
    private double brightness = 1.0;
    private int simStepsPerFrame = 1;

    private ComboBox<String> sourceTypeCombo;
    private ComboBox<String> objectTypeCombo;
    private ComboBox<String> viewCombo;
    private ComboBox<String> colorSchemeCombo;
    private ComboBox<String> toolModeCombo;
    private Slider frequencySlider;
    private Slider conductivitySlider;
    private Slider brightnessSlider;
    private Slider speedSlider;
    private Label statsLabel;

    // Public static so FDTDSimulation can reference it in method signatures
    public static class SimObject {
        public String type;
        public int x, y;
        public int sizeX, sizeY;
        public double angle;
        public double conductivity;

        public SimObject(String type, int x, int y, int sizeX, int sizeY, double angle, double conductivity) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.angle = angle;
            this.conductivity = conductivity;
        }

        public double getEffectiveRadius() {
            return Math.max(sizeX, sizeY) * 1.5;
        }
    }

    private final List<SimObject> simObjects = new ArrayList<>();
    private SimObject selectedObject;
    private double dragStartX, dragStartY;
    private double dragStartMouseX, dragStartMouseY;

    @Override
    public void start(Stage primaryStage) {
        simulation = new FDTDSimulation(GRID_SIZE, GRID_SIZE);
        incidentSim = new FDTDSimulation(GRID_SIZE, GRID_SIZE);
        // incidentSim should start with no objects to represent the incident field
        incidentSim.rasterizeMaterials(Collections.emptyList());

        BorderPane root = new BorderPane();

        viewBox = new SplitPane();
        viewBox.setOrientation(Orientation.HORIZONTAL);
        canvas = new Canvas(800, 800);
        canvas.widthProperty().addListener((obs, oldVal, newVal) -> render());
        canvas.heightProperty().addListener((obs, oldVal, newVal) -> render());
        viewBox.getItems().add(canvas);

        canvas.setOnMousePressed(this::handleMousePressed);
        canvas.setOnMouseDragged(this::handleMouseDragged);
        canvas.setOnMouseReleased(this::handleMouseReleased);

        VBox controlPanel = createControlPanel();

        root.setCenter(viewBox);
        root.setRight(controlPanel);

        Scene scene = new Scene(root, 1300, 850);
        primaryStage.setTitle("FDTD EM Wave Visualizer");
        primaryStage.setScene(scene);
        primaryStage.show();

        timer = new AnimationTimer() {
            private long lastUpdate = 0;

            @Override
            public void handle(long now) {
                // ~60 FPS cap
                if (now - lastUpdate >= 16_666_666) {
                    if (isRunning) {
                        for (int s = 0; s < simStepsPerFrame; s++) {
                            simulation.update();
                            incidentSim.update();
                        }
                        render();
                        updateStats();
                    }
                    lastUpdate = now;
                }
            }
        };
        timer.start();

        // initial rasterize (empty object list)
        simulation.rasterizeMaterials(simObjects);
        incidentSim.rasterizeMaterials(Collections.emptyList());
        render();
    }

    private VBox createControlPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));
        panel.setPrefWidth(260);
        panel.setStyle("-fx-background-color: #f6f7f9;");

        Label titleLabel = new Label("FDTD Controls");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Button startButton = new Button("Start");
        startButton.setOnAction(e -> isRunning = true);
        Button pauseButton = new Button("Pause");
        pauseButton.setOnAction(e -> isRunning = false);
        Button resetButton = new Button("Reset");
        resetButton.setOnAction(e -> {
            isRunning = false;
            simulation.reset();
            incidentSim.reset();
            simObjects.clear();
            simulation.rasterizeMaterials(simObjects);
            incidentSim.rasterizeMaterials(Collections.emptyList());
            render();
        });
        HBox simControls = new HBox(8, startButton, pauseButton, resetButton);

        Label sourceLabel = new Label("Wave Source:");
        sourceTypeCombo = new ComboBox<>();
        sourceTypeCombo.getItems().addAll("Point Source", "Line Source", "Plane Wave", "Gaussian Pulse");
        sourceTypeCombo.setValue("Point Source");
        sourceTypeCombo.setOnAction(e -> updateSourceType());

        Label freqLabel = new Label("Frequency: 1.0 GHz");
        frequencySlider = new Slider(0.1, 6.0, 1.0);
        frequencySlider.setShowTickLabels(true);
        frequencySlider.setShowTickMarks(true);
        frequencySlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            freqLabel.setText(String.format("Frequency: %.2f GHz", newVal.doubleValue()));
            simulation.setFrequency(newVal.doubleValue());
            incidentSim.setFrequency(newVal.doubleValue());
        });

        Label toolLabel = new Label("Tool Mode:");
        toolModeCombo = new ComboBox<>();
        toolModeCombo.getItems().addAll("Add", "Move", "Resize", "Rotate", "Erase");
        toolModeCombo.setValue("Add");

        Label objectLabel = new Label("Object Type:");
        objectTypeCombo = new ComboBox<>();
        objectTypeCombo.getItems().addAll(
                "Metal Sphere",
                "Metal Box",
                "Dielectric Sphere",
                "Absorber",
                "Corner Reflector",
                "Wedge",
                "Stealth Wedge",
                "RAM Layer"
        );

        objectTypeCombo.setValue("Metal Sphere");

        Label condLabel = new Label("Conductivity: Medium");
        conductivitySlider = new Slider(0.0, 1.0, 0.5);
        conductivitySlider.setShowTickLabels(true);
        conductivitySlider.setShowTickMarks(true);
        conductivitySlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double val = newVal.doubleValue();
            String level = val < 0.33 ? "Low" : val < 0.67 ? "Medium" : "High";
            condLabel.setText("Conductivity: " + level);
        });

        Label viewLabel = new Label("Visualization:");
        viewCombo = new ComboBox<>();
        viewCombo.getItems().addAll("Electric Field", "Magnetic Field", "Power Density");
        viewCombo.setValue("Electric Field");
        viewCombo.setOnAction(e -> {
            simulation.setVisualizationMode(viewCombo.getValue());
            incidentSim.setVisualizationMode(viewCombo.getValue());
            render();
        });

        Label colorLabel = new Label("Color Scheme:");
        colorSchemeCombo = new ComboBox<>();
        colorSchemeCombo.getItems().addAll("Rainbow", "Grayscale", "Fire", "Plasma", "Thermal", "Ocean", "Spectrum");
        colorSchemeCombo.setValue("Fire");
        colorSchemeCombo.setOnAction(e -> {
            colorScheme = colorSchemeCombo.getValue();
            render();
        });

        Label brightLabel = new Label("Brightness: 1.0");
        brightnessSlider = new Slider(0.2, 5.0, 1.0);
        brightnessSlider.setShowTickLabels(true);
        brightnessSlider.setShowTickMarks(true);
        brightnessSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            brightness = newVal.doubleValue();
            brightLabel.setText(String.format("Brightness: %.2f", brightness));
            render();
        });

        Label speedLabel = new Label("Sim Speed: 1x");
        speedSlider = new Slider(1, 10, 1);
        speedSlider.setShowTickLabels(true);
        speedSlider.setShowTickMarks(true);
        speedSlider.setMajorTickUnit(1);
        speedSlider.setBlockIncrement(1);
        speedSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            simStepsPerFrame = newVal.intValue();
            speedLabel.setText(String.format("Sim Speed: %dx", simStepsPerFrame));
        });

        CheckBox sideBySideCheck = new CheckBox("Side-by-Side Scattered View");
        sideBySideCheck.setOnAction(e -> {
            sideBySide = sideBySideCheck.isSelected();
            updateLayout();
            render();
        });

        statsLabel = new Label("Time Step: 0\nMax Field: 0.0");
        statsLabel.setStyle("-fx-font-family: monospace; -fx-background-color: white; -fx-padding: 6;");

        Separator sep1 = new Separator();
        Separator sep2 = new Separator();
        Separator sep3 = new Separator();

        panel.getChildren().addAll(
                titleLabel,
                new Label("Simulation:"),
                simControls,
                sep1,
                sourceLabel,
                sourceTypeCombo,
                freqLabel,
                frequencySlider,
                speedLabel,
                speedSlider,
                sep2,
                toolLabel,
                toolModeCombo,
                objectLabel,
                objectTypeCombo,
                condLabel,
                conductivitySlider,
                sep3,
                viewLabel,
                viewCombo,
                colorLabel,
                colorSchemeCombo,
                brightLabel,
                brightnessSlider,
                sideBySideCheck,
                new Separator(),
                statsLabel
        );

        return panel;
    }

    private void updateLayout() {
        if (sideBySide) {
            if (canvas2 == null) {
                canvas2 = new Canvas(canvas.getWidth(), canvas.getHeight());
                canvas2.widthProperty().addListener((obs, oldVal, newVal) -> render());
                canvas2.heightProperty().addListener((obs, oldVal, newVal) -> render());
            }
            if (!viewBox.getItems().contains(canvas2)) {
                viewBox.getItems().setAll(canvas, canvas2);
            }
            viewBox.setDividerPositions(0.5);
        } else {
            viewBox.getItems().setAll(canvas);
        }
    }

    private void updateSourceType() {
        String type = sourceTypeCombo.getValue();
        simulation.setSourceType(type);
        incidentSim.setSourceType(type);
    }

    private void handleMousePressed(MouseEvent e) {
        double cellWidth = canvas.getWidth() / GRID_SIZE;
        double cellHeight = canvas.getHeight() / GRID_SIZE;
        int gridX = (int)(e.getX() / cellWidth);
        int gridY = (int)(e.getY() / cellHeight);

        String mode = toolModeCombo.getValue();
        if (mode.equals("Add")) addNewObject(gridX, gridY);
        else if (mode.equals("Erase")) eraseObject(gridX, gridY);
        else selectObject(gridX, gridY);

        dragStartX = gridX;
        dragStartY = gridY;
        dragStartMouseX = e.getX();
        dragStartMouseY = e.getY();
    }

    private void handleMouseDragged(MouseEvent e) {
        if (selectedObject == null) return;

        double cellWidth = canvas.getWidth() / GRID_SIZE;
        double cellHeight = canvas.getHeight() / GRID_SIZE;
        int gridX = (int)(e.getX() / cellWidth);
        int gridY = (int)(e.getY() / cellHeight);

        int deltaX = gridX - (int)dragStartX;
        int deltaY = gridY - (int)dragStartY;

        String mode = toolModeCombo.getValue();
        if (mode.equals("Move")) {
            selectedObject.x += deltaX;
            selectedObject.y += deltaY;
        } else if (mode.equals("Resize")) {
            selectedObject.sizeX = Math.max(1, selectedObject.sizeX + deltaX);
            selectedObject.sizeY = Math.max(1, selectedObject.sizeY + deltaY);
        } else if (mode.equals("Rotate")) {
            selectedObject.angle += deltaX * Math.PI / 180.0;
        }

        dragStartX = gridX;
        dragStartY = gridY;

        // Re-rasterize simulation materials and keep incidentSim as empty (incident only)
        simulation.rasterizeMaterials(simObjects);
        incidentSim.rasterizeMaterials(Collections.emptyList());
        render();
    }

    private void handleMouseReleased(MouseEvent e) {
        selectedObject = null;
    }

    private void addNewObject(int x, int y) {
        String objectType = objectTypeCombo.getValue();
        double conductivity = conductivitySlider.getValue();
        SimObject obj = new SimObject(objectType, x, y, 16, 16, 0, conductivity);
        simObjects.add(obj);
        simulation.rasterizeMaterials(simObjects);
        incidentSim.rasterizeMaterials(Collections.emptyList());
        render();
    }

    private void eraseObject(int x, int y) {
        SimObject obj = findClosestObject(x, y);
        if (obj != null) {
            simObjects.remove(obj);
            simulation.rasterizeMaterials(simObjects);
            incidentSim.rasterizeMaterials(Collections.emptyList());
            render();
        }
    }

    private void selectObject(int x, int y) {
        selectedObject = findClosestObject(x, y);
    }

    private SimObject findClosestObject(int x, int y) {
        SimObject closest = null;
        double minDist = Double.MAX_VALUE;
        for (SimObject obj : simObjects) {
            double dist = Math.hypot(obj.x - x, obj.y - y);
            if (dist < obj.getEffectiveRadius() && dist < minDist) {
                minDist = dist;
                closest = obj;
            }
        }
        return closest;
    }

    private void render() {
        double[][] totalField = getVisField(simulation);
        renderOnCanvas(canvas, totalField, simulation, false);

        if (sideBySide) {
            double[][] scatteredField = getScatteredVisField();
            renderOnCanvas(canvas2, scatteredField, simulation, true);
        }
    }

    private double[][] getVisField(FDTDSimulation sim) {
        String mode = sim.getVisualizationMode();
        double[][] ez = sim.getEz();
        double[][] hx = sim.getHx();
        double[][] hy = sim.getHy();
        double[][] f = new double[GRID_SIZE][GRID_SIZE];

        switch (mode) {
            case "Electric Field":
                for (int i=0;i<GRID_SIZE;i++) for(int j=0;j<GRID_SIZE;j++) f[i][j] = Math.abs(ez[i][j]);
                break;
            case "Magnetic Field":
                for (int i=0;i<GRID_SIZE;i++) for(int j=0;j<GRID_SIZE;j++) f[i][j] = Math.sqrt(hx[i][j]*hx[i][j]+hy[i][j]*hy[i][j]);
                break;
            case "Power Density":
                for (int i=0;i<GRID_SIZE;i++) for(int j=0;j<GRID_SIZE;j++) {
                    double hmag = Math.sqrt(hx[i][j]*hx[i][j]+hy[i][j]*hy[i][j]);
                    f[i][j] = Math.abs(ez[i][j]*hmag);
                }
                break;
        }
        return f;
    }

    private double[][] getScatteredVisField() {
        double[][] totEz = simulation.getEz();
        double[][] incEz = incidentSim.getEz();
        double[][] totHx = simulation.getHx();
        double[][] incHx = incidentSim.getHx();
        double[][] totHy = simulation.getHy();
        double[][] incHy = incidentSim.getHy();

        double[][] scatEz = new double[GRID_SIZE][GRID_SIZE];
        double[][] scatHx = new double[GRID_SIZE][GRID_SIZE];
        double[][] scatHy = new double[GRID_SIZE][GRID_SIZE];

        for (int i=0;i<GRID_SIZE;i++) for (int j=0;j<GRID_SIZE;j++) {
            scatEz[i][j] = totEz[i][j]-incEz[i][j];
            scatHx[i][j] = totHx[i][j]-incHx[i][j];
            scatHy[i][j] = totHy[i][j]-incHy[i][j];
        }

        double[][] f = new double[GRID_SIZE][GRID_SIZE];
        switch(simulation.getVisualizationMode()) {
            case "Electric Field":
                for (int i=0;i<GRID_SIZE;i++) for(int j=0;j<GRID_SIZE;j++) f[i][j] = Math.abs(scatEz[i][j]);
                break;
            case "Magnetic Field":
                for (int i=0;i<GRID_SIZE;i++) for(int j=0;j<GRID_SIZE;j++) f[i][j] = Math.sqrt(scatHx[i][j]*scatHx[i][j]+scatHy[i][j]*scatHy[i][j]);
                break;
            case "Power Density":
                for (int i=0;i<GRID_SIZE;i++) for(int j=0;j<GRID_SIZE;j++){
                    double hmag = Math.sqrt(scatHx[i][j]*scatHx[i][j]+scatHy[i][j]*scatHy[i][j]);
                    f[i][j] = Math.abs(scatEz[i][j]*hmag);
                }
                break;
        }
        return f;
    }

    private void renderOnCanvas(Canvas c, double[][] field, FDTDSimulation sim, boolean isScattered) {
        GraphicsContext gc = c.getGraphicsContext2D();
        double canvasWidth = c.getWidth();
        double canvasHeight = c.getHeight();
        WritableImage image = new WritableImage(GRID_SIZE, GRID_SIZE);
        PixelWriter pw = image.getPixelWriter();

        // Dynamic normalization (per-frame) with brightness control
        double maxField = 1e-12;
        for (int i=0;i<GRID_SIZE;i++) for(int j=0;j<GRID_SIZE;j++)
            if (Double.isFinite(field[i][j])) maxField = Math.max(maxField, Math.abs(field[i][j]));

        // apply brightness scaling (larger brightness => show smaller signals)
        maxField /= Math.max(1e-12, brightness);

        for (int i=0;i<GRID_SIZE;i++){
            for(int j=0;j<GRID_SIZE;j++){
                double val = field[i][j];
                if (!Double.isFinite(val)) val = 0.0;
                double normalized = Math.min(1.0, Math.abs(val)/maxField);
                normalized = Math.pow(normalized, 0.45); // gamma correction

                Color color = getColor(normalized);
                // Draw objects on top if present
                if (simulation.isObject(i, j)) color = Color.DARKGRAY;
                pw.setColor(i,j,color);
            }
        }

        // draw to canvas scaled
        gc.drawImage(image,0,0,canvasWidth,canvasHeight);

        // Draw source indicator
        int srcX = sim.getSourceX();
        int srcY = sim.getSourceY();
        double cellW = canvasWidth/GRID_SIZE;
        double cellH = canvasHeight/GRID_SIZE;
        gc.setStroke(Color.RED);
        gc.setLineWidth(2);
        gc.strokeOval(srcX*cellW-6,srcY*cellH-6,12,12);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font(14));
        gc.fillText(isScattered ? "Scattered Field" : "Total Field", 10, 20);
    }

    private Color getColor(double normalized) {
        switch(colorScheme){
            case "Grayscale": return Color.gray(normalized);
            case "Fire": return Color.color(Math.min(1.0,normalized*1.6), Math.min(1.0, normalized*0.9), Math.min(1.0, normalized*0.1));
            case "Plasma": return Color.color(Math.min(1.0,0.3+normalized*0.7), Math.min(1.0, normalized*0.2), Math.min(1.0,1.0-normalized*0.6));
            case "Thermal": return Color.color(Math.min(1.0,normalized*1.0), Math.min(1.0, normalized*0.6), 0.05);
            case "Ocean": return Color.color(0.05, Math.min(1.0,0.4+normalized*0.6), Math.min(1.0, 0.6+normalized*0.4));
            case "Spectrum": return Color.hsb(normalized*300,1.0,1.0);
            case "Rainbow": default: return Color.hsb(normalized*270,1.0,1.0);
        }
    }

    private void updateStats() {
        statsLabel.setText(String.format("Time Step: %d\nMax Field: %.4e",
                simulation.getTimeStep(),
                simulation.getMaxField()));
    }

    public static void main(String[] args) {
        launch(args);
    }
}
