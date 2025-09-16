package org.example.math;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SystemSolver {
    private static final double TOL = 1e-6;
    private static final int MAX_ITER = 100;

    public static void solveSystem(String[] equations) {
        int vars = countVars(equations);

        if (equations.length == 1 && vars == 1) {
            solveSingle(equations[0]);
        } else if (equations.length == 2 && vars == 2 && isLinear(equations)) {
            solve2x2(equations[0], equations[1]);
        } else if (equations.length == 3 && vars == 3 && isLinear(equations)) {
            solve3x3(equations);
        } else {
            solveNonlinear(equations, vars);
        }
    }

    private static int countVars(String[] equations) {
        Set<String> vars = new HashSet<>();
        for (String eq : equations) {
            Matcher m = Pattern.compile("[a-zA-Z]").matcher(eq);
            while (m.find()) vars.add(m.group());
        }
        return vars.size();
    }

    private static boolean isLinear(String[] equations) {
        return Arrays.stream(equations).noneMatch(eq -> eq.contains("^"));
    }

    private static void solveSingle(String eq) {
        String expr = normalize(eq);
        Expression f = new ExpressionBuilder(expr).variable("x").build();
        var fun = (java.util.function.DoubleUnaryOperator) x -> f.setVariable("x", x).evaluate();

        System.out.println("Finding roots in [-10, 10]...");
        var roots = Algorthims.findAllRoots(fun, -10, 10, 0.5, TOL, MAX_ITER);
        System.out.println(roots.isEmpty() ? "No roots found." : "Roots: " + roots);
    }

    public static void solve2x2(String eq1, String eq2) {
        double[] c1 = parse2D(eq1), c2 = parse2D(eq2);
        double det = c1[0] * c2[1] - c2[0] * c1[1];

        if (Math.abs(det) < TOL) {
            System.out.println("No unique solution.");
            return;
        }

        double x = (c1[2] * c2[1] - c2[2] * c1[1]) / det;
        double y = (c1[0] * c2[2] - c2[0] * c1[2]) / det;
        System.out.println("Solution: x = " + x + ", y = " + y);
    }

    private static void solve3x3(String[] eqs) {
        double[][] A = new double[3][3];
        double[] B = new double[3];

        for (int i = 0; i < 3; i++) {
            double[] coeffs = parse3D(eqs[i]);
            A[i] = Arrays.copyOf(coeffs, 3);
            B[i] = coeffs[3];
        }

        double detA = det3x3(A);
        if (Math.abs(detA) < TOL) {
            System.out.println("No unique solution.");
            return;
        }

        double x = det3x3(replaceCol(A, B, 0)) / detA;
        double y = det3x3(replaceCol(A, B, 1)) / detA;
        double z = det3x3(replaceCol(A, B, 2)) / detA;
        System.out.println("Solution: x = " + x + ", y = " + y + ", z = " + z);
    }

    private static void solveNonlinear(String[] equations, int vars) {
        List<String> varNames = getVarNames(equations);
        if (varNames.size() != vars) return;

        String[] normalized = Arrays.stream(equations).map(SystemSolver::normalize).toArray(String[]::new);
        List<Map<String, Double>> solutions = new ArrayList<>();
        Random rand = new Random();

        for (int range = 5; range <= 50 && solutions.isEmpty(); range *= 2) {
            for (int i = 0; i < 30; i++) {
                double[] guess = rand.doubles(vars, -range, range).toArray();
                try {
                    double[] sol = newton(normalized, varNames, guess);
                    if (isValid(sol, normalized, varNames) && !isDuplicate(solutions, sol, varNames)) {
                        Map<String, Double> solMap = new HashMap<>();
                        for (int j = 0; j < vars; j++) solMap.put(varNames.get(j), sol[j]);
                        solutions.add(solMap);
                    }
                } catch (Exception ignored) {}
            }
        }

        if (solutions.isEmpty()) {
            System.out.println("No solution found.");
        } else {
            for (int i = 0; i < solutions.size(); i++) {
                System.out.print("Solution " + (i + 1) + ": ");
                solutions.get(i).forEach((v, val) -> System.out.print(v + " = " + val + ", "));
                System.out.println();
            }
        }
    }

    private static double[] newton(String[] eqs, List<String> vars, double[] x) {
        for (int iter = 0; iter < MAX_ITER; iter++) {
            double[] f = new double[vars.size()];
            double[][] J = new double[vars.size()][vars.size()];

            for (int i = 0; i < vars.size(); i++) {
                Expression expr = new ExpressionBuilder(eqs[i]).variables(vars.toArray(new String[0])).build();
                for (int j = 0; j < vars.size(); j++) expr.setVariable(vars.get(j), x[j]);
                f[i] = expr.evaluate();

                for (int j = 0; j < vars.size(); j++) {
                    double old = x[j];
                    x[j] += 1e-6;
                    Expression e2 = new ExpressionBuilder(eqs[i]).variables(vars.toArray(new String[0])).build();
                    for (int k = 0; k < vars.size(); k++) e2.setVariable(vars.get(k), x[k]);
                    J[i][j] = (e2.evaluate() - f[i]) / 1e-6;
                    x[j] = old;
                }
            }

            double[] dx = gauss(J, negate(f));
            for (int j = 0; j < vars.size(); j++) x[j] += dx[j];
            if (norm(dx) < TOL) break;
        }
        return x;
    }

    private static double[] gauss(double[][] A, double[] b) {
        int n = b.length;
        for (int i = 0; i < n; i++) {
            int max = i;
            for (int j = i + 1; j < n; j++) if (Math.abs(A[j][i]) > Math.abs(A[max][i])) max = j;
            swap(A, b, i, max);
            for (int j = i + 1; j < n; j++) {
                double factor = A[j][i] / A[i][i];
                b[j] -= factor * b[i];
                for (int k = i; k < n; k++) A[j][k] -= factor * A[i][k];
            }
        }
        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            double sum = 0;
            for (int j = i + 1; j < n; j++) sum += A[i][j] * x[j];
            x[i] = (b[i] - sum) / A[i][i];
        }
        return x;
    }

    // Utility methods
    private static String normalize(String eq) {
        String[] parts = eq.split("=");
        return parts.length == 1 ? parts[0] : "(" + parts[0] + ")-(" + parts[1] + ")";
    }

    private static List<String> getVarNames(String[] equations) {
        Set<String> vars = new TreeSet<>();
        for (String eq : equations) {
            Matcher m = Pattern.compile("[a-zA-Z]").matcher(eq);
            while (m.find()) vars.add(m.group());
        }
        return new ArrayList<>(vars);
    }

    private static double[] parse2D(String eq) {
        eq = eq.replaceAll("\\s+", "");
        String[] sides = eq.split("=");
        double c = Double.parseDouble(sides[1]);
        double a = getCoeff(sides[0], "x");
        double b = getCoeff(sides[0], "y");
        return new double[]{a, b, c};
    }

    private static double[] parse3D(String eq) {
        eq = eq.replaceAll("\\s+", "");
        String[] sides = eq.split("=");
        double d = Double.parseDouble(sides[1]);
        return new double[]{getCoeff(sides[0], "x"), getCoeff(sides[0], "y"), getCoeff(sides[0], "z"), d};
    }

    private static double getCoeff(String expr, String var) {
        Matcher m = Pattern.compile("([+-]?\\d*\\.?\\d*)" + var).matcher(expr);
        if (!m.find()) return 0;
        String s = m.group(1);
        return s.isEmpty() || s.equals("+") ? 1 : s.equals("-") ? -1 : Double.parseDouble(s);
    }

    private static double det3x3(double[][] m) {
        return m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1])
                - m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0])
                + m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0]);
    }

    private static double[][] replaceCol(double[][] A, double[] B, int col) {
        double[][] res = new double[3][3];
        for (int i = 0; i < 3; i++) {
            res[i] = Arrays.copyOf(A[i], 3);
            res[i][col] = B[i];
        }
        return res;
    }

    private static boolean isValid(double[] sol, String[] eqs, List<String> vars) {
        for (String eq : eqs) {
            Expression expr = new ExpressionBuilder(eq).variables(vars.toArray(new String[0])).build();
            for (int j = 0; j < vars.size(); j++) expr.setVariable(vars.get(j), sol[j]);
            if (Math.abs(expr.evaluate()) > TOL) return false;
        }
        return true;
    }

    private static boolean isDuplicate(List<Map<String, Double>> sols, double[] sol, List<String> vars) {
        return sols.stream().anyMatch(s -> {
            for (int i = 0; i < vars.size(); i++) {
                if (Math.abs(s.get(vars.get(i)) - sol[i]) > TOL) return false;
            }
            return true;
        });
    }

    private static void swap(double[][] A, double[] b, int i, int j) {
        double[] temp = A[i]; A[i] = A[j]; A[j] = temp;
        double t = b[i]; b[i] = b[j]; b[j] = t;
    }

    private static double[] negate(double[] f) {
        return Arrays.stream(f).map(x -> -x).toArray();
    }

    private static double norm(double[] v) {
        return Math.sqrt(Arrays.stream(v).map(x -> x * x).sum());
    }
}