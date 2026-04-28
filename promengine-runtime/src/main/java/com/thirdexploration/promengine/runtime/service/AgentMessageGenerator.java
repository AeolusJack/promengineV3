package com.thirdexploration.promengine.runtime.service;

import com.thirdexploration.promengine.core.domain.Response;
import com.thirdexploration.promengine.executor.Orchestrator;
import com.thirdexploration.promengine.executor.execution.ExecutionContext;
import com.thirdexploration.promengine.neuro.web.RippleWebSocketHandler;
import com.thirdexploration.promengine.runtime.dto.GroupChatEvent;
import com.thirdexploration.promengine.runtime.model.AgentGroup;
import com.thirdexploration.promengine.runtime.model.AgentRecord;
import com.thirdexploration.promengine.runtime.model.ChatMessage;
import com.thirdexploration.promengine.runtime.model.GroupAgent;
import com.thirdexploration.promengine.runtime.repository.GroupChatMessageRepository;
import com.thirdexploration.promengine.prompt.core.PromptContext;
import com.thirdexploration.promengine.prompt.core.PromptPipeline;
import com.thirdexploration.promengine.core.domain.UserInput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentMessageGenerator {
    private final ChatClient.Builder chatClientBuilder;
    private final PromptPipeline promptPipeline;
    private final AgentContextBuilder contextBuilder;
    private final GroupChatMessageRepository groupMessageRepo;
    private final Orchestrator orchestrator;

    private final RippleWebSocketHandler rippleHandler;




    private void sendRippleSampled(String type, String sessionId, String agentId, String text) {
        GroupChatEvent build = GroupChatEvent.builder().agentId(agentId).sessionId(sessionId).text(text).type(type).build();
        rippleHandler.sendToSession(sessionId, build);
    }


    /**
     * 生成指定 Agent 在群组讨论中的回复。
     */
    public String generateMessage(AgentGroup group, GroupAgent ga) {
        // 1. 获取 Agent 配置与历史消息
        AgentRecord agent = contextBuilder.getAgentRecord(ga.getAgentId());
        List<ChatMessage> history = groupMessageRepo.findByGroupId(group.getId());
        
        // 2. 构建独立的执行上下文与系统提示词
        PromptContext promptContext = contextBuilder.buildGroupPromptContext(group, ga, agent, history);
        String systemPrompt = promptPipeline.render(promptContext);
        systemPrompt = promptPipeline.compress(systemPrompt);
        
        // 3. 构造包含历史消息的完整上下文
        StringBuilder fullPrompt = new StringBuilder(systemPrompt)
                .append("\n\n群组讨论历史：\n")
                .append(formatHistory(history, ga));
        
        // 手动补充当前轮次的任务提示
        String taskPrompt = String.format("现在轮到你（%s）发言了。请你以\"%s\"的角色身份，对话题\"%s\"发表你的专业看法。请确保你的回复言之有物，控制在5-8句话以内。",
                ga.getName(), ga.getRole() != null ? ga.getRole() : "参与者", group.getTopic());
        fullPrompt.append(taskPrompt);

        // 4. 调用 LLM 生成回复
        try {
            UserInput input = UserInput.builder()
                    .sessionId(group.getId())
                    .text(fullPrompt.toString())
                    .timestamp(System.currentTimeMillis())
                    .userId(ga.getAgentId())
                    .domain(null)            // 可选，从请求体获取
                    .build();

            ExecutionContext ctx = ExecutionContext.of(input);
            CompletableFuture<Response> execute = orchestrator.execute(ctx);
            Response join = execute.join();

//            ChatClient chatClient = chatClientBuilder.build();
//            String response = chatClient.prompt()
//                    .user(fullPrompt.toString())
//                    .call()
//                    .content();
            String response = join.getText();
            log.debug("Agent {} generated message: {}", ga.getName(), response);
            //发送websocket事件
            sendRippleSampled("group-message",group.getId(),ga.getAgentId(),response);
            return response != null ? response.trim() : "（Agent 暂时没有想好说什么）";
        } catch (Exception e) {
            log.error("Failed to generate message for agent {}", ga.getAgentId(), e);
            return String.format("（%s 暂时掉线了）", ga.getName());
        }
    }

    /**
     * 格式化历史消息为文本，突出当前 Agent 的角色信息。
     */
    private String formatHistory(List<ChatMessage> history, GroupAgent currentAgent) {
        if (history.isEmpty()) return "暂无历史消息。";
        return history.stream()
                .map(msg -> {
                    if ("agent".equals(msg.getRole())) {
                        String sessionName = msg.getSessionName();
                        String[] split = sessionName.split("_");
                        return split[1] + "（扮演" + getRoleName(history, msg.getUserId()) + "）: " + msg.getContent();
                    } else {
                        return "主持人: " + msg.getContent();
                    }
                })
                .collect(Collectors.joining("\n"));
    }

    private String getRoleName(List<ChatMessage> history, String agentId) {
        // 从历史消息中查找该 Agent 的角色名
        return history.stream()
                .filter(m -> agentId.equals(m.getUserId()) && m.getRole() != null)
                .findFirst()
                .map(ChatMessage::getRole)
                .orElse("参与者");
    }
}