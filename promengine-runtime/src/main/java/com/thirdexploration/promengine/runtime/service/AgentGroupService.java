package com.thirdexploration.promengine.runtime.service;

import com.thirdexploration.promengine.core.cache.CacheRegion;
import com.thirdexploration.promengine.core.cache.CacheTemplate;
import com.thirdexploration.promengine.runtime.model.AgentGroup;
import com.thirdexploration.promengine.runtime.model.ChatMessage;
import com.thirdexploration.promengine.runtime.model.GroupAgent;
import com.thirdexploration.promengine.runtime.repository.AgentGroupRepository;
import com.thirdexploration.promengine.runtime.repository.GroupChatMessageRepository;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentGroupService {
    private final AgentGroupRepository groupRepository;
    private final GroupChatMessageRepository groupMessageRepo;
    private final AgentGroupOrchestrator agentGroupOrchestrator;

    private final CacheTemplate cacheTemplate;

    public void startDiscussion(String groupId) {

        AgentGroup group = groupRepository.findById(groupId);
        //completed
        if ("completed".equals(group.getStatus()) || "active".equals(group.getStatus())){
            log.warn("群聊已完成或正在进行中，直接返回，不进行后续逻辑，群聊状态：{}",group.getStatus());
        }
        if ("stopped".equals(group.getStatus())){
            group.setStatus("active");
            groupRepository.update(group);
            //存入缓存
            cacheTemplate.put(CacheRegion.GROUP_STATE, groupId + ":active", true);
        }

        if (group != null) {
            // 异步执行讨论循环
            CompletableFuture.runAsync(() -> runDiscussion(group));
        }
    }

    private void runDiscussion(AgentGroup group) {
        agentGroupOrchestrator.executeDiscussion(group);
    }


    public List<ChatMessage> getMessages(String groupId) throws InvocationTargetException, IllegalAccessException {
         List<ChatMessage> byGroupId = groupMessageRepo.findByGroupId(groupId);
         return   byGroupId.stream().map(x ->{
                String sessionName = x.getSessionName();
                if (StringUtils.isNotBlank(sessionName) && sessionName.contains("_")){
                    String[] split = sessionName.split("_");
                    x.setAgentName(split[1]);
                }
                x.setAgentId(x.getUserId());
                return x;

            }).collect(Collectors.toUnmodifiableList());

    }

    public ChatMessage addMessage(String groupId, String message) {
        //当前用户id
        return groupMessageRepo.saveGroupMessage(groupId, "user", "user-id", "用户", null, message);
    }



    public List<AgentGroup> listGroups(String userId) {
        return groupRepository.findAllByUser(userId);
    }

    public AgentGroup getGroup(String userId, String id) {
        return groupRepository.findById(id);
    }

    /**
     * 创建群聊，群聊创建后需要手动开始
     * @param userId
     * @param body
     * @return
     */
    public AgentGroup createGroup(String userId, Map<String, Object> body) {
        String name = (String) body.get("name");
        AgentGroup group = AgentGroup.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .name(name)
                .topic((String) body.getOrDefault("topic", ""))
                .maxRounds((Integer) body.getOrDefault("maxRounds", 10))
                .autoMode((Boolean) body.getOrDefault("autoMode", true))
                .status("stopped")
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


    /**
     * 停止群聊
     * @param id
     */
    public void stopDiscussion(String id) {

        AgentGroup byId = groupRepository.findById(id);
        //停止群聊
        byId.setStatus("stopped");
        groupRepository.update(byId);
        //数据库停止动作完成后刷缓存
        //存入缓存
        cacheTemplate.put(CacheRegion.GROUP_STATE, id +":active", false);

    }
}