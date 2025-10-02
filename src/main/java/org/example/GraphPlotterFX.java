package org.example;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
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

    private final ObservableList<String> functions = FXCollections.observableArrayList();

    private Canvas canvas;
    private double mouseX = -1, mouseY = -1;

    private static volatile String initialFunction = "sin(x)";
    private static volatile GraphPlotterFX instance;   // keep reference
    private static volatile boolean appLaunched = false;
    private Stage stage;

    private double zoomVelocity = 0;
    private final double zoomFriction = 0.85;
    private final double zoomSensitivity = 0.001;
    private String zoomMode = "None";
    private double zoomFactor = 1.0;

    private volatile String previewExpr = ""; // the expression currently being typed
    private volatile int previewReplaceIndex = -1; // if >=0, preview will replace that function index

    private boolean zoomToMouse = false; // whether zoom centers on mouse

    private enum Theme {LIGHT, DARK, BLACK_BLUE}
    private Theme currentTheme = Theme.LIGHT;

    private Button darkModeBtnRef;
    private HBox controlsRef;
    private ListView<String> listViewRef;
    private Slider zoomFactorSliderRef;

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

        TextField functionInput = new TextField(initialFunction);
        Button addBtn = new Button("Add");
        Button updateBtn = new Button("Update");
        Button removeBtn = new Button("Remove");

        updateBtn.setDisable(true);
        removeBtn.setDisable(true);

        ListView<String> listView = new ListView<>(functions);
        listView.setPrefWidth(220);
        listViewRef = listView;

        VBox leftBox = new VBox(8, new Label("Functions"), listView);
        leftBox.setPadding(new Insets(8));
        leftBox.setPrefWidth(220);
        leftBox.setMinWidth(80);
        leftBox.setMaxWidth(Double.MAX_VALUE);

        StackPane centerPane = new StackPane(canvas);
        centerPane.setMinWidth(300);

        canvas.widthProperty().bind(centerPane.widthProperty());
        canvas.heightProperty().bind(centerPane.heightProperty());

        SplitPane split = new SplitPane();
        split.getItems().addAll(leftBox, centerPane);
        split.setDividerPositions(0.22);

        SplitPane.setResizableWithParent(leftBox, true);
        SplitPane.setResizableWithParent(centerPane, true);

        HBox.setHgrow(functionInput, Priority.ALWAYS);

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
        zoomFactorSliderRef = zoomFactorSlider;

        darkModeBtnRef = new Button("Toggle Dark Mode");
        Button resetPosBtn = new Button("Reset Position");
        Button resetScaleBtn = new Button("Reset Scale");

        resetPosBtn.setOnAction(e -> {
            offsetX = 0;
            offsetY = 0;
            redraw(gc);
        });
        resetScaleBtn.setOnAction(e -> {
            scale = 50;
            redraw(gc);
        });

        darkModeBtnRef.setOnAction(e -> {
            currentTheme = (currentTheme == Theme.DARK) ? Theme.LIGHT : Theme.DARK;
            applyThemeToScene(stage.getScene());
            redraw(gc);
        });

        CheckBox zoomToMouseCheckbox = new CheckBox("Zoom to mouse");
        zoomToMouseCheckbox.setSelected(zoomToMouse);
        zoomToMouseCheckbox.selectedProperty().addListener((obs, oldV, newV) -> zoomToMouse = newV);

        ComboBox<String> themeDropdown = new ComboBox<>();
        themeDropdown.getItems().addAll("Dark", "Light", "Black & Blue (Fugitive Aero)");
        themeDropdown.setValue("Dark");
        themeDropdown.valueProperty().addListener((obs, oldV, newV) -> {
            switch (newV) {
                case "Light" -> currentTheme = Theme.LIGHT;
                case "Black & Blue (Fugitive Aero)" -> currentTheme = Theme.BLACK_BLUE;
                default -> currentTheme = Theme.DARK;
            }
            applyThemeToScene(stage.getScene());
            redraw(gc);
        });

        HBox controls = new HBox(10, functionInput, addBtn, updateBtn, removeBtn,
                new Label("Zoom Mode:"), zoomModeDropdown,
                new Label("Zoom Factor:"), zoomFactorSlider,
                zoomToMouseCheckbox, themeDropdown,
                darkModeBtnRef, resetPosBtn, resetScaleBtn);
        controls.setPadding(new Insets(8));
        controlsRef = controls;

        BorderPane root = new BorderPane();
        root.setCenter(split);
        root.setBottom(controls);

        Scene scene = new Scene(root, 1200, 800);

        applyThemeToScene(scene);

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
            double delta = e.getDeltaY();
            double baseFactor = (delta > 0) ? 1.1 : 0.9;

            if (zoomMode.equals("None")) {
                applyScale(baseFactor, e.getX(), e.getY(), zoomToMouse);
                redraw(gc);
            } else {
                zoomVelocity += delta * zoomSensitivity * zoomFactor;
                mouseX = e.getX();
                mouseY = e.getY();
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

        Platform.runLater(() -> {
            installListCellFactory(listView);

            Node thumb = zoomFactorSliderRef.lookup(".thumb");
            if (thumb != null) {
                if (currentTheme == Theme.BLACK_BLUE || currentTheme == Theme.DARK) thumb.setStyle("-fx-background-color: white; -fx-background-radius: 8;");
                else thumb.setStyle("");
            }

            if (scene != null) applyThemeToScene(scene);
        });

        functions.add(initialFunction);

        functionInput.textProperty().addListener((obs, oldText, newText) -> {
            previewExpr = newText.trim();
            int sel = listView.getSelectionModel().getSelectedIndex();
            previewReplaceIndex = (sel >= 0) ? sel : -1;
            redraw(gc);
        });

        addBtn.setOnAction(e -> {
            String expr = functionInput.getText().trim();
            if (!expr.isEmpty()) {
                functions.add(expr);
                functionInput.clear();
                previewExpr = "";
                listView.getSelectionModel().clearSelection();
                previewReplaceIndex = -1;
                redraw(gc);
            }
        });

        updateBtn.setOnAction(e -> {
            int sel = listView.getSelectionModel().getSelectedIndex();
            if (sel >= 0) {
                String expr = functionInput.getText().trim();
                if (!expr.isEmpty()) {
                    functions.set(sel, expr);
                    previewExpr = "";
                    listView.getSelectionModel().clearSelection();
                    previewReplaceIndex = -1;
                    redraw(gc);
                }
            }
        });

        removeBtn.setOnAction(e -> {
            int sel = listView.getSelectionModel().getSelectedIndex();
            if (sel >= 0) {
                functions.remove(sel);
                functionInput.clear();
                previewExpr = "";
                listView.getSelectionModel().clearSelection();
                previewReplaceIndex = -1;
                redraw(gc);
            }
        });

        listView.getSelectionModel().selectedIndexProperty().addListener((obs, oldIdx, newIdx) -> {
            int sel = newIdx.intValue();
            if (sel >= 0) {
                String selectedExpr = functions.get(sel);
                functionInput.setText(selectedExpr);
                updateBtn.setDisable(false);
                removeBtn.setDisable(false);
                previewReplaceIndex = sel;
                previewExpr = selectedExpr;
            } else {
                updateBtn.setDisable(true);
                removeBtn.setDisable(true);
                previewReplaceIndex = -1;
                previewExpr = "";
            }
            redraw(gc);
        });

        listView.setCellFactory(lv -> {
            ListCell<String> cell = new ListCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setStyle("");
                    } else {
                        setText(item);
                        switch (currentTheme) {
                            case DARK -> setStyle("-fx-text-fill: #dcdcdc; -fx-background-color: transparent;");
                            case BLACK_BLUE -> setStyle("-fx-text-fill: #9fdcff; -fx-background-color: transparent;");
                            default -> setStyle("");
                        }
                    }
                }
            };

            ContextMenu cm = new ContextMenu();
            MenuItem editItem = new MenuItem("Edit");
            MenuItem removeItem = new MenuItem("Remove");
            MenuItem duplicateItem = new MenuItem("Duplicate");

            editItem.setOnAction(e -> {
                int idx = cell.getIndex();
                if (idx >= 0 && idx < functions.size()) {
                    listView.getSelectionModel().select(idx);
                }
            });
            removeItem.setOnAction(e -> {
                int idx = cell.getIndex();
                if (idx >= 0 && idx < functions.size()) {
                    functions.remove(idx);
                }
            });
            duplicateItem.setOnAction(e -> {
                int idx = cell.getIndex();
                if (idx >= 0 && idx < functions.size()) {
                    functions.add(functions.get(idx));
                }
            });

            cm.getItems().addAll(editItem, duplicateItem, removeItem);

            cell.emptyProperty().addListener((obs, wasEmpty, isNowEmpty) -> {
                if (isNowEmpty) {
                    cell.setContextMenu(null);
                } else {
                    cell.setContextMenu(cm);
                }
            });

            cell.setOnMouseClicked(evt -> {
                if (!cell.isEmpty() && evt.getButton() == MouseButton.PRIMARY && evt.getClickCount() == 2) {
                    // Double click -> edit
                    listView.getSelectionModel().select(cell.getIndex());
                }
            });

            return cell;
        });

        // initial draw
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

                    double useMouseX = (mouseX >= 0) ? mouseX : canvas.getWidth() / 2.0;
                    double useMouseY = (mouseY >= 0) ? mouseY : canvas.getHeight() / 2.0;

                    if (zoomToMouse) applyScale(factor, useMouseX, useMouseY, true);
                    else scale *= factor;

                    zoomVelocity *= zoomFriction;
                    redraw(gc);
                }
            }
        }.start();
    }


    private void installListCellFactory(ListView<String> listView) {
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    switch (currentTheme) {
                        case DARK -> setStyle("-fx-text-fill: #dcdcdc; -fx-background-color: transparent;");
                        case BLACK_BLUE -> setStyle("-fx-text-fill: #9fdcff; -fx-background-color: transparent;");
                        default -> setStyle("");
                    }
                }
            }
        });
    }

    private void applyThemeToScene(Scene scene) {
        if (scene == null) return;
        Parent root = scene.getRoot();

        String accent = "#7fd3ff";
        String focused = "transparent";

        switch (currentTheme) {
            case DARK -> accent = "#e6e6e6";
            case BLACK_BLUE -> accent = "#9fdcff";
            default -> accent = "#007acc";
        }

        root.setStyle(String.join(";",
                "-fx-accent: " + accent,
                "-fx-focus-color: " + focused,
                "-fx-faint-focus-color: " + focused
        ));

        applyThemeToControls(root);

        if (listViewRef != null) installListCellFactory(listViewRef);

        if (zoomFactorSliderRef != null) {
            Node thumb = zoomFactorSliderRef.lookup(".thumb");
            if (thumb != null) {
                if (currentTheme == Theme.BLACK_BLUE || currentTheme == Theme.DARK) thumb.setStyle("-fx-background-color: white; -fx-background-radius: 8;");
                else thumb.setStyle("");
            }
        }
    }

    private void applyThemeToControls(Node rootNode) {
        if (rootNode == null) return;

        if (rootNode instanceof Button b) {
            switch (currentTheme) {
                case DARK -> b.setStyle("-fx-background-color: linear-gradient(#2b2b2b, #1f1f1f); -fx-text-fill: #eaeaea; -fx-background-radius:6; -fx-border-color: #3a3a3a;");
                case BLACK_BLUE -> b.setStyle("-fx-background-color: linear-gradient(#002233, #001522); -fx-text-fill: #9fdcff; -fx-background-radius:8; -fx-border-color: rgba(127,211,255,0.15);");
                default -> b.setStyle("");
            }
        } else if (rootNode instanceof TextField tf) {
            switch (currentTheme) {
                case DARK -> tf.setStyle("-fx-control-inner-background: #141414; -fx-text-fill: #eaeaea; -fx-background-radius:6; -fx-border-color: #333;");
                case BLACK_BLUE -> tf.setStyle("-fx-control-inner-background: #001219; -fx-text-fill: #9fdcff; -fx-background-radius:6; -fx-border-color: rgba(127,211,255,0.12);");
                default -> tf.setStyle("");
            }
        } else if (rootNode instanceof ComboBox<?> cb) {
            switch (currentTheme) {
                case DARK -> cb.setStyle("-fx-background-color: #1b1b1b; -fx-text-fill: #eaeaea; -fx-border-color: #333;");
                case BLACK_BLUE -> cb.setStyle("-fx-background-color: #001528; -fx-text-fill: #9fdcff; -fx-border-color: rgba(127,211,255,0.12);");
                default -> cb.setStyle("");
            }
        } else if (rootNode instanceof Slider s) {
            switch (currentTheme) {
                case DARK -> s.setStyle("-fx-control-inner-background: #1a1a1a; -fx-background-color: linear-gradient(#2b2b2b, #1f1f1f);");
                case BLACK_BLUE -> s.setStyle("-fx-control-inner-background: transparent; -fx-background-color: transparent;");
                default -> s.setStyle("");
            }
        } else if (rootNode instanceof Label l) {
            switch (currentTheme) {
                case DARK -> l.setStyle("-fx-text-fill: #dcdcdc;");
                case BLACK_BLUE -> l.setStyle("-fx-text-fill: #9fdcff;");
                default -> l.setStyle("");
            }
        } else if (rootNode instanceof CheckBox cbx) {
            switch (currentTheme) {
                case DARK -> cbx.setStyle("-fx-text-fill: #e6e6e6;");
                case BLACK_BLUE -> cbx.setStyle("-fx-text-fill: #9fdcff;");
                default -> cbx.setStyle("");
            }
        } else if (rootNode instanceof ListView<?> lv) {
            switch (currentTheme) {
                case DARK -> lv.setStyle("-fx-control-inner-background: #101010; -fx-background-color: #0d0d0d; -fx-text-fill: #e6e6e6;");
                case BLACK_BLUE -> lv.setStyle("-fx-control-inner-background: transparent; -fx-background-color: transparent; -fx-text-fill: #9fdcff;");
                default -> lv.setStyle("");
            }
        } else if (rootNode instanceof Pane p) {
            Background bg = null;
            switch (currentTheme) {
                case DARK -> bg = new Background(new BackgroundFill(Color.web("#0b0b0b"), CornerRadii.EMPTY, Insets.EMPTY));
                case BLACK_BLUE -> {
                    Stop[] stops = new Stop[]{new Stop(0, Color.web("#000010")), new Stop(1, Color.web("#00162b"))};
                    LinearGradient lg = new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE, stops);
                    bg = new Background(new BackgroundFill(lg, CornerRadii.EMPTY, Insets.EMPTY));
                }
                default -> bg = new Background(new BackgroundFill(Color.WHITE, CornerRadii.EMPTY, Insets.EMPTY));
            }
            p.setBackground(bg);
        }

        if (rootNode instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                applyThemeToControls(child);
            }
        }
    }

    private void applyScale(double factor, double screenX, double screenY, boolean zoomToMouse) {
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

    private void redraw(GraphicsContext gc) {
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        Paint bgPaint = Color.WHITE;
        Color gridColor = Color.LIGHTGRAY;
        Color axisColor = Color.BLACK;
        Color textColor = Color.GRAY;

        switch (currentTheme) {
            case DARK -> {
                bgPaint = Color.web("#0b0b0b");
                gridColor = Color.web("#2a2a2a");
                axisColor = Color.web("#e6e6e6");
                textColor = Color.web("#cfcfcf");
            }
            case BLACK_BLUE -> {
                Stop[] stops = new Stop[]{new Stop(0, Color.web("#000010")), new Stop(1, Color.web("#00162b"))};
                bgPaint = new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE, stops);
                gridColor = Color.web("#06253a");
                axisColor = Color.web("#7fd3ff");
                textColor = Color.web("#9fdcff");
            }
            default -> {
                bgPaint = Color.WHITE;
                gridColor = Color.LIGHTGRAY;
                axisColor = Color.BLACK;
                textColor = Color.GRAY;
            }
        }

        gc.setFill(bgPaint);
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

        Color[] colors = {Color.web("#ff6b6b"), Color.web("#4da6ff"), Color.web("#7bffb2"), Color.web("#ffb86b"), Color.web("#c087ff")};
        double hoverRadius = 8;

        List<double[]> intersections = new ArrayList<>();
        List<List<double[]>> functionPoints = new ArrayList<>();

        for (int fi = 0; fi < functions.size(); fi++) {
            String exprString = functions.get(fi);
            if (previewReplaceIndex == fi && previewExpr != null && !previewExpr.isEmpty()) {
                exprString = previewExpr;
            }

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
            } catch (Exception ignored) {
            }
            functionPoints.add(pts);
        }

        boolean drewStandalonePreview = false;
        if ((previewExpr != null && !previewExpr.isEmpty()) && previewReplaceIndex < 0) {
            List<double[]> pts = new ArrayList<>();
            try {
                Expression expr = new ExpressionBuilder(previewExpr).variable("x").build();
                for (double px = -w / 2; px < w / 2; px++) {
                    double x = (px - offsetX) / scale;
                    double y = expr.setVariable("x", x).evaluate();
                    double screenX = w / 2 + x * scale + offsetX;
                    double screenY = h / 2 - y * scale + offsetY;
                    pts.add(new double[]{screenX, screenY, x, y});
                }

                functionPoints.add(pts);
                drewStandalonePreview = true;
            } catch (Exception ignored) {
            }
        }

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

        // Draw functions
        for (int i = 0; i < functionPoints.size(); i++) {
            // If this was the standalone preview we added as last element, use preview style
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

            // reset dashes/alpha
            gc.setLineDashes(null);
            gc.setGlobalAlpha(1.0);
        }

        // Hover / snap-to points (intersections + on-curve)
        for (double[] inter : intersections) {
            if (Math.hypot(mouseX - inter[0], mouseY - inter[1]) < hoverRadius) {
                drawHoverPoint(gc, inter[0], inter[1], inter[2], inter[3], axisColor, (Color) ((bgPaint instanceof Color) ? bgPaint : Color.BLACK));
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
                            drawHoverPoint(gc, mouseX, lineY, worldX, worldY, axisColor, (Color) ((bgPaint instanceof Color) ? bgPaint : Color.BLACK));
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
