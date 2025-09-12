package org.example;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import java.awt.*;
import java.util.function.DoubleUnaryOperator;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

public class GraphPlotter {

    private static XYSeries series;
    private static JFreeChart chart;

    public static void drawGraph(String exprString, double xMin, double xMax, double step) {
        // Build dataset
        series = new XYSeries("f(x)");
        updateSeries(exprString, xMin, xMax, step);

        XYSeriesCollection dataset = new XYSeriesCollection(series);
        chart = ChartFactory.createXYLineChart(
                "f(x) = " + exprString,
                "x",
                "f(x)",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        XYPlot plot = chart.getXYPlot();

        // Axes with zero baselines
        NumberAxis domain = (NumberAxis) plot.getDomainAxis();
        NumberAxis range = (NumberAxis) plot.getRangeAxis();
        plot.setDomainZeroBaselineVisible(true);
        plot.setRangeZeroBaselineVisible(true);

        // Gridlines
        plot.setDomainGridlinesVisible(true);
        plot.setRangeGridlinesVisible(true);

        // Chart panel with interactivity
        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setMouseWheelEnabled(true);   // zoom with wheel
        chartPanel.setDomainZoomable(true);      // zoom x
        chartPanel.setRangeZoomable(true);       // zoom y
        chartPanel.setFillZoomRectangle(true);
        chartPanel.setZoomAroundAnchor(true);

        // Allow panning with mouse drag
        chartPanel.setEnabled(true);

        // Function input panel
        JTextField functionInput = new JTextField(exprString, 25);
        JButton updateBtn = new JButton("Update");
        JPanel controlPanel = new JPanel(new FlowLayout());
        controlPanel.add(new JLabel("f(x) = "));
        controlPanel.add(functionInput);
        controlPanel.add(updateBtn);

        updateBtn.addActionListener(e -> {
            String newExpr = functionInput.getText().trim();
            updateSeries(newExpr, xMin, xMax, step);
            chart.setTitle("f(x) = " + newExpr);
        });

        JFrame frame = new JFrame("Graph Viewer");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(900, 700);
        frame.setLayout(new BorderLayout());
        frame.add(chartPanel, BorderLayout.CENTER);
        frame.add(controlPanel, BorderLayout.SOUTH);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void updateSeries(String exprString, double xMin, double xMax, double step) {
        series.clear();
        try {
            Expression expr = new ExpressionBuilder(exprString).variable("x").build();
            for (double x = xMin; x <= xMax; x += step) {
                double y = expr.setVariable("x", x).evaluate();
                if (!Double.isNaN(y) && !Double.isInfinite(y)) {
                    series.add(x, y);
                }
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null,
                    "Invalid function: " + exprString,
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}
