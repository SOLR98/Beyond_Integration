package com.solr98.beyondintegration.feature.enchant;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数学公式解析器：解析附魔分离费用公式字符串并求值。
 * 支持四则运算、括号、幂（^）、一元负号、变量替换、常用数学常量
 * （pi、e）与函数（sqrt/abs/log/sin/cos/tan/min/max/round/ceil/floor 等）。
 */
public class FormulaParser {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 求值公式：先替换变量，再去空白并归一化数学符号，最后递归解析表达式。
     * 解析失败时记录错误日志并返回 0。
     */
    public static double evaluate(String formula, Map<String, Double> variables) {
        if (formula == null || formula.trim().isEmpty()) {
            return 0;
        }

        try {
            String expression = replaceVariables(formula, variables);
            expression = expression.replaceAll("\\s+", "");
            expression = simplifyMathSymbols(expression);

            if (expression.isEmpty()) return 0;

            int[] pos = {0};
            double result = parseExpression(expression, pos);
            return result;
        } catch (Exception e) {
            LOGGER.error("Error evaluating formula '{}': {}", formula, e.getMessage());
            return 0;
        }
    }

    /** 将公式中的变量名（整词匹配）替换为数值字符串 */
    private static String replaceVariables(String formula, Map<String, Double> variables) {
        String result = formula;
        for (Map.Entry<String, Double> entry : variables.entrySet()) {
            Pattern pattern = Pattern.compile("\\b" + Pattern.quote(entry.getKey()) + "\\b");
            result = pattern.matcher(result).replaceAll(entry.getValue().toString());
        }
        return result;
    }

    /** 将 ×·÷− 等数学符号归一化为 * / - */
    private static String simplifyMathSymbols(String expression) {
        return expression
                .replaceAll("[×·]", "*")
                .replaceAll("÷", "/")
                .replaceAll("−", "-");
    }

    /** 解析加/减表达式（最低优先级） */
    private static double parseExpression(String expr, int[] pos) {
        double result = parseTerm(expr, pos);

        while (pos[0] < expr.length()) {
            char op = expr.charAt(pos[0]);
            if (op == '+') {
                pos[0]++;
                result += parseTerm(expr, pos);
            } else if (op == '-') {
                pos[0]++;
                result -= parseTerm(expr, pos);
            } else {
                break;
            }
        }
        return result;
    }

    /** 解析乘/除表达式（除数为 0 时抛异常） */
    private static double parseTerm(String expr, int[] pos) {
        double result = parseFactor(expr, pos);

        while (pos[0] < expr.length()) {
            char op = expr.charAt(pos[0]);
            if (op == '*') {
                pos[0]++;
                result *= parseFactor(expr, pos);
            } else if (op == '/') {
                pos[0]++;
                double divisor = parseFactor(expr, pos);
                if (divisor == 0) {
                    throw new ArithmeticException("Division by zero");
                }
                result /= divisor;
            } else {
                break;
            }
        }
        return result;
    }

    /**
     * 解析最小单元：括号表达式、数字字面量、一元负号、
     * 常量（pi/e）或函数调用，并处理其后的幂运算。
     */
    private static double parseFactor(String expr, int[] pos) {
        if (pos[0] >= expr.length()) {
            throw new IllegalArgumentException("Unexpected end of expression");
        }

        char firstChar = expr.charAt(pos[0]);

        if (firstChar == '(') {
            pos[0]++;
            double result = parseExpression(expr, pos);
            if (pos[0] >= expr.length() || expr.charAt(pos[0]) != ')') {
                throw new IllegalArgumentException("Unmatched parenthesis");
            }
            pos[0]++;
            return parsePower(expr, result, pos);
        }

        if (Character.isDigit(firstChar) || firstChar == '.') {
            int start = pos[0];
            while (pos[0] < expr.length() &&
                    (Character.isDigit(expr.charAt(pos[0])) || expr.charAt(pos[0]) == '.')) {
                pos[0]++;
            }
            double number = Double.parseDouble(expr.substring(start, pos[0]));
            return parsePower(expr, number, pos);
        }

        if (firstChar == '-') {
            pos[0]++;
            return -parseFactor(expr, pos);
        }

        if (Character.isLetter(firstChar)) {
            int start = pos[0];
            while (pos[0] < expr.length() && Character.isLetter(expr.charAt(pos[0]))) {
                pos[0]++;
            }
            String funcName = expr.substring(start, pos[0]);

            if (pos[0] < expr.length() && expr.charAt(pos[0]) == '(') {
                pos[0]++;
                double arg = parseExpression(expr, pos);
                if (pos[0] >= expr.length() || expr.charAt(pos[0]) != ')') {
                    throw new IllegalArgumentException("Unmatched parenthesis for function " + funcName);
                }
                pos[0]++;
                return applyFunction(funcName, arg, expr, pos);
            }

            switch (funcName.toLowerCase()) {
                case "pi": return Math.PI;
                case "e": return Math.E;
                default:
                    throw new IllegalArgumentException("Unknown constant: " + funcName);
            }
        }

        throw new IllegalArgumentException("Invalid character at position " + pos[0] + ": " + firstChar);
    }

    /** 处理幂运算：支持 base ^ exponent */
    private static double parsePower(String expr, double base, int[] pos) {
        if (pos[0] < expr.length() && expr.charAt(pos[0]) == '^') {
            pos[0]++;
            double exponent = parseFactor(expr, pos);
            return Math.pow(base, exponent);
        }
        return base;
    }

    /** 应用数学函数：一元函数（sqrt/abs/log 等）与二元函数（min/max，支持逗号分隔第二个参数） */
    private static double applyFunction(String funcName, double arg, String expr, int[] pos) {
        double arg2 = 0;
        if (pos[0] < expr.length() && expr.charAt(pos[0]) == ',') {
            pos[0]++;
            arg2 = parseExpression(expr, pos);
        }

        switch (funcName.toLowerCase()) {
            case "sqrt":  return Math.sqrt(arg);
            case "abs":   return Math.abs(arg);
            case "log":   return Math.log(arg);
            case "log10": return Math.log10(arg);
            case "sin":   return Math.sin(Math.toRadians(arg));
            case "cos":   return Math.cos(Math.toRadians(arg));
            case "tan":   return Math.tan(Math.toRadians(arg));
            case "min":   return Math.min(arg, arg2);
            case "max":   return Math.max(arg, arg2);
            case "round": return Math.round(arg);
            case "ceil":  return Math.ceil(arg);
            case "floor": return Math.floor(arg);
            default:
                throw new IllegalArgumentException("Unknown function: " + funcName);
        }
    }

    /** 校验公式合法性：用默认测试变量试求值，抛异常则视为不合法 */
    public static boolean validateFormula(String formula) {
        if (formula == null || formula.trim().isEmpty()) {
            return false;
        }

        try {
            Map<String, Double> testVars = new HashMap<>();
            testVars.put("base", 1.0);
            testVars.put("level", 1.0);
            testVars.put("multiplier", 1.0);
            testVars.put("books", 1.0);
            evaluate(formula, testVars);
            return true;
        } catch (Exception e) {
            LOGGER.warn("Formula validation failed for '{}': {}", formula, e.getMessage());
            return false;
        }
    }

    /** 提取公式中出现的全部自定义变量名（排除函数名与常量 pi/e） */
    public static String[] extractVariables(String formula) {
        if (formula == null || formula.trim().isEmpty()) {
            return new String[0];
        }

        Pattern pattern = Pattern.compile("\\b[a-zA-Z_][a-zA-Z0-9_]*\\b");
        Matcher matcher = pattern.matcher(formula);

        java.util.Set<String> variables = new java.util.HashSet<>();
        while (matcher.find()) {
            String var = matcher.group();
            if (!isFunction(var) && !isConstant(var)) {
                variables.add(var);
            }
        }

        return variables.toArray(new String[0]);
    }

    /** 判断名称是否为内置函数（忽略大小写） */
    private static boolean isFunction(String name) {
        String[] functions = {"sqrt", "abs", "log", "log10", "sin", "cos", "tan",
                "min", "max", "round", "ceil", "floor"};
        for (String func : functions) {
            if (func.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /** 判断名称是否为内置常量（pi/e） */
    private static boolean isConstant(String name) {
        return "pi".equalsIgnoreCase(name) || "e".equalsIgnoreCase(name);
    }
}
