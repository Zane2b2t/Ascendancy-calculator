package org.example;

import java.util.Scanner;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.example.ai.GeminiAI;
import org.example.math.Functions;
import org.example.math.SystemSolver;
import org.example.math.Algorthims;

public class Main {

    public static void main(String[] args) throws Exception {
        Functions.util.ensureConsoleAndRelaunch(args);

        boolean aiMode = false;
        boolean alwaysOnTop = false;
        Scanner scanner = new Scanner(System.in);
        String currentFunction = null;

        GeminiAI.loadApiKey();

        while (true) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("exit")) break;

            // Help / Commands
            if (input.equalsIgnoreCase("help") || input.equalsIgnoreCase("commands")) {
                Functions.util.printHelp();
                continue;
            }

            if (input.equalsIgnoreCase("ontop")) {
                if (!alwaysOnTop) {
                    Functions.util.setConsoleAlwaysOnTop();
                    alwaysOnTop = true;
                    System.out.println("Console set to always on top.");
                } else {
                    Functions.util.removeAlwaysOnTop();
                    alwaysOnTop = false;
                    System.out.println("Console removed from always on top.");
                }
                continue;
            }

            if (input.equalsIgnoreCase("setapikey")) {
                System.out.print("Enter your Gemini API key: ");
                String key = scanner.nextLine().trim();
                GeminiAI.saveApiKey(key);
                System.out.println("API key saved.");
                continue;
            }

            if (input.equalsIgnoreCase("a47b") && !aiMode) {
                aiMode = true;
                System.out.println("AI mode activated. Type your questions:");
                continue;
            }
            if (aiMode && input.equalsIgnoreCase("a47b")) {
                aiMode = false;
                System.out.println("Disabled AI");
                continue;
            }
            if (aiMode) {
                System.out.println("Responding..");
                String aiResponse = GeminiAI.askAI(input);
                System.out.println("<Ascendancy>: " + aiResponse);
                continue;
            }

            if (input.equalsIgnoreCase("wasd")) {
                if (currentFunction == null) {
                    System.out.println("No function with x is set yet.");
                    continue;
                }
                handleFunctionMenu(scanner, currentFunction);
                continue;
            }

            if (input.toLowerCase().startsWith("system")) {
                System.out.println("Enter number of equations:");
                int n = Integer.parseInt(scanner.nextLine());
                String[] equations = new String[n];
                for (int i = 0; i < n; i++) {
                    System.out.println("Enter equation " + (i + 1) + ":");
                    equations[i] = scanner.nextLine();
                }
                SystemSolver.solveSystem(equations);
                continue;
            }

            if (input.toLowerCase().startsWith("f(x) = ")) {
                input = input.substring(7);
            }

            input = Functions.fixImplicitMultiplication(input);

            if (input.contains("=")) {
                Functions.solveEquation(input);
                continue;
            }

            try {
                if (!input.toLowerCase().contains("x")) {
                    Expression expression = new ExpressionBuilder(input).build();
                    System.out.println(expression.evaluate());
                } else {
                    currentFunction = input;
                    System.out.println("Function f(x) = " + currentFunction + " stored. (Type 'wasd' for options)");
                }
            } catch (Exception e) {
                System.out.println("Error: Invalid input! Type 'help' to see usage and examples.");
            }
        }

        if (GraphPlotter.isAppLaunched()) {
            GraphPlotter.shutdown();
        }
        scanner.close();
        System.out.println("Program exited.");
    }

    private static void handleFunctionMenu(Scanner scanner, String func) {
        var f = Functions.buildFunction(func);

        System.out.println("Choose an option:" +
                "\n 1: Check Odd/Even" +
                "\n 2: Solve Equation" +
                "\n 3: Draw Graph" +
                "\n 4: First Derivative at a point" +
                "\n 5: Second Derivative at a point" +
                "\n 6: nth Derivative at a point"
        );

        String choice = scanner.nextLine().trim();
        try {
            switch (choice) {
                case "1":
                    System.out.print("Enter a value for x: ");
                    double x = Double.parseDouble(scanner.nextLine());
                    Functions.testFunction(f, func, x);
                    break;
                case "2":
                    Functions.solveWithMenu(scanner, f);
                    break;
                case "3":
                    GraphPlotter.launchGraph(func);
                    break;
                case "4":
                    System.out.print("Enter a value for x: ");
                    double x1 = Double.parseDouble(scanner.nextLine());
                    double d1 = Algorthims.derivative(f, x1);
                    System.out.println("f'(x) ≈ " + d1);
                    break;
                case "5":
                    System.out.print("Enter a value for x: ");
                    double x2 = Double.parseDouble(scanner.nextLine());
                    double d2 = Algorthims.secondDerivative(f, x2);
                    System.out.println("f''(x) ≈ " + d2);
                    break;
                case "6":
                    System.out.print("Enter order n: ");
                    int n = Integer.parseInt(scanner.nextLine());
                    System.out.print("Enter a value for x: ");
                    double xn = Double.parseDouble(scanner.nextLine());
                    double dn = Algorthims.nthDerivative(f, xn, n);
                    System.out.println("f^(" + n + ")(x) ≈ " + dn);
                    break;
                default:
                    System.out.println("Not implemented yet.");
            }
        } catch (Exception e) {
            System.out.println("Error in function menu: " + e.getMessage());
        }
    }
}
