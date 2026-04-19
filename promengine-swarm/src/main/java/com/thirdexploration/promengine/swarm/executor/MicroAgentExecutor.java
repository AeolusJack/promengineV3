package com.thirdexploration.promengine.swarm.executor;

import com.thirdexploration.promengine.core.ModelGateway;
import com.thirdexploration.promengine.core.domain.CompletionRequest;
import com.thirdexploration.promengine.core.domain.CompletionResult;
import com.thirdexploration.promengine.core.domain.TaskContext;
import com.thirdexploration.promengine.swarm.MicroAgent;
import com.thirdexploration.promengine.swarm.MicroAgentResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MicroAgentExecutor {

    private final ModelGateway modelGateway;

    public MicroAgentResult execute(MicroAgent agent, TaskContext ctx) {
        String prompt = agent.getPromptTemplate().replace("{subtask}", 
                agent.getConfig().get("subtask").toString());

        CompletionRequest request = CompletionRequest.builder()
                .modelId("local-ollama")
                .prompt(prompt)
                .maxTokens(500)
                .temperature(0.3f)
                .build();

        try {
            CompletionResult result = modelGateway.complete(request);
            return MicroAgentResult.success(agent.getId(), result.getContent(), 
                    result.getCompletionTokens() > 0 ? 0.9f : 0.5f);
        } catch (Exception e) {
            return MicroAgentResult.failure(agent.getId(), e.getMessage());
        }
    }
}