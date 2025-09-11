package org.example;

import java.util.*;
import java.util.Scanner;
import java.util.function.DoubleUnaryOperator;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in); // allows users to input values in the terminal

        while (true) { //this is an infinite loop (its condition is always met)
            System.out.println("\nEnter your function f(x) = ");
            String input = scanner.nextLine().trim();

            // exit program if input is exit
            if (input.equalsIgnoreCase("exit")) break;

            // if the user already added f(x) = in the input, remove it
            if (input.toLowerCase().startsWith("f(x) = ")) {
                input = input.substring(7);
            }

            // replace things like xcos(x) or 5x from the user input to be x*cos(x) or 5*x for the machine, this also makes it read ^ which java normally doesn't
            input = input.replaceAll("(\\d)x", "$1*x");

            try {
                String finalInput = input;
                DoubleUnaryOperator function = x -> {
                    Expression expression = new ExpressionBuilder(finalInput)
                            .variable("x")
                            .build()
                            .setVariable("x", x);
                    return expression.evaluate();
                };

                System.out.println("Choose an option:" +
                        " 1: Check Odd/Even, 2: Solve Equation, 3: Draw Graph");
                String choice = scanner.nextLine().trim();

                if (choice.equals("1")) {
                    System.out.print("Enter a value for x to test the function: ");
                    double x = Double.parseDouble(scanner.nextLine());
                    testFunction(function, input, x);
                } else if (choice.equals("2")) {
                    System.out.println("Choose root-finding method: 1) Newton-Raphson 2) Bisection");
                    String method = scanner.nextLine().trim();

                    long startTime = System.nanoTime();

                    if (method.equals("1")) {
                        List<Double> roots = findAllRootsNewton(function, -470, 470, 1.0, 1e-7, 100);
                        long endTime = System.nanoTime();
                        System.out.println("Roots ≈ " + roots);
                        System.out.println("Calculation time: " + (endTime - startTime) / 1_000_000.0 + " ms");
                    } else if (method.equals("2")) {
                        List<Double> roots = findAllRoots(function, -470, 470, 0.5, 1e-7, 100);
                        long endTime = System.nanoTime();
                        System.out.println("Roots ≈ " + roots);
                        System.out.println("Calculation time: " + (endTime - startTime) / 1_000_000.0 + " ms");
                    }
                } else if (choice.equals("3")) {
                    drawGraph(function, -20, 20, -10, 10); // default range
                }

            } catch (Exception e) { //handles unexpected exceptions caused by user skill issue
                System.out.println("Error: Invalid function! Please check your syntax.");
                System.out.println("You can use:");
                System.out.println("- Basic operations: +, -, *, /, ^");
                System.out.println("- Functions: sin(x), cos(x), tan(x), log(x), sqrt(x)");
                System.out.println("- Example: xcos(x) or 2x^3+3x^2-12x");
            }
        }

        scanner.close();
    }

    public static void testFunction(DoubleUnaryOperator f, String functionDescription, double x) {
        System.out.println("\nTesting f(x) = " + functionDescription);
        double fx = f.applyAsDouble(x);
        double fNegX = f.applyAsDouble(-x);
        System.out.println("f(" + x + ") = " + fx);
        System.out.println("f(" + (-x) + ") = " + fNegX);
        System.out.println("Function type: " + checkFunctionType(f, x));
    }

    public static String checkFunctionType(DoubleUnaryOperator f, double x) {
        double fX = f.applyAsDouble(x);
        double fNegX = f.applyAsDouble(-x);
        if (fX == fNegX) return "Even";
        else if (fX == -fNegX) return "Odd";
        else return "Neither";
    }

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

    public static void drawGraph(DoubleUnaryOperator f, double xMin, double xMax, double yMin, double yMax) {
        int width = 120;
        int height = 30;
        double xStep = (xMax - xMin) / width;
        double yStep = (yMax - yMin) / height;

        for (int row = 0; row <= height; row++) {
            double y = yMax - row * yStep;
            StringBuilder line = new StringBuilder();

            for (int col = 0; col <= width; col++) {
                double x = xMin + col * xStep;
                double fx = f.applyAsDouble(x);

                if (Math.abs(fx - y) < yStep / 2) {
                    line.append("·");
                } else if (Math.abs(y) < yStep / 2) {
                    line.append("-");
                } else if (Math.abs(x) < xStep / 2) {
                    line.append("|");
                } else {
                    line.append(" ");
                }
            }
            System.out.println(line);
        }
    }

}
