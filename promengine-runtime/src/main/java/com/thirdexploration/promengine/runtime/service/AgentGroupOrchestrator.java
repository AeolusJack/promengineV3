package com.thirdexploration.promengine.runtime.service;

import com.thirdexploration.promengine.runtime.model.AgentGroup;
import com.thirdexploration.promengine.runtime.model.ChatMessage;
import com.thirdexploration.promengine.runtime.model.GroupAgent;
import com.thirdexploration.promengine.runtime.repository.GroupChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentGroupOrchestrator {
    private final AgentMessageGenerator messageGenerator;
    private final GroupChatMessageRepository groupMessageRepo;

    /**
     * 驱动一场完整的多轮群组讨论。
     */
    public void executeDiscussion(AgentGroup group) {
        int maxRounds = group.getMaxRounds();
        for (int round = 1; round <= maxRounds; round++) {
            if ("stopped".equals(group.getStatus())) break;
            log.info("Starting round {} for group {}", round, group.getId());
            for (GroupAgent ga : group.getAgents()) {
                try {
                    String msg = messageGenerator.generateMessage(group, ga);
                    groupMessageRepo.saveGroupMessage(group.getId(), "agent",
                            ga.getAgentId(), ga.getName(), ga.getAvatar(), msg);
                } catch (Exception e) {
                    log.error("Error generating message for agent {} in group {}", ga.getAgentId(), group.getId(), e);
                }
            }
        }
        group.setStatus("completed");
    }
}