package com.thirdexploration.promengine.executor.tool.registry;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.thirdexploration.promengine.executor.tool.annotation.ToolHandler;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ToolDefinition {
    private String name;
    private String description;
    private String version;
    private ToolHandler.Category category;
    private ToolHandler.Location location;
    private boolean enabled;
    private List<ParameterDef> parameters;
    private SandboxPolicyDef sandboxPolicy;
    //  追加字段
    private RiskLevel riskLevel = RiskLevel.LOW;
    private List<String> requiredPermissions = List.of();

    public enum RiskLevel { LOW, MEDIUM, HIGH }
    @Data
    @Builder
    public static class ParameterDef {
        private String name;
        private String description;
        private String type;        // string, number, boolean, object, array
        private boolean required;
        private String example;
        private boolean sensitive;
        private List<String> allowedValues;
        private String pattern;
    }

    @Data
    @Builder
    public static class SandboxPolicyDef {
        private List<String> allowedPaths;
        private boolean allowNetwork;
        private List<String> allowedDomains;
        private int maxMemoryMB;
        private int maxExecutionSeconds;
        private boolean requireConfirmation;
    }

    /** 生成 JSON Schema 字符串，供 LLM function calling 使用 */
    // 在 ToolDefinition 类中添加
    public String toJsonSchema() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ArrayNode required = schema.putArray("required");

        for (ParameterDef param : parameters) {
            ObjectNode prop = properties.putObject(param.getName());
            prop.put("type", param.getType());
            prop.put("description", param.getDescription());
            if (param.getAllowedValues() != null && !param.getAllowedValues().isEmpty()) {
                ArrayNode enumValues = prop.putArray("enum");
                param.getAllowedValues().forEach(enumValues::add);
            }
            if (param.isRequired()) {
                required.add(param.getName());
            }
        }
        return schema.toString();
    }
}