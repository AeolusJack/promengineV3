package com.thirdexploration.promengine.executor.tool.handlers;

import com.thirdexploration.promengine.executor.tool.annotation.ToolHandler;
import com.thirdexploration.promengine.executor.tool.annotation.ToolParameter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ToolHandler(
    name = "calculator",
    description = "执行简单的数学运算，支持加减乘除",
    category = ToolHandler.Category.UTILITY,
    location = ToolHandler.Location.LOCAL   // 本地直接执行，无需沙箱
        ,
        version = "1.2.0"
)
public class CalculatorHandler {

    public String execute(
            @ToolParameter(value = "expression", description = "数学表达式，如 '2 + 3 * 4'", example = "2 + 3")
            String expression) {

        log.info("Calculator executing: {}", expression);
        try {
            // 简单实现：只支持两个数的加减乘除
            String[] parts = expression.split(" ");
            if (parts.length != 3) {
                return "错误：表达式格式应为 '数字 运算符 数字'，例如 '2 + 3'";
            }
            double a = Double.parseDouble(parts[0]);
            double b = Double.parseDouble(parts[2]);
            double result = switch (parts[1]) {
                case "+" -> a + b;
                case "-" -> a - b;
                case "*" -> a * b;
                case "/" -> a / b;
                default -> throw new IllegalArgumentException("不支持的运算符：" + parts[1]);
            };
            return String.valueOf(result);
        } catch (Exception e) {
            return "计算失败：" + e.getMessage();
        }
    }
}