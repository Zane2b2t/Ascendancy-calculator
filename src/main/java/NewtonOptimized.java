
import java.util.*;
import java.util.function.DoubleUnaryOperator;

public class NewtonOptimized {
    public static double newtonRaphsonFast(DoubleUnaryOperator f, DoubleUnaryOperator df, double x0, double tol, int maxIter) {
        for (int i = 0; i < maxIter; i++) {
            double fx = f.applyAsDouble(x0);
            double dfx = df.applyAsDouble(x0);
            if (dfx == 0) throw new RuntimeException("Zero derivative");
            double x1 = x0 - fx / dfx;
            if (Math.abs(x1 - x0) < tol) return x1;
            x0 = x1;
        }
        throw new RuntimeException("No convergence");
    }

    public static List<Double> findAllRootsNewtonFast(DoubleUnaryOperator f, DoubleUnaryOperator df, double start, double end, double step, double tol, int maxIter) {
        List<Double> roots = new ArrayList<>();
        for (double guess = start; guess <= end; guess += step) {
            try {
                double root = newtonRaphsonFast(f, df, guess, tol, maxIter);
                boolean exists = false;
                for (double r : roots) {
                    if (Math.abs(r - root) < tol) {
                        exists = true;
                        break;
                    }
                }
                if (!exists && !Double.isNaN(root) && !Double.isInfinite(root)) roots.add(root);
            } catch (Exception ignored) {}
        }
        return roots;
    }
}