package org.example.math;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SystemSolver {

    // Solve 2x2 linear system: a1*x + b1*y = c1, a2*x + b2*y = c2
    public static void solve2x2(String eq1, String eq2) {
        double[] coefficients1 = parseEquation(eq1);
        double[] coefficients2 = parseEquation(eq2);

        double a1 = coefficients1[0], b1 = coefficients1[1], c1 = coefficients1[2];
        double a2 = coefficients2[0], b2 = coefficients2[1], c2 = coefficients2[2];

        double det = a1 * b2 - a2 * b1;

        if (det == 0) {
            System.out.println("The system has no unique solution.");
            return;
        }

        double x = (c1 * b2 - c2 * b1) / det;
        double y = (a1 * c2 - a2 * c1) / det;

        System.out.println("Solution: x = " + x + ", y = " + y);
    }

    // Parse equation like "24x + y = 48" into {a, b, c} -> a*x + b*y = c
    private static double[] parseEquation(String eq) {
        eq = eq.replaceAll("\\s+", ""); // remove spaces
        String[] sides = eq.split("=");

        if (sides.length != 2) throw new RuntimeException("Invalid equation: " + eq);

        double c = Double.parseDouble(sides[1]);

        double a = 0, b = 0;

        Pattern pattern = Pattern.compile("([+-]?\\d*\\.?\\d*)x");
        Matcher matcher = pattern.matcher(sides[0]);
        if (matcher.find()) {
            String match = matcher.group(1);
            a = match.isEmpty() || match.equals("+") ? 1 : match.equals("-") ? -1 : Double.parseDouble(match);
        }

        pattern = Pattern.compile("([+-]?\\d*\\.?\\d*)y");
        matcher = pattern.matcher(sides[0]);
        if (matcher.find()) {
            String match = matcher.group(1);
            b = match.isEmpty() || match.equals("+") ? 1 : match.equals("-") ? -1 : Double.parseDouble(match);
        }

        return new double[]{a, b, c};
    }
}
