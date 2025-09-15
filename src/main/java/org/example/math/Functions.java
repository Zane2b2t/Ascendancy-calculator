package org.example.math;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

import java.util.List;
import java.util.Scanner;
import java.util.function.DoubleUnaryOperator;

public class Functions {

    // --- Expression utilities ---

    public static double evaluateExpression(String input) {
        Expression expression = new ExpressionBuilder(input).build();
        return expression.evaluate();
    }

    public static DoubleUnaryOperator buildFunction(String expr) {
        return x -> {
            Expression e = new ExpressionBuilder(expr)
                    .variable("x")
                    .build()
                    .setVariable("x", x);
            return e.evaluate();
        };
    }

    public static String fixImplicitMultiplication(String input) {
        input = input.replaceAll("(\\d)\\s*([a-zA-Z])", "$1*$2");   // 5x -> 5*x
        input = input.replaceAll("(\\))\\s*(\\d)", "$1*$2");       // )5 -> )*5
        input = input.replaceAll("(\\d)\\s*\\(", "$1*(");          // 5( -> 5*(
        input = input.replaceAll("(x)\\s*\\(", "$1*(");            // x( -> x*(
        return input;
    }

    public static String readMultiline(Scanner scanner) {
        StringBuilder sb = new StringBuilder();
        String line;
        while (!(line = scanner.nextLine()).isEmpty()) {
            sb.append(line).append("\n");
        }
        return sb.toString();
    }

    // --- Function analysis ---

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

    // --- Equation solving ---

    public static void solveEquation(String input) {
        try {
            String[] parts = input.split("=");
            if (parts.length != 2) throw new RuntimeException("Invalid equation format");

            String lhs = parts[0].trim();
            String rhs = parts[1].trim();

            // Build f(x) = lhs - rhs
            String equation = "(" + lhs + ")-(" + rhs + ")";
            DoubleUnaryOperator f = buildFunction(equation);

            List<Double> roots = Algorthims.findAllRoots(f, -1000, 1000, 1, 1e-7, 100);
            if (roots.isEmpty()) {
                System.out.println("No real solution found.");
            } else {
                System.out.println("Solutions: " + roots);
            }
        } catch (Exception e) {
            System.out.println("Error: Could not solve equation. Please check your syntax.");
        }
    }

    public static void solveWithMenu(Scanner scanner, DoubleUnaryOperator f) {
        System.out.println("Choose root-finding method: 1) Newton-Raphson 2) Bisection");
        String method = scanner.nextLine().trim();
        long startTime = System.nanoTime();

        try {
            List<Double> roots;
            if (method.equals("1")) {
                roots = Algorthims.findAllRootsNewton(f, -470, 470, 1.0, 1e-7, 100);
            } else {
                roots = Algorthims.findAllRoots(f, -470, 470, 0.5, 1e-7, 100);
            }
            long endTime = System.nanoTime();
            System.out.println("Roots ≈ " + roots);
            System.out.println("Calculation time: " + (endTime - startTime) / 1_000_000.0 + " ms");
        } catch (Exception e) {
            System.out.println("Error: Could not solve function.");
        }
    }

    public class util{
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
    }
}
