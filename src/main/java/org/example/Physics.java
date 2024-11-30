package org.example;

import java.util.Scanner;

public class Physics {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("Enter your n value");
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("exit")) {
                break;
            }
            float x = Float.parseFloat(input);
            System.out.println("sinThetaCritical = " + getX(x));
        }
        scanner.close();
    }
    public static float getX(float n) {
        return 1/n;
    }
}
