package org.example;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;

public class GraphThemeManager {

    public enum Theme {LIGHT, DARK, BLACK_BLUE, FOREST}
    private Theme currentTheme = Theme.FOREST;

    public Theme getCurrentTheme() {
        return currentTheme;
    }

    public void setCurrentTheme(Theme theme) {
        this.currentTheme = theme;
    }

    public void applyThemeToScene(Scene scene) {
        if (scene == null) return;
        Parent root = scene.getRoot();

        String accent = "#7fd3ff";
        String focused = "transparent";

        switch (currentTheme) {
            case DARK -> accent = "#e6e6e6";
            case BLACK_BLUE -> accent = "#9fdcff";
            case FOREST -> accent = "#57F287";
            default -> accent = "#007acc";
        }

        root.setStyle(String.join(";",
                "-fx-accent: " + accent,
                "-fx-focus-color: " + focused,
                "-fx-faint-focus-color: " + focused
        ));

        applyThemeToControls(root);
    }

    public void applyThemeToControls(Node rootNode) {
        if (rootNode == null) return;

        if (rootNode instanceof Button b) {
            switch (currentTheme) {
                case DARK -> b.setStyle("-fx-background-color: linear-gradient(#2b2b2b, #1f1f1f); -fx-text-fill: #eaeaea; -fx-background-radius:6; -fx-border-color: #3a3a3a;");
                case BLACK_BLUE -> b.setStyle("-fx-background-color: linear-gradient(#002233, #001522); -fx-text-fill: #9fdcff; -fx-background-radius:8; -fx-border-color: rgba(127,211,255,0.15);");
                case FOREST -> b.setStyle("-fx-background-color: linear-gradient(#2b2d31, #1e1f22); -fx-text-fill: #dfe6dd; -fx-background-radius:8; -fx-border-color: rgba(87,242,135,0.25);");
                default -> b.setStyle("");
            }
        } else if (rootNode instanceof TextField tf) {
            switch (currentTheme) {
                case DARK -> tf.setStyle("-fx-control-inner-background: #141414; -fx-text-fill: #eaeaea; -fx-background-radius:6; -fx-border-color: #333;");
                case BLACK_BLUE -> tf.setStyle("-fx-control-inner-background: #001219; -fx-text-fill: #9fdcff; -fx-background-radius:6; -fx-border-color: rgba(127,211,255,0.12);");
                case FOREST -> tf.setStyle("-fx-control-inner-background: #1f2125; -fx-text-fill: #dfe6dd; -fx-background-radius:6; -fx-border-color: rgba(87,242,135,0.2);");
                default -> tf.setStyle("");
            }
        } else if (rootNode instanceof ComboBox<?> cb) {
            switch (currentTheme) {
                case DARK -> cb.setStyle("-fx-background-color: #1b1b1b; -fx-text-fill: #eaeaea; -fx-border-color: #333;");
                case BLACK_BLUE -> cb.setStyle("-fx-background-color: #001528; -fx-text-fill: #9fdcff; -fx-border-color: rgba(127,211,255,0.12);");
                case FOREST -> cb.setStyle("-fx-background-color: #2b2d31; -fx-text-fill: #dfe6dd; -fx-border-color: rgba(87,242,135,0.2);");
                default -> cb.setStyle("");
            }
        } else if (rootNode instanceof Slider s) {
            switch (currentTheme) {
                case DARK -> s.setStyle("-fx-control-inner-background: #1a1a1a; -fx-background-color: linear-gradient(#2b2b2b, #1f1f1f);");
                case BLACK_BLUE -> s.setStyle("-fx-control-inner-background: transparent; -fx-background-color: transparent;");
                case FOREST -> s.setStyle("-fx-control-inner-background: transparent; -fx-background-color: transparent;");
                default -> s.setStyle("");
            }
        } else if (rootNode instanceof Label l) {
            switch (currentTheme) {
                case DARK -> l.setStyle("-fx-text-fill: #dcdcdc;");
                case BLACK_BLUE -> l.setStyle("-fx-text-fill: #9fdcff;");
                case FOREST -> l.setStyle("-fx-text-fill: #dfe6dd;");
                default -> l.setStyle("");
            }
        } else if (rootNode instanceof CheckBox cbx) {
            switch (currentTheme) {
                case DARK -> cbx.setStyle("-fx-text-fill: #e6e6e6;");
                case BLACK_BLUE -> cbx.setStyle("-fx-text-fill: #9fdcff;");
                case FOREST -> cbx.setStyle("-fx-text-fill: #dfe6dd;");
                default -> cbx.setStyle("");
            }
        } else if (rootNode instanceof ListView<?> lv) {
            switch (currentTheme) {
                case DARK -> lv.setStyle("-fx-control-inner-background: #101010; -fx-background-color: #0d0d0d; -fx-text-fill: #e6e6e6;");
                case BLACK_BLUE -> lv.setStyle("-fx-control-inner-background: transparent; -fx-background-color: transparent; -fx-text-fill: #9fdcff;");
                case FOREST -> lv.setStyle("-fx-control-inner-background: #1e1f22; -fx-background-color: #1e1f22; -fx-text-fill: #dfe6dd;");
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
                case FOREST -> {
                    // Discord-like: greenish tint from top-left to warm orange at bottom-right
                    Stop[] stops = new Stop[]{
                            new Stop(0.0, Color.web("#203226")),   // green-tinted corner (top-left)
                            new Stop(0.25, Color.web("#1a2320")),
                            new Stop(0.50, Color.web("#141a18")),  // neutral dark mid
                            new Stop(0.75, Color.web("#1e1b15")),
                            new Stop(1.0, Color.web("#2e2a1f"))    // warm bottom-right
                    };
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

    public void installListCellFactory(ListView<String> listView) {
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
                        case FOREST -> setStyle("-fx-text-fill: #dfe6dd; -fx-background-color: transparent;");
                        default -> setStyle("");
                    }
                }
            }
        });
    }

    public void styleSliderThumb(Slider slider) {
        Node thumb = slider.lookup(".thumb");
        if (thumb != null) {
            if (currentTheme == Theme.BLACK_BLUE || currentTheme == Theme.DARK || currentTheme == Theme.FOREST) {
                thumb.setStyle("-fx-background-color: white; -fx-background-radius: 8;");
            } else {
                thumb.setStyle("");
            }
        }
    }

    public Paint getBackgroundPaint() {
        return switch (currentTheme) {
            case DARK -> Color.web("#0b0b0b");
            case BLACK_BLUE -> {
                Stop[] stops = new Stop[]{new Stop(0, Color.web("#000010")), new Stop(1, Color.web("#00162b"))};
                yield new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE, stops);
            }
            case FOREST -> {
                Stop[] stops = new Stop[]{
                        new Stop(0.0, Color.web("#203226")),
                        new Stop(0.25, Color.web("#1a2320")),
                        new Stop(0.50, Color.web("#141a18")),
                        new Stop(0.75, Color.web("#1e1b15")),
                        new Stop(1.0, Color.web("#2e2a1f"))
                };
                yield new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE, stops);
            }
            default -> Color.WHITE;
        };
    }

    public Color getGridColor() {
        return switch (currentTheme) {
            case DARK -> Color.web("#2a2a2a");
            case BLACK_BLUE -> Color.web("#06253a");
            case FOREST -> Color.web("#243129", 0.7);
            default -> Color.LIGHTGRAY;
        };
    }

    public Color getAxisColor() {
        return switch (currentTheme) {
            case DARK -> Color.web("#e6e6e6");
            case BLACK_BLUE -> Color.web("#7fd3ff");
            // Subtle light axis akin to Discord text, use accent only for interactive elements
            case FOREST -> Color.web("#b8c8bf");
            default -> Color.BLACK;
        };
    }

    public Color getTextColor() {
        return switch (currentTheme) {
            case DARK -> Color.web("#cfcfcf");
            case BLACK_BLUE -> Color.web("#9fdcff");
            case FOREST -> Color.web("#dbe5de");
            default -> Color.GRAY;
        };
    }
}

