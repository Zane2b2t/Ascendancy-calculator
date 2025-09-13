package org.example;

import java.util.*;
import java.util.Scanner;
import java.util.function.DoubleUnaryOperator;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.example.Shading.GLSLToExp4j;
import org.example.Shading.ShaderViewerLWJGL;

public class Main {

    public static void ensureConsoleAndRelaunch(String[] args) {
        try {
            if (java.lang.System.console() != null) return;
            for (String a : args) if ("--console-launched".equals(a)) return;
            java.security.CodeSource cs = new Object(){}.getClass().getEnclosingClass().getProtectionDomain().getCodeSource();
            if (cs == null) return;
            String jar = new java.io.File(cs.getLocation().toURI()).getAbsolutePath();
            if (!jar.endsWith(".jar")) return;
            String os = System.getProperty("os.name").toLowerCase();
            String javaBin = new java.io.File(System.getProperty("java.home"), "bin" + java.io.File.separator + (os.contains("win") ? "java.exe" : "java")).getAbsolutePath();
            if (os.contains("win")) {
                new java.lang.ProcessBuilder("cmd","/c","start","", "cmd","/k", String.format("\"%s\" -jar \"%s\" --console-launched", javaBin, jar)).start();
                System.exit(0);
            } else if (os.contains("mac")) {
                new java.lang.ProcessBuilder("osascript","-e",
                        "tell application \"Terminal\" to do script \"" + javaBin.replace("\\","\\\\").replace("\"","\\\"") + " -jar \\\"" + jar.replace("\\","\\\\").replace("\"","\\\"") + "\\\" --console-launched\""
                ).start();
                System.exit(0);
            } else {
                String run = javaBin + " -jar \"" + jar + "\" --console-launched; exec $SHELL";
                String[][] t = {
                        {"x-terminal-emulator","-e","bash","-lc",run},
                        {"gnome-terminal","--","bash","-lc",run},
                        {"konsole","-e","bash","-lc",run},
                        {"xterm","-e","bash","-lc",run}
                };
                for (String[] c : t) try { new java.lang.ProcessBuilder(c).start(); System.exit(0); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    public static void main(String[] args) {
        ensureConsoleAndRelaunch(args);
        Scanner scanner = new Scanner(System.in); // allows users to input values in the terminal

        while (true) { // this is an infinite loop (its condition is always met)
            System.out.println("\nEnter your function f(x) = ");
            String input = scanner.nextLine().trim();

            // exit program if input is exit
            if (input.equalsIgnoreCase("exit")) break;

            // if the user already added f(x) = in the input, remove it
            if (input.toLowerCase().startsWith("f(x) = ")) {
                input = input.substring(7);
            }

            // make simple substitutions (e.g., 5x -> 5*x)
            input = input.replaceAll("(\\d)\\s*([a-zA-Z])", "$1*$2");

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
                        "\n 1: Check Odd/Even" +
                        "\n 2: Solve Equation" +
                        "\n 3: Draw Graph" +
                        "\n 4: Shader Viewer (Java Simulation)" +
                        "\n 5: Shader Viewer (LWJGL/OpenGL)" + // New option
                        "\n 6: GLSLToExp4j"); // Moved option
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
                    // ASCII
                    drawGraph(function, -20, 20, -10, 10);
                    // JFreeChart
                    GraphPlotter.drawGraph(input, -20, 20, 0.01);
                    // JavaFX
                    GraphPlotterFX.launchGraph(input);

                } else if (choice.equals("4")) { //simulated using java
                    System.out.println("Enter shader function f(x,y) = ");
                    String shaderFunc = scanner.nextLine().trim();
                    Shaders.showShader(shaderFunc);

                } else if (choice.equals("5")) { //opengl shader
                    System.out.println("\nEnter a GLSL snippet. Assign a 'vec3' to the 'color' variable.");
                    System.out.println("You can use: ");
                    System.out.println(" - vec2 uv: Screen coordinates from (0,0) to (1,1).");
                    System.out.println(" - float u_time: Time in seconds for animations.");
                    System.out.println(" - vec2 mouse: Mouse coordinates from (0,0) to (1,1).");
                    System.out.println("Example: color = vec3(uv.x, uv.y, abs(sin(u_time)));");
                    System.out.print("Your code: ");
                    String shaderCode = scanner.nextLine().trim();
                    try {
                        ShaderViewerLWJGL.launch(shaderCode);
                    } catch (Exception e) {
                        System.err.println("\n--- LWJGL/OpenGL Error ---");
                        System.err.println("Failed to launch the shader viewer.");
                        System.err.println("Please ensure your graphics drivers are up to date and that LWJGL is configured correctly.");
                        e.printStackTrace();
                    }

                } else if (choice.equals("6")) {
                    System.out.println("Enter GLSL code snippet:");
                    StringBuilder glslInput = new StringBuilder();
                    String line;
                    while (!(line = scanner.nextLine()).isEmpty()) {
                        glslInput.append(line).append("\n");
                    }
                    String converted = GLSLToExp4j.convert(glslInput.toString());
                    System.out.println("Converted formula for option 4:");
                    System.out.println(converted);
                }

            } catch (Exception e) { // handles unexpected exceptions caused by user skill issue
                System.out.println("Error: Invalid function! Please check your syntax.");
                System.out.println("You can use:");
                System.out.println("- Basic operations: +, -, *, /, ^");
                System.out.println("- Functions: sin(x), cos(x), tan(x), log(x), sqrt(x)");
                System.out.println("- Example: xcos(x) or 2x^3+3x^2-12x");
            }
        }

        // fix not-reopening when entering a new function
        if (GraphPlotterFX.isAppLaunched()) {
            GraphPlotterFX.shutdown();
        }

        scanner.close();
        System.out.println("Program exited.");
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
