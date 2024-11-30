package org.example;

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
            if (input.equalsIgnoreCase("exit")) {
                break;
            }

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

                System.out.print("Enter a value for x to test the function: ");
                double x = Double.parseDouble(scanner.nextLine());

                testFunction(function, input, x);

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

        try {
            double fx = f.applyAsDouble(x);
            double fNegX = f.applyAsDouble(-x);

            System.out.println("f(" + x + ") = " + fx);
            System.out.println("f(" + (-x) + ") = " + fNegX);
            System.out.println("Function type: " + checkFunctionType(f, x));
        } catch (Exception e) {
            System.out.println("Error evaluating function! Make sure your function is valid for the given x value.");
        }
    }

    public static String checkFunctionType(DoubleUnaryOperator f, double x) {
        double fX = f.applyAsDouble(x);
        double fNegX = f.applyAsDouble(-x);

        if (fX == fNegX) { //when f(x) = f(-x) the function is even
            return "Even";
        } else if (fX == -fNegX) { //when f(x) = -f(-x) the function is odd, we can rewrite this to use Math.abs() (absolute value) but im lazy, and it's pointless, just wanted to point out it'd work
            return "Odd";
        } else {
            return "Neither";
        }
    }
}