package com.thirdexploration.promengine.executor.tool.registry;

import com.thirdexploration.promengine.executor.tool.annotation.ToolHandler;
import com.thirdexploration.promengine.executor.tool.annotation.ToolParameter;
import com.thirdexploration.promengine.executor.sandbox.annotation.SandboxPolicy;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class ToolAutoRegistrar {

    private final ToolRegistry toolRegistry;
    private final ApplicationContext applicationContext;

    @PostConstruct
    public void scanAndRegister() {
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(ToolHandler.class);
        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            Object bean = entry.getValue();
            Class<?> beanClass = bean.getClass();
            ToolHandler annotation = AnnotationUtils.findAnnotation(beanClass, ToolHandler.class);
            if (annotation == null) continue;

            Method executeMethod = findExecuteMethod(beanClass);
            List<ToolDefinition.ParameterDef> paramDefs = extractParameters(executeMethod);
            ToolDefinition.SandboxPolicyDef sandboxPolicyDef = buildSandboxPolicyDef(
                    AnnotationUtils.findAnnotation(beanClass, SandboxPolicy.class)
            );

            ToolDefinition definition = ToolDefinition.builder()
                    .name(annotation.name())
                    .description(annotation.description())
                    .version(annotation.version())
                    .category(annotation.category())
                    .location(annotation.location())
                    .enabled(annotation.enabled())
                    .parameters(paramDefs)
                    .sandboxPolicy(sandboxPolicyDef)
                    .build();

            ToolRegistry.ToolInvoker invoker = args -> {
                Object[] methodArgs = resolveMethodArgs(executeMethod, paramDefs, args);
                return (String) executeMethod.invoke(bean, methodArgs);
            };

            toolRegistry.register(definition, invoker);
            log.info("Auto-registered tool: {} v{} (category: {}, location: {})",
                    annotation.name(), annotation.version(), annotation.category(), annotation.location());
        }
    }

    private Method findExecuteMethod(Class<?> clazz) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals("execute") && method.getParameterCount() > 0) {
                return method;
            }
        }
        throw new IllegalStateException("No execute method found in " + clazz.getName());
    }

    private List<ToolDefinition.ParameterDef> extractParameters(Method method) {
        List<ToolDefinition.ParameterDef> defs = new ArrayList<>();
        for (Parameter param : method.getParameters()) {
            ToolParameter tp = param.getAnnotation(ToolParameter.class);
            if (tp == null) continue;
            defs.add(ToolDefinition.ParameterDef.builder()
                    .name(tp.value())
                    .description(tp.description())
                    .type(mapType(param.getType()))
                    .required(tp.required())
                    .example(tp.example())
                    .sensitive(tp.sensitive())
                    .allowedValues(tp.allowedValues().length > 0 ? List.of(tp.allowedValues()) : null)
                    .pattern(tp.pattern().isEmpty() ? null : tp.pattern())
                    .build());
        }
        return defs;
    }

    private String mapType(Class<?> type) {
        if (type == String.class) return "string";
        if (type == Integer.class || type == int.class) return "integer";
        if (type == Boolean.class || type == boolean.class) return "boolean";
        if (type == Double.class || type == double.class) return "number";
        if (type == Map.class) return "object";
        if (type == List.class) return "array";
        return "string";
    }

    private Object[] resolveMethodArgs(Method method, List<ToolDefinition.ParameterDef> defs, Map<String, Object> args) {
        Object[] result = new Object[method.getParameterCount()];
        Parameter[] params = method.getParameters();
        for (int i = 0; i < params.length; i++) {
            ToolParameter tp = params[i].getAnnotation(ToolParameter.class);
            if (tp != null) {
                Object value = args.get(tp.value());
                result[i] = convertValue(value, params[i].getType());
            }
        }
        return result;
    }

    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null) return null;
        if (targetType.isInstance(value)) return value;
        if (targetType == String.class) return value.toString();
        if (targetType == Integer.class || targetType == int.class) {
            return Integer.parseInt(value.toString());
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            return Boolean.parseBoolean(value.toString());
        }
        if (targetType == Double.class || targetType == double.class) {
            return Double.parseDouble(value.toString());
        }
        return value;
    }

    public static ToolDefinition.SandboxPolicyDef buildSandboxPolicyDef(SandboxPolicy sp) {
        if (sp == null) return null;
        return ToolDefinition.SandboxPolicyDef.builder()
                .allowedPaths(List.of(sp.allowedPaths()))
                .allowNetwork(sp.allowNetwork())
                .allowedDomains(List.of(sp.allowedDomains()))
                .maxMemoryMB(sp.maxMemoryMB())
                .maxExecutionSeconds(sp.maxExecutionSeconds())
                .requireConfirmation(sp.requireConfirmation())
                .build();
    }
}