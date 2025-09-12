package org.example.Shading;

import java.util.*;
import java.util.regex.*;

public class GLSLToExp4j {
    private static final Map<String, String> FUNCTION_MAP = new HashMap<>();

    static {
        FUNCTION_MAP.put("sin", "sin");
        FUNCTION_MAP.put("cos", "cos");
        FUNCTION_MAP.put("tan", "tan");
        FUNCTION_MAP.put("sqrt", "sqrt");
        FUNCTION_MAP.put("log", "log");
        FUNCTION_MAP.put("exp", "exp");
        FUNCTION_MAP.put("pow", "^");
        FUNCTION_MAP.put("abs", "abs");
    }


    private static final Map<String, String> UNIFORM_DEFAULTS = Map.of(
            "step", "20",
            "offset", "0",
            "mix", "1"
    );

    public static String convert(String glslCode) {
        String[] lines = glslCode.split("\\r?\\n");
        Map<String, String> variables = new HashMap<>();
        String finalExpr = null;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            line = line.replaceAll("gl_FragCoord\\.x", "x");
            line = line.replaceAll("gl_FragCoord\\.y", "y");

            line = line.replaceAll("(\\d+)\\.0+", "$1");

            for (Map.Entry<String, String> entry : UNIFORM_DEFAULTS.entrySet()) {
                line = line.replaceAll("\\b" + entry.getKey() + "\\b", entry.getValue());
            }

            for (Map.Entry<String, String> entry : FUNCTION_MAP.entrySet()) {
                line = line.replaceAll("\\b" + entry.getKey() + "\\b", entry.getValue());
            }

            Matcher m = Pattern.compile("(?:float\\s+)?(\\w+)\\s*=\\s*(.*?);?$").matcher(line);
            if (m.find()) {
                String var = m.group(1);
                String expr = m.group(2).trim();

                expr = inline(expr, variables);

                variables.put(var, expr);

                if (line.contains("gl_FragColor")) {
                    finalExpr = expr;
                }
            }

            if (line.startsWith("gl_FragColor")) {
                Matcher m2 = Pattern.compile("vec4\\(([^,]+),").matcher(line);
                if (m2.find()) {
                    finalExpr = m2.group(1).trim();
                    finalExpr = inline(finalExpr, variables);
                }
            }
        }

        return finalExpr != null ? finalExpr : "Error: could not parse GLSL";
    }

    private static String inline(String expr, Map<String, String> variables) {
        boolean changed;
        do {
            changed = false;
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                String var = entry.getKey();
                String val = "(" + entry.getValue() + ")";
                if (expr.matches(".*\\b" + var + "\\b.*")) {
                    expr = expr.replaceAll("\\b" + var + "\\b", val);
                    changed = true;
                }
            }
        } while (changed);
        return expr;
    }

    public static void main(String[] args) {
        String glsl = """
            float distance = sqrt(gl_FragCoord.x*gl_FragCoord.x + gl_FragCoord.y*gl_FragCoord.y);
            distance = sin(distance / step + sin(gl_FragCoord.y / step)) * 0.5 + 0.5;
            gl_FragColor = vec4(distance,distance,distance,1.0);
        """;

        System.out.println("Converted formula: " + convert(glsl));
    }
}
