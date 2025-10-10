package org.example.math;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

public class Algorthims {

    public static double newtonRaphson(DoubleUnaryOperator f, double x0, double tol, int maxIter) {
        for (int i = 0; i < maxIter; i++) {
            double h = 1e-6;
            double derivative = (f.applyAsDouble(x0 + h) - f.applyAsDouble(x0 - h)) / (2 * h);
            if (derivative == 0) throw new RuntimeException("Zero derivative");
            double x1 = x0 - f.applyAsDouble(x0) / derivative;
            if (Math.abs(x1 - x0) < tol) return x1;
            x0 = x1;
        }
        throw new RuntimeException("error");
    }

    public static double bisection(DoubleUnaryOperator f, double a, double b, double tol, int maxIter) {
        double fa = f.applyAsDouble(a);
        double fb = f.applyAsDouble(b);
        if (fa == 0) return a;
        if (fb == 0) return b;
        if (fa * fb > 0) throw new RuntimeException("Invalid interval");
        for (int i = 0; i < maxIter; i++) {
            double mid = (a + b) / 2;
            double fmid = f.applyAsDouble(mid);
            if (Math.abs(fmid) < tol || (b - a) / 2 < tol) return mid;
            if (fa * fmid < 0) {
                b = mid;
                fb = fmid;
            } else {
                a = mid;
                fa = fmid;
            }
        }
        throw new RuntimeException("error");
    }

    public static List<Double> findAllRoots(DoubleUnaryOperator f, double start, double end, double step, double tol, int maxIter) {
        List<Double> roots = new ArrayList<>();
        double a = start;
        while (a < end) {
            double b = a + step;
            double fa = f.applyAsDouble(a);
            double fb = f.applyAsDouble(b);
            if (fa * fb <= 0) {
                try {
                    double root = bisection(f, a, b, tol, maxIter);
                    boolean exists = roots.stream().anyMatch(r -> Math.abs(r - root) < tol);
                    if (!exists) roots.add(root);
                } catch (Exception ignored) {}
            }
            a = b;
        }
        return roots;
    }

    public static List<Double> findAllRootsNewton(DoubleUnaryOperator f, double start, double end, double step, double tol, int maxIter) {
        List<Double> roots = new ArrayList<>();
        for (double guess = start; guess <= end; guess += step) {
            try {
                double root = newtonRaphson(f, guess, tol, maxIter);
                boolean exists = roots.stream().anyMatch(r -> Math.abs(r - root) < tol);
                if (!exists && !Double.isNaN(root) && !Double.isInfinite(root)) roots.add(root);
            } catch (Exception ignored) {}
        }
        return roots;
    }

    //derivatives
    public static double derivative(DoubleUnaryOperator f, double x) {
        double h = 1e-6;
        return (f.applyAsDouble(x + h) - f.applyAsDouble(x - h)) / (2 * h);
    }

    /** Second derivative using central difference */
    public static double secondDerivative(DoubleUnaryOperator f, double x) {
        double h = 1e-6;
        return (f.applyAsDouble(x + h) - 2 * f.applyAsDouble(x) + f.applyAsDouble(x - h)) / (h * h);
    }

    /** nth derivative (recursive, not very efficient for large n) */
    public static double nthDerivative(DoubleUnaryOperator f, double x, int n) {
        if (n == 1) return derivative(f, x);
        if (n == 2) return secondDerivative(f, x);
        double h = 1e-5;
        return (nthDerivative(f, x + h, n - 1) - nthDerivative(f, x - h, n - 1)) / (2 * h);
    }

    /** Gradient for a list of points (approximate derivative values) */
    public static List<Double> derivativeOverRange(DoubleUnaryOperator f, double start, double end, double step) {
        List<Double> values = new ArrayList<>();
        for (double x = start; x <= end; x += step) {
            values.add(derivative(f, x));
        }
        return values;
    }
}
