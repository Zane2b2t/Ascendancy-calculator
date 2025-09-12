package org.example;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class Shaders {

    /**
     * Render a runtime shader function to an image.
     * Supports x, y, and t (time in seconds) variables.
     *
     * @param exprStr user expression (e.g. "sin(sqrt(x*x + y*y)/20 + t)")
     * @param width   image width
     * @param height  image height
     * @param step    distance scaling
     * @param offset  distance offset
     * @param mix     alpha
     * @param rgb0    first color
     * @param rgb1    second color
     * @param time    current time in seconds
     */
    public static BufferedImage renderShader(String exprStr, int width, int height,
                                             double step, double offset, float mix,
                                             Color rgb0, Color rgb1, double time) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        Expression expr;
        try {
            expr = new ExpressionBuilder(exprStr)
                    .variables("x", "y", "t")
                    .build();
        } catch (Exception e) {
            return errorImage(width, height, e.getMessage());
        }

        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                double x = i - width / 2.0;
                double y = j - height / 2.0;

                double value;
                try {
                    expr.setVariable("x", x)
                            .setVariable("y", y)
                            .setVariable("t", time);
                    value = expr.evaluate();
                } catch (Exception e) {
                    value = 0.0;
                }

                double factor = (value + 1.0) / 2.0; // map [-1,1] -> [0,1]
                factor = Math.max(0, Math.min(1, factor));

                float r = (float) (rgb0.getRed() / 255.0 * factor + rgb1.getRed() / 255.0 * (1 - factor));
                float g = (float) (rgb0.getGreen() / 255.0 * factor + rgb1.getGreen() / 255.0 * (1 - factor));
                float b = (float) (rgb0.getBlue() / 255.0 * factor + rgb1.getBlue() / 255.0 * (1 - factor));

                int color = new Color(r, g, b, mix).getRGB();
                img.setRGB(i, j, color);
            }
        }

        return img;
    }

    private static BufferedImage errorImage(int w, int h, String msg) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.BLACK);
        g.drawString("Parse error: " + msg, 10, 20);
        g.dispose();
        return img;
    }

    /** Opens a Swing window to preview an animated shader. */
    public static void showShader(String exprStr) {
        int w = 600, h = 600;
        JFrame f = new JFrame("Animated Shader - " + exprStr);
        f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JLabel label = new JLabel();
        f.add(label);
        f.pack();
        f.setSize(w, h);
        f.setVisible(true);

        // Timer for animation (~30 fps)
        new Timer(33, e -> {
            double time = System.currentTimeMillis() / 1000.0;
            BufferedImage img = renderShader(exprStr, w, h, 40.0, 0.0, 1.0f,
                    Color.CYAN, Color.MAGENTA, time);
            label.setIcon(new ImageIcon(img));
        }).start();
    }

    public static void main(String[] args) {
        // Example animated shader
        showShader("sin(sqrt(x*x + y*y)/20 + t)");
    }
}
