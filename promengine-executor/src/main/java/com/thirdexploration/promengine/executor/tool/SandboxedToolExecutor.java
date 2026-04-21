package com.thirdexploration.promengine.executor.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thirdexploration.promengine.executor.ToolExecutor;
import com.thirdexploration.promengine.executor.tool.registry.ToolRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class SandboxedToolExecutor implements ToolExecutor {

    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Value("${promengine.orchestrator.verbose-logging:false}")
    private boolean verboseLogging;//观测日志开关

    @PostConstruct
    public void init() {
        log.info("SandboxedToolExecutor initialized, relying on @ToolHandler scanning");
    }

    @Override
    public String execute(AssistantMessage.ToolCall toolCall) {
        String toolName = toolCall.name();
        //观测日志开始
        String arguments = toolCall.arguments();
        log.info("=== 执行工具: {} ===", toolName);
        log.info("  参数: {}", arguments);
        //观测日志结束
        var resolved = toolRegistry.resolve(toolName, null);
        if (resolved.isEmpty()) {
            return "错误：未知工具 '" + toolName + "'";
        }

        long start = System.currentTimeMillis();
        try {
            Map<String, Object> args = objectMapper.readValue(toolCall.arguments(), Map.class);
            String result = resolved.get().invoker().invoke(args);
            log.debug("Tool {} executed in {} ms", toolName, System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            log.error("Tool execution failed: {}", toolName, e);
            return "工具执行失败：" + e.getMessage();
        }
    }

    @Override
    public List<ToolCallback> getAvailableTools() {
        List<ToolCallback> callbacks = toolRegistry.getAllToolCallbacks();
        log.info("SandboxedToolExecutor returning {} tools", callbacks.size());
        return callbacks;
    }

    @Override
    public String getToolDescriptions() {
        StringBuilder sb = new StringBuilder();
        Set<String> names = toolRegistry.getRegisteredToolNames();
        for (String name : names) {
            toolRegistry.resolve(name, null).ifPresent(tool -> {
                sb.append("- ").append(tool.definition().getName())
                        .append(": ").append(tool.definition().getDescription()).append("\n");
            });
        }
        return sb.toString();
    }

    @Override
    public List<String> getAvailableToolNames() {
        return toolRegistry.getAllToolCallbacks().stream().map(x -> x.getName()).toList();
    }
}