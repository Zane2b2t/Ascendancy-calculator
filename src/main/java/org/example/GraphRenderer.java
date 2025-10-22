package org.example;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Text;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.example.math.Functions;

import java.util.ArrayList;
import java.util.Arrays;
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
        double overscan = 2; // extra pixels sampled around the edges

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

        // vertical grid lines
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

        // horizontal grid lines
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

        // axes
        gc.setStroke(axisColor);
        gc.setLineWidth(2);
        gc.strokeLine(0, h / 2 + logic.getOffsetY(), w, h / 2 + logic.getOffsetY());
        gc.strokeLine(w / 2 + logic.getOffsetX(), 0, w / 2 + logic.getOffsetX(), h);

        List<double[]> intersections = new ArrayList<>();
        List<List<double[]>> functionPoints = new ArrayList<>();
        List<Double> verticalLines = new ArrayList<>();
        double threshold = (h / logic.getScale()) * DISCONTINUITY_THRESHOLD;

        // evaluate each function string
        for (int fi = 0; fi < functions.size(); fi++) {
            String expr = (previewReplaceIndex == fi && !previewExpr.isEmpty()) ? previewExpr : functions.get(fi);
            processExpression(expr, functionPoints, verticalLines, w, h, overscan, pxStep, threshold);
        }

        // standalone preview (not replacing any existing function)
        boolean drewStandalonePreview = false;
        if (!previewExpr.isEmpty() && previewReplaceIndex < 0) {
            int sizeBefore = functionPoints.size();
            processExpression(previewExpr, functionPoints, verticalLines, w, h, overscan, pxStep, threshold);
            drewStandalonePreview = functionPoints.size() > sizeBefore;
        }

        // find intersections between plotted functions
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

        // axis intersections & extrema
        for (List<double[]> pts : functionPoints) {
            for (int k = 1; k < pts.size(); k++) {
                double[] p1 = pts.get(k - 1);
                double[] p2 = pts.get(k);

                // y = 0 crossing
                if (p1[3] * p2[3] < 0) {
                    double t = p1[3] / (p1[3] - p2[3]);
                    double x = p1[2] + t * (p2[2] - p1[2]);
                    double screenX = w / 2 + x * logic.getScale() + logic.getOffsetX();
                    double screenY = h / 2 + logic.getOffsetY();
                    intersections.add(new double[]{screenX, screenY, x, 0});
                }

                // x = 0 crossing
                if (p1[2] * p2[2] < 0) {
                    double t = p1[2] / (p1[2] - p2[2]);
                    double y = p1[3] + t * (p2[3] - p1[3]);
                    double screenX = w / 2 + logic.getOffsetX();
                    double screenY = h / 2 - y * logic.getScale() + logic.getOffsetY();
                    intersections.add(new double[]{screenX, screenY, 0, y});
                }

                // extrema (slope sign change)
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

        // origin
        double originX = w / 2 + logic.getOffsetX();
        double originY = h / 2 + logic.getOffsetY();
        intersections.add(new double[]{originX, originY, 0, 0});

        // draw functions
        for (int i = 0; i < functionPoints.size(); i++) {
            boolean isPreview = drewStandalonePreview && i == functionPoints.size() - 1;
            setupGraphicsContext(gc, isPreview, i);
            drawSegments(gc, functionPoints.get(i), threshold);
            gc.setLineDashes(null);
            gc.setGlobalAlpha(1.0);
        }

        // vertical line intersections (from equations like x = a)
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

        // hover detection
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
        // treat strings with a 'where' clause as functions with domain restrictions
        String[] whereSplitForEq = expr.split("(?i)\\bwhere\\b", 2);
        boolean isEquation = whereSplitForEq[0].contains("=");
        if (isEquation) {
            int before = verticalLines.size();
            handleEquation(whereSplitForEq[0], verticalLines, w, h);
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
                String[] parts = expr.split("(?i)\\bwhere\\b", 2);
                String baseExpr = parts[0].trim();
                baseExpr = Functions.fixImplicitMultiplication(baseExpr);

                Condition rootCondition = null;
                if (parts.length == 2) {
                    String condStr = parts[1].trim();
                    rootCondition = parseConditionExpression(condStr);
                }

                Expression expression = new ExpressionBuilder(baseExpr)
                        .variables("x", "pi", "e")
                        .build();
                expression.setVariable("pi", Math.PI).setVariable("e", Math.E);
                List<double[]> pts = new ArrayList<>();
                for (double px = -w / 2 - overscan; px < w / 2 + overscan; px += pxStep) {
                    double x = (px - logic.getOffsetX()) / logic.getScale();

                    if (rootCondition != null && !rootCondition.test(x, 2.0 / Math.max(1.0, logic.getScale()))) {
                        continue; // skip points outside domain
                    }

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

    // Clarification: the condition parser below evaluates domain strings such as
    // "x in [1,5)", "0 < x < pi", "R - {5,10}", and combinations using and/or/not.
    // Comments nearby explain the tricky parts (bracket depth tracking, chained inequalities, intervals, set exclusion).
    private interface Condition {
        boolean test(double x, double tol);
    }

    private static class ComparisonCondition implements Condition {
        final String op;
        final Expression left, right;
        ComparisonCondition(String op, Expression l, Expression r) { this.op = op; this.left = l; this.right = r; }
        public boolean test(double x, double tol) {
            try {
                double l = left.setVariable("x", x).setVariable("pi", Math.PI).setVariable("e", Math.E).evaluate();
                double r = right.setVariable("x", x).setVariable("pi", Math.PI).setVariable("e", Math.E).evaluate();
                return switch (op) {
                    case "<" -> l < r;
                    case "<=" -> l <= r;
                    case ">" -> l > r;
                    case ">=" -> l >= r;
                    case "!=" -> Math.abs(l - r) > tol;
                    case "==", "=" -> Math.abs(l - r) <= tol;
                    default -> true;
                };
            } catch (Exception e) { return false; }
        }
    }

    private static class IntervalCondition implements Condition {
        final double lo, hi; final boolean loInc, hiInc;
        IntervalCondition(double lo, boolean loInc, double hi, boolean hiInc) { this.lo = lo; this.loInc = loInc; this.hi = hi; this.hiInc = hiInc; }
        public boolean test(double x, double tol) {
            boolean lower = lo == Double.NEGATIVE_INFINITY ? true : (loInc ? x + tol >= lo : x > lo + tol);
            boolean upper = hi == Double.POSITIVE_INFINITY ? true : (hiInc ? x - tol <= hi : x < hi - tol);
            return lower && upper;
        }
    }

    private static class SetExclusionCondition implements Condition {
        final List<Expression> excludes;
        SetExclusionCondition(List<Expression> excludes) { this.excludes = excludes; }
        public boolean test(double x, double tol) {
            for (Expression e : excludes) {
                try { double v = e.setVariable("x", x).setVariable("pi", Math.PI).setVariable("e", Math.E).evaluate();
                    if (Math.abs(v - x) <= tol) return false; // excluded
                } catch (Exception ignored) {}
            }
            return true;
        }
    }

    private static class CompositeCondition implements Condition {
        final boolean isAnd; final List<Condition> children;
        CompositeCondition(boolean isAnd, List<Condition> children) { this.isAnd = isAnd; this.children = children; }
        public boolean test(double x, double tol) {
            if (isAnd) {
                for (Condition c : children) if (!c.test(x, tol)) return false;
                return true;
            } else {
                for (Condition c : children) if (c.test(x, tol)) return true;
                return false;
            }
        }
    }

    private Condition parseConditionExpression(String s) {
        if (s == null) return null;
        String normalized = s.replace('\u2264', '<')
                .replace('\u2265', '>')
                .replaceAll("≤", "<=")
                .replaceAll("≥", ">=")
                .replaceAll("\\s+", " ")
                .trim();

        // split top-level OR (ignores tokens inside parentheses/brackets)
        List<String> orParts = splitTopLevel(normalized, "(?i)\\bor\\b");
        if (orParts.size() > 1) {
            List<Condition> children = new ArrayList<>();
            for (String p : orParts) children.add(parseConditionExpression(p));
            return new CompositeCondition(false, children);
        }

        // split top-level AND
        List<String> andParts = splitTopLevel(normalized, "(?i)\\band\\b");
        if (andParts.size() > 1) {
            List<Condition> children = new ArrayList<>();
            for (String p : andParts) children.add(parseConditionExpression(p));
            return new CompositeCondition(true, children);
        }

        String t = normalized.trim();
        if (t.startsWith("not ") || t.startsWith("!")) {
            String sub = t.replaceFirst("(?i)not\\s+|!", "").trim();
            Condition c = parseConditionExpression(sub);
            // simple negation wrapper
            return new Condition() { public boolean test(double x, double tol) { return !c.test(x, tol); } };
        }

        // strip outer parentheses immediately
        if (t.startsWith("(") && t.endsWith(")")) {
            return parseConditionExpression(t.substring(1, t.length() - 1));
        }

        // quick check for interval-like syntax; we parse it manually below
        String intervalRegex = "^([\\[\\(])\\s*([^,]+)\\s*,\\s*([^\\]\\)\\[]+)\\s*([\\]\\)\\[])$";
        if (t.matches(intervalRegex) || t.matches("^([\\[\\(])\\s*([^,]+)\\s*,\\s*([^\\]\\)]+)\\s*([\\]\\)\\[])") ) {
            // fall through to manual parsing
        }

        // manual interval parse: [a,b], (a,b], etc.
        if (t.length() >= 2 && (t.charAt(0) == '[' || t.charAt(0) == '(') && (t.contains(","))) {
            char left = t.charAt(0);
            char right = t.charAt(t.length() - 1);
            int comma = t.indexOf(',');
            if (comma > 0) {
                String leftVal = t.substring(1, comma).trim();
                String rightVal = t.substring(comma + 1, t.length() - 1).trim();
                double lo = parseBoundValue(leftVal, true);
                double hi = parseBoundValue(rightVal, false);
                boolean loInc = left == '[';
                boolean hiInc = (right == ']'); // ']' inclusive; anything else -> exclusive

                // auto-swap reversed bounds so [50,10) -> [10,50)
                if (!Double.isInfinite(lo) && !Double.isInfinite(hi) && lo > hi) {
                    double tmp = lo; lo = hi; hi = tmp;
                    boolean tmpInc = loInc; loInc = hiInc; hiInc = tmpInc;
                }

                return new IntervalCondition(lo, loInc, hi, hiInc);
            }
        }

        // x in [a,b) or x belong_to (...) style: forward to interval parser
        String inRegex = "(?i)^(x)\\s*(?:in|∈|belong_to|belongs_to|belongs to)\\s*(.+)$";
        if (t.matches(inRegex)) {
            String rhs = t.replaceFirst("(?i)^(x)\\s*(?:in|∈|belong_to|belongs_to|belongs to)\\s*", "");
            rhs = rhs.trim();
            return parseConditionExpression(rhs);
        }

        // R - {a,b} style: build an exclusion list
        if (t.matches("(?i)^R\\s*-\\s*\\{.*\\}$")) {
            int l = t.indexOf('{'), r = t.lastIndexOf('}');
            if (l >= 0 && r > l) {
                String inside = t.substring(l + 1, r).trim();
                List<Expression> exps = new ArrayList<>();
                for (String part : inside.split("\\s*,\\s*")) {
                    try {
                        String fixed = Functions.fixImplicitMultiplication(part);
                        Expression e = new ExpressionBuilder(fixed).variables("x","pi","e").build();
                        e.setVariable("pi", Math.PI).setVariable("e", Math.E);
                        exps.add(e);
                    } catch (Exception ignored) {}
                }
                return new Condition() {
                    public boolean test(double x, double tol) {
                        for (Expression e : exps) {
                            try {
                                double v = e.setVariable("x", x).setVariable("pi", Math.PI).setVariable("e", Math.E).evaluate();
                                if (Math.abs(v - x) <= tol) return false; // excluded
                            } catch (Exception ignored) {}
                        }
                        return true;
                    }
                };
            }
        }

        // x in {1,2,3} membership
        if (t.matches("(?i)^x\\s*(?:in|∈)\\s*\\{.*\\}$")) {
            int l = t.indexOf('{'), r = t.lastIndexOf('}');
            if (l >= 0 && r > l) {
                String inside = t.substring(l + 1, r).trim();
                List<Expression> members = new ArrayList<>();
                for (String part : inside.split("\\s*,\\s*")) {
                    try {
                        String fixed = Functions.fixImplicitMultiplication(part);
                        Expression e = new ExpressionBuilder(fixed).variables("x","pi","e").build();
                        e.setVariable("pi", Math.PI).setVariable("e", Math.E);
                        members.add(e);
                    } catch (Exception ignored) {}
                }
                return new Condition() {
                    public boolean test(double x, double tol) {
                        for (Expression m : members) {
                            try {
                                double v = m.setVariable("x", x).setVariable("pi", Math.PI).setVariable("e", Math.E).evaluate();
                                if (Math.abs(v - x) <= tol) return true;
                            } catch (Exception ignored) {}
                        }
                        return false;
                    }
                };
            }
        }

        // chained inequalities: 0 < x < 5  -> parsed as 0 < x AND x < 5
        if (t.matches(".*[<>]=?.*\\bx\\b.*[<>]=?.*")) {
            String normalizedChained = t.replaceAll("([<>]=?)", " $1 ").replaceAll("\\s+", " ").trim();
            String[] parts = normalizedChained.split(" ");
            int xIndex = -1;
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].equalsIgnoreCase("x")) { xIndex = i; break; }
            }
            if (xIndex > 0) {
                List<Condition> children = new ArrayList<>();
                try {
                    if (xIndex >= 2) {
                        String leftVal = parts[xIndex - 2];
                        String leftOp  = parts[xIndex - 1];
                        Expression lExpr = new ExpressionBuilder(Functions.fixImplicitMultiplication(leftVal))
                                .variables("x","pi","e").build();
                        Expression rExpr = new ExpressionBuilder("x").variable("x").build();
                        lExpr.setVariable("pi", Math.PI).setVariable("e", Math.E);
                        children.add(new ComparisonCondition(leftOp, lExpr, rExpr));
                    }
                    if (xIndex + 2 < parts.length) {
                        String rightOp  = parts[xIndex + 1];
                        String rightVal = parts[xIndex + 2];
                        Expression lExpr = new ExpressionBuilder("x").variable("x").build();
                        Expression rExpr = new ExpressionBuilder(Functions.fixImplicitMultiplication(rightVal))
                                .variables("x","pi","e").build();
                        rExpr.setVariable("pi", Math.PI).setVariable("e", Math.E);
                        children.add(new ComparisonCondition(rightOp, lExpr, rExpr));
                    }
                    if (!children.isEmpty()) return new CompositeCondition(true, children);
                } catch (Exception ignored) { /* fall through */ }
            }
        }

        // simple comparisons (outside parentheses)
        String[] ops = {"<=","=","==","!=",">=","<",">"};
        for (String op : ops) {
            int pos = indexOfOp(t, op);
            if (pos >= 0) {
                String left = t.substring(0, pos).trim();
                String right = t.substring(pos + op.length()).trim();
                if (left.isEmpty()) left = "x"; if (right.isEmpty()) right = "x";
                try {
                    Expression lExpr = new ExpressionBuilder(Functions.fixImplicitMultiplication(left)).variables("x","pi","e").build();
                    Expression rExpr = new ExpressionBuilder(Functions.fixImplicitMultiplication(right)).variables("x","pi","e").build();
                    lExpr.setVariable("pi", Math.PI).setVariable("e", Math.E);
                    rExpr.setVariable("pi", Math.PI).setVariable("e", Math.E);
                    return new ComparisonCondition(op, lExpr, rExpr);
                } catch (Exception ignored) {}
            }
        }

        // number literal -> treat as x == literal
        try {
            double v = Double.parseDouble(t);
            Expression e = new ExpressionBuilder("" + v).build();
            return new ComparisonCondition("==", new ExpressionBuilder("x").variable("x").build(), e);
        } catch (Exception ignored) {}

        // unknown input: accept all (falls back to no restriction)
        return (x, tol) -> true;
    }

    // splitTopLevel: split by "and"/"or" but ignore these words if they are inside parentheses/brackets.
    // Example tricky input: "[]( ... ) [23]1[p23][]" — function tracks depth of (), {}, [] and only splits when depth==0.
    private List<String> splitTopLevel(String s, String operatorRegex) {
        List<String> out = new ArrayList<>();
        int depth = 0; int last = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(' || c == '{' || c == '[') depth++;
            else if (c == ')' || c == '}' || c == ']') depth = Math.max(0, depth - 1);
            if (depth == 0) {
                // look for the operator as a simple space-delimited token
                if (s.regionMatches(true, i, " or ", 0, 4) && operatorRegex.equals("(?i)\\bor\\b")) {
                    out.add(s.substring(last, i).trim()); last = i + 4; i += 3; continue;
                }
                if (s.regionMatches(true, i, " and ", 0, 5) && operatorRegex.equals("(?i)\\band\\b")) {
                    out.add(s.substring(last, i).trim()); last = i + 5; i += 4; continue;
                }
            }
        }
        if (last == 0) { out.add(s.trim()); return out; }
        out.add(s.substring(last).trim()); return out;
    }

    // parseBoundValue: accepts numeric literals or expressions (pi, 2*pi, sqrt(2), infinity)
    private double parseBoundValue(String s, boolean allowNegInf) {
        s = s.trim();
        if (s.equalsIgnoreCase("infinity") || s.equalsIgnoreCase("inf") || s.equals("∞")) return Double.POSITIVE_INFINITY;
        if (s.equalsIgnoreCase("-infinity") || s.equalsIgnoreCase("-inf")) return Double.NEGATIVE_INFINITY;
        try { return Double.parseDouble(s); } catch (Exception e) {
            try { Expression ex = new ExpressionBuilder(Functions.fixImplicitMultiplication(s)).variables("x","pi","e").build(); ex.setVariable("pi", Math.PI).setVariable("e", Math.E); return ex.evaluate(); } catch (Exception ex2) { return allowNegInf ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY; }
        }
    }

    // find operator outside parentheses
    private int indexOfOp(String s, String op) {
        int depth = 0;
        for (int i = 0; i <= s.length() - op.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '(') depth++;
            else if (ch == ')') depth = Math.max(0, depth - 1);
            if (depth == 0 && s.startsWith(op, i)) return i;
        }
        return -1;
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
