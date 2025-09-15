package org.example;

import java.util.Scanner;
import java.util.function.DoubleUnaryOperator;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.example.ai.GeminiAI;
import org.example.math.Functions;

public class Main {

    public static void main(String[] args) throws Exception {
        boolean aiMode = false;
        Scanner scanner = new Scanner(System.in);
        String currentFunction = null;

        GeminiAI.loadApiKey();

        while (true) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("exit")) break;

            if (input.equalsIgnoreCase("setapikey")) {
                System.out.print("Enter your Gemini API key: ");
                String key = scanner.nextLine().trim();
                GeminiAI.saveApiKey(key);
                System.out.println("API key saved.");
                continue;
            }


            if (input.equalsIgnoreCase("a47b")) {
                aiMode = true;
                System.out.println("AI mode activated. Type your questions:");
                continue;
            }

            if (aiMode) {
                String aiResponse = GeminiAI.askAI(input);
                System.out.println("AI says: " + aiResponse);
                continue;
            }


            if (input.equalsIgnoreCase("shift")) {
                if (currentFunction == null) {
                    System.out.println("No function with x is set yet.");
                    continue;
                }
                handleFunctionMenu(scanner, currentFunction);
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
                    System.out.println("Function f(x) = " + currentFunction + " stored. (Type 'shift' for options)");
                }
            } catch (Exception e) {
                System.out.println("Error: Invalid input! Try again.");
            }
        }

        if (GraphPlotterFX.isAppLaunched()) {
            GraphPlotterFX.shutdown();
        }
        scanner.close();
        System.out.println("Program exited.");
    }

    private static void handleFunctionMenu(Scanner scanner, String func) {
        DoubleUnaryOperator f = Functions.buildFunction(func);

        System.out.println("Choose an option:" +
                "\n 1: Check Odd/Even" +
                "\n 2: Solve Equation" +
                "\n 3: Draw Graph"
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
                    GraphPlotterFX.launchGraph(func);
                    break;
                default:
                    System.out.println("Not implemented yet.");
            }
        } catch (Exception e) {
            System.out.println("Error in function menu: " + e.getMessage());
        }
    }
}
