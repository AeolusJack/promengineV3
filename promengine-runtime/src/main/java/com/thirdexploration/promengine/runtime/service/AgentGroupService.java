package com.thirdexploration.promengine.runtime.service;

import com.thirdexploration.promengine.runtime.model.AgentGroup;
import com.thirdexploration.promengine.runtime.model.ChatMessage;
import com.thirdexploration.promengine.runtime.model.GroupAgent;
import com.thirdexploration.promengine.runtime.repository.AgentGroupRepository;
import com.thirdexploration.promengine.runtime.repository.AgentRepository;
import com.thirdexploration.promengine.runtime.repository.GroupChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class AgentGroupService {
    private final AgentGroupRepository groupRepository;
    private final GroupChatMessageRepository groupMessageRepo;
    private final AgentGroupOrchestrator agentGroupOrchestrator;



    public void startDiscussion(String groupId) {
        AgentGroup group = groupRepository.findById(groupId);
        if (group != null) {
            // 异步执行讨论循环
            CompletableFuture.runAsync(() -> runDiscussion(group));
        }
    }

    private void runDiscussion(AgentGroup group) {
        agentGroupOrchestrator.executeDiscussion(group);
    }
//
//    private String generateAgentMessage(GroupAgent ga, AgentGroup group) {
//        // 获取 Agent 的系统提示词和历史消息，调用 LLM 生成回复
//        // 这里省略具体实现
//        return "（Agent 发言内容）";
//    }

    public List<ChatMessage> getMessages(String groupId) {
        return groupMessageRepo.findByGroupId(groupId);
    }

    public ChatMessage addMessage(String groupId, String message) {
        return groupMessageRepo.saveGroupMessage(groupId, "user", null, null, null, message);
    }



    public List<AgentGroup> listGroups(String userId) {
        return groupRepository.findAllByUser(userId);
    }

    public AgentGroup getGroup(String userId, String id) {
        return groupRepository.findById(id);
    }

    public AgentGroup createGroup(String userId, Map<String, Object> body) {
        String name = (String) body.get("name");
        AgentGroup group = AgentGroup.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .name(name)
                .topic((String) body.getOrDefault("topic", ""))
                .maxRounds((Integer) body.getOrDefault("maxRounds", 10))
                .autoMode((Boolean) body.getOrDefault("autoMode", true))
                .status("active")
                .createdAt(System.currentTimeMillis())
                .build();
        groupRepository.save(group);
        // add agents
        List<Map<String, Object>> agents = (List<Map<String, Object>>) body.get("agents");
        if (agents != null) {
            for (Map<String, Object> a : agents) {
                GroupAgent ga = GroupAgent.builder()
                        .agentId((String) a.get("agentId"))
                        .name((String) a.get("name"))
                        .avatar((String) a.get("avatar"))
                        .role((String) a.get("role"))
                        .build();
                groupRepository.addAgentToGroup(group.getId(), ga);
            }
        }
        group.setAgents(groupRepository.findAgentsByGroup(group.getId()));
        return group;
    }

    public void deleteGroup(String userId, String id) {
        groupRepository.delete(id);
    }
}