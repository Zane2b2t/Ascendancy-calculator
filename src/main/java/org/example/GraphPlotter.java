package org.example;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class GraphPlotter extends Application {

    private final GraphLogic logic = new GraphLogic();
    private final GraphThemeManager themeManager = new GraphThemeManager();
    private final GraphRenderer renderer = new GraphRenderer(logic, themeManager);

    private double dragStartX, dragStartY;
    private double lastMouseX = Double.NaN, lastMouseY = Double.NaN; // track for zoom-to-mouse during animated zoom

    private final ObservableList<String> functions = FXCollections.observableArrayList();

    private Canvas canvas;

    private static volatile String initialFunction = "sin(x)";
    private static volatile GraphPlotter instance;
    private static volatile boolean appLaunched = false;
    private Stage stage;

    private ListView<String> listViewRef;
    private Slider zoomFactorSliderRef;

    public static synchronized void launchGraph(String func) {
        initialFunction = func;
        if (!appLaunched) {
            appLaunched = true;
            new Thread(() -> Application.launch(GraphPlotter.class)).start();
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
        zoomModeDropdown.valueProperty().addListener((obs, oldVal, newVal) -> logic.setZoomMode(newVal));

        Slider zoomFactorSlider = new Slider(0.1, 3.0, 1.0);
        zoomFactorSlider.setShowTickMarks(true);
        zoomFactorSlider.setShowTickLabels(true);
        zoomFactorSlider.setMajorTickUnit(0.5);
        zoomFactorSlider.setMinorTickCount(4);
        zoomFactorSlider.setBlockIncrement(0.1);
        zoomFactorSlider.valueProperty().addListener((obs, oldVal, newVal) -> logic.setZoomFactor(newVal.doubleValue()));
        zoomFactorSliderRef = zoomFactorSlider;

        Button darkModeBtnRef = new Button("Toggle Dark Mode");
        Button resetPosBtn = new Button("Reset Position");
        Button resetScaleBtn = new Button("Reset Scale");

        resetPosBtn.setOnAction(e -> {
            logic.resetPosition();
            redraw(gc);
        });
        resetScaleBtn.setOnAction(e -> {
            logic.resetScale();
            redraw(gc);
        });

        darkModeBtnRef.setOnAction(e -> {
            GraphThemeManager.Theme current = themeManager.getCurrentTheme();
            themeManager.setCurrentTheme(current == GraphThemeManager.Theme.DARK ? 
                GraphThemeManager.Theme.LIGHT : GraphThemeManager.Theme.DARK);
            themeManager.applyThemeToScene(stage.getScene());
            redraw(gc);
        });

        CheckBox zoomToMouseCheckbox = new CheckBox("Zoom to mouse");
        zoomToMouseCheckbox.setSelected(logic.isZoomToMouse());
        zoomToMouseCheckbox.selectedProperty().addListener((obs, oldV, newV) -> logic.setZoomToMouse(newV));

        ComboBox<String> themeDropdown = new ComboBox<>();
        themeDropdown.getItems().addAll("Forest (Discord)", "Dark", "Light", "Black & Blue (Fugitive Aero)");
        themeDropdown.setValue("Forest (Discord)");
        themeDropdown.valueProperty().addListener((obs, oldV, newV) -> {
            switch (newV) {
                case "Forest" -> themeManager.setCurrentTheme(GraphThemeManager.Theme.FOREST);
                case "Light" -> themeManager.setCurrentTheme(GraphThemeManager.Theme.LIGHT);
                case "Black & Blue" -> themeManager.setCurrentTheme(GraphThemeManager.Theme.BLACK_BLUE);
                default -> themeManager.setCurrentTheme(GraphThemeManager.Theme.DARK);
            }
            themeManager.applyThemeToScene(stage.getScene());
            redraw(gc);
        });

        HBox controls = new HBox(10, functionInput, addBtn, updateBtn, removeBtn,
                new Label("Zoom Mode:"), zoomModeDropdown,
                new Label("Zoom Factor:"), zoomFactorSlider,
                zoomToMouseCheckbox, themeDropdown,
                darkModeBtnRef, resetPosBtn, resetScaleBtn);
        controls.setPadding(new Insets(8));

        BorderPane root = new BorderPane();
        root.setCenter(split);
        root.setBottom(controls);

        Scene scene = new Scene(root, 1200, 800);

        themeManager.applyThemeToScene(scene);

        canvas.setOnMousePressed(e -> {
            dragStartX = e.getX();
            dragStartY = e.getY();
            lastMouseX = e.getX();
            lastMouseY = e.getY();
        });

        canvas.setOnMouseDragged(e -> {
            logic.setOffsetX(logic.getOffsetX() + e.getX() - dragStartX);
            logic.setOffsetY(logic.getOffsetY() + e.getY() - dragStartY);
            dragStartX = e.getX();
            dragStartY = e.getY();
            lastMouseX = e.getX();
            lastMouseY = e.getY();
            redraw(gc);
        });

        canvas.setOnScroll(e -> {
            double delta = e.getDeltaY();
            double baseFactor = (delta > 0) ? 1.1 : 0.9;
            lastMouseX = e.getX();
            lastMouseY = e.getY();

            if (logic.getZoomMode().equals("None")) {
                logic.applyScale(baseFactor, e.getX(), e.getY(), logic.isZoomToMouse(), canvas);
                redraw(gc);
            } else {
                logic.setZoomVelocity(logic.getZoomVelocity() + delta * logic.getZoomSensitivity() * logic.getZoomFactor());
                renderer.setMousePosition(e.getX(), e.getY());
            }
        });

        canvas.setOnMouseMoved(e -> {
            renderer.setMousePosition(e.getX(), e.getY());
            lastMouseX = e.getX();
            lastMouseY = e.getY();
            redraw(gc);
        });

        stage.setTitle("Ascendancy graphing calculator");
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> {
            e.consume();
            stage.hide();
        });

        stage.show();

        Platform.runLater(() -> {
            themeManager.installListCellFactory(listView);

            if (zoomFactorSliderRef != null) {
                themeManager.styleSliderThumb(zoomFactorSliderRef);
            }

            if (scene != null) themeManager.applyThemeToScene(scene);
        });

        functions.add(initialFunction);

        functionInput.textProperty().addListener((obs, oldText, newText) -> {
            renderer.setPreviewExpr(newText.trim());
            int sel = listView.getSelectionModel().getSelectedIndex();
            renderer.setPreviewReplaceIndex((sel >= 0) ? sel : -1);
            redraw(gc);
        });

        addBtn.setOnAction(e -> {
            String expr = functionInput.getText().trim();
            if (!expr.isEmpty()) {
                functions.add(expr);
                functionInput.clear();
                renderer.setPreviewExpr("");
                listView.getSelectionModel().clearSelection();
                renderer.setPreviewReplaceIndex(-1);
                redraw(gc);
            }
        });

        updateBtn.setOnAction(e -> {
            int sel = listView.getSelectionModel().getSelectedIndex();
            if (sel >= 0) {
                String expr = functionInput.getText().trim();
                if (!expr.isEmpty()) {
                    functions.set(sel, expr);
                    renderer.setPreviewExpr("");
                    listView.getSelectionModel().clearSelection();
                    renderer.setPreviewReplaceIndex(-1);
                    redraw(gc);
                }
            }
        });

        removeBtn.setOnAction(e -> {
            int sel = listView.getSelectionModel().getSelectedIndex();
            if (sel >= 0) {
                functions.remove(sel);
                functionInput.clear();
                renderer.setPreviewExpr("");
                listView.getSelectionModel().clearSelection();
                renderer.setPreviewReplaceIndex(-1);
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
                renderer.setPreviewReplaceIndex(sel);
                renderer.setPreviewExpr(selectedExpr);
            } else {
                updateBtn.setDisable(true);
                removeBtn.setDisable(true);
                renderer.setPreviewReplaceIndex(-1);
                renderer.setPreviewExpr("");
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
                        switch (themeManager.getCurrentTheme()) {
                            case DARK -> setStyle("-fx-text-fill: #dcdcdc; -fx-background-color: transparent;");
                            case BLACK_BLUE -> setStyle("-fx-text-fill: #9fdcff; -fx-background-color: transparent;");
                            case FOREST -> setStyle("-fx-text-fill: #dfe6dd; -fx-background-color: transparent;");
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
                    listView.getSelectionModel().clearSelection();
                    updateBtn.setDisable(true);
                    removeBtn.setDisable(true);
                    functionInput.clear();
                    renderer.setPreviewExpr("");
                    renderer.setPreviewReplaceIndex(-1);
                    redraw(canvas.getGraphicsContext2D());
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
                    listView.getSelectionModel().select(cell.getIndex());
                }
            });

            return cell;
        });

        redraw(gc);

        new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (!logic.getZoomMode().equals("None") && Math.abs(logic.getZoomVelocity()) > 0.0001) {
                    double factor = Math.exp(logic.getZoomVelocity());
                    factor = logic.applyEasing(factor);

                    double mouseX = (Double.isNaN(lastMouseX) || Double.isNaN(lastMouseY)) ? canvas.getWidth() / 2.0 : lastMouseX;
                    double mouseY = (Double.isNaN(lastMouseX) || Double.isNaN(lastMouseY)) ? canvas.getHeight() / 2.0 : lastMouseY;

                    if (logic.isZoomToMouse()) {
                        logic.applyScale(factor, mouseX, mouseY, true, canvas);
                    } else {
                        logic.setScale(logic.getScale() * factor);
                    }

                    logic.setZoomVelocity(logic.getZoomVelocity() * logic.getZoomFriction());
                    redraw(gc);
                }
            }
        }.start();
    }

    private void redraw(GraphicsContext gc) {
        renderer.redraw(gc, canvas, functions);
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
