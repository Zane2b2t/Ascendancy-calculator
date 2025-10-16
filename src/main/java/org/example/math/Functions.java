package org.example.math;

import com.sun.jna.Library;
import com.sun.jna.win32.W32APIOptions;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

import java.util.List;
import java.util.Scanner;
import java.util.function.DoubleUnaryOperator;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
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


    public class util {

        public interface WinAPI extends Library {
            WinAPI INSTANCE = Native.load("kernel32", WinAPI.class);

            WinDef.HWND GetConsoleWindow();
        }

        public static void setConsoleAlwaysOnTop() {
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                try {
                    WinDef.HWND consoleWindow = WinAPI.INSTANCE.GetConsoleWindow();
                    if (consoleWindow != null && !consoleWindow.getPointer().equals(com.sun.jna.Pointer.NULL)) {
                        User32.INSTANCE.SetWindowPos(consoleWindow,
                                new WinDef.HWND(new com.sun.jna.Pointer(-1)),
                                0, 0, 0, 0,
                                0x0001 | 0x0002);
                    }
                } catch (Exception e) {
                    System.err.println("Could not set console always on top: " + e.getMessage());
                }
            }
        }

        public static void removeAlwaysOnTop() {
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                try {
                    WinDef.HWND consoleWindow = WinAPI.INSTANCE.GetConsoleWindow();
                    if (consoleWindow != null && !consoleWindow.getPointer().equals(com.sun.jna.Pointer.NULL)) {
                        User32.INSTANCE.SetWindowPos(consoleWindow,
                                new WinDef.HWND(new com.sun.jna.Pointer(-2)),
                                0, 0, 0, 0,
                                0x0001 | 0x0002);
                    }
                } catch (Exception e) {
                    System.err.println("Could not remove always on top: " + e.getMessage());
                }
            }
        }

        private static boolean shouldSetAlwaysOnTop(String[] args) {
            for (String arg : args) {
                if ("--always-on-top".equals(arg) || "--ontop".equals(arg)) {
                    return true;
                }
            }
            return false;
        }

        public static void ensureConsoleAndRelaunch(String[] args) {
            try {
                boolean alwaysOnTop = shouldSetAlwaysOnTop(args);

                if (java.lang.System.console() != null) {
                    if (alwaysOnTop) {
                        setConsoleAlwaysOnTop();
                    }
                    return;
                }

                for (String a : args) if ("--console-launched".equals(a)) {
                    if (alwaysOnTop) {
                        setConsoleAlwaysOnTop();
                    }
                    return;
                }

                java.security.CodeSource cs = new Object(){}.getClass().getEnclosingClass().getProtectionDomain().getCodeSource();

                if (cs == null) return;

                String jar = new java.io.File(cs.getLocation().toURI()).getAbsolutePath();

                if (!jar.endsWith(".jar")) return;

                String os = System.getProperty("os.name").toLowerCase();

                String javaBin = new java.io.File(System.getProperty("java.home"), "bin" + java.io.File.separator + (os.contains("win") ? "java.exe" : "java")).getAbsolutePath();

                // Build arguments string to pass through
                StringBuilder argsBuilder = new StringBuilder();
                for (String arg : args) {
                    if (!"--console-launched".equals(arg)) {
                        argsBuilder.append(" \"").append(arg).append("\"");
                    }
                }
                String argsString = argsBuilder.toString();

                if (os.contains("win")) {
                    new java.lang.ProcessBuilder("cmd","/c","start","", "cmd","/k",
                            String.format("\"%s\" -jar \"%s\" --console-launched%s", javaBin, jar, argsString)).start();
                    System.exit(0);
                } else if (os.contains("mac")) {
                    new java.lang.ProcessBuilder("osascript","-e",
                            "tell application \"Terminal\" to do script \"" + javaBin.replace("\\","\\\\").replace("\"","\\\"") + " -jar \\\"" + jar.replace("\\","\\\\").replace("\"","\\\"") + "\\\" --console-launched" + argsString + "\""
                    ).start();
                    System.exit(0);
                } else {
                    String run = javaBin + " -jar \"" + jar + "\" --console-launched" + argsString + "; exec $SHELL";
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
        public static void printHelp() {
            System.out.println("Ascendancy.calc - Commands and Usage:\n" +
                    "\n" +
                    "Commands:\n" +
                    " - exit             : Quit the program\n" +
                    " - help | commands  : Show this help\n" +
                    " - ontop            : Toggle console always-on-top\n" +
                    " - setapikey        : Enter and save Gemini API key\n" +
                    " - a47b             : Toggle AI Q&A mode (type again to disable)\n" +
                    " - wasd            : Open function tools menu for current f(x)\n" +
                    " - system           : Solve a system of equations (you will be prompted)\n" +
                    "\n" +
                    "Math input:\n" +
                    " - Enter numeric expressions to evaluate, e.g. 2+3*4, sin(1), sqrt(2).\n" +
                    " - Enter a function in x to store it, e.g. x^2+3x+2 or f(x) = x^3 - 1.\n" +
                    "   Then type 'wasd' to: check odd/even, solve, draw graph, or compute derivatives.\n" +
                    " - Enter an equation to solve, e.g. 2x+5 = 11, sin(x) = 0.5, or x = 3.\n" +
                    "\n" +
                    "Examples:\n" +
                    " - 3+4/2\n" +
                    " - f(x) = x^2 + 2x + 1   (then type 'wasd')\n" +
                    " - 2x + 5 = 11   (shows solutions to x)\n" +
                    " - you can also add restrictions like: e^x where x>=-1 and x<=1  or  2x where 0<x and x<6  or 2x where 0<x<6\n" +
                    " - system               (then enter number of equations and each equation)\n"
            );
        }
    }

    public class fun {

    }
}
