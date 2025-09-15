package org.example;

import java.util.Scanner;
import java.util.function.DoubleUnaryOperator;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.example.math.Functions;

public class Main {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        String currentFunction = null;

        while (true) {
            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("exit")) break;

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

            // Handle implicit multiplication
            input = Functions.fixImplicitMultiplication(input);

            // Equation solving
            if (input.contains("=")) {
                Functions.solveEquation(input);
                continue;
            }

            // Normal calculator
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
