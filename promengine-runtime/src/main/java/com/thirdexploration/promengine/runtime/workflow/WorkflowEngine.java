package com.thirdexploration.promengine.runtime.workflow;

import com.thirdexploration.promengine.core.agent.ReviewRequest;
import com.thirdexploration.promengine.core.agent.TaskPlan;
import com.thirdexploration.promengine.core.agent.TaskPlan.Step;
import com.thirdexploration.promengine.executor.tool.registry.ToolRegistry;
import com.thirdexploration.promengine.memory.agent.model.AgentExecutionLog;
import com.thirdexploration.promengine.memory.agent.model.AgentHumanReview;
import com.thirdexploration.promengine.memory.agent.repository.AgentExecutionLogRepository;
import com.thirdexploration.promengine.memory.agent.repository.AgentHumanReviewRepository;
import com.thirdexploration.promengine.runtime.review.ReviewHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowEngine {

    private final ToolRegistry toolRegistry;
    private final AgentExecutionLogRepository executionLogRepository;
    private final AgentHumanReviewRepository reviewRepository;
    private final ReviewHandler reviewHandler; // 来自 runtime
    private final ExecutorService parallelExecutor = Executors.newFixedThreadPool(4);

    /**
     * 执行任务计划，支持并行组、重试、日志和人工审核
     * @return 每步 id -> 结果
     */
    public Map<String, Object> execute(TaskPlan plan, String agentId, String sessionId) {
        Map<String, Object> results = new LinkedHashMap<>();
        List<TaskPlan.Step> steps = plan.getSteps();
        if (steps == null || steps.isEmpty()) return Map.of("status", "empty");

        Map<String, List<TaskPlan.Step>> groups = groupByParallelGroup(steps);
        for (List<TaskPlan.Step> group : groups.values()) {
            if (group.size() == 1) {
                Step step = group.get(0);
                results.put(step.getId(), processStep(step, agentId, sessionId));
            } else {
                CompletableFuture.allOf(group.stream()
                        .map(step -> CompletableFuture.supplyAsync(() ->
                                results.put(step.getId(), processStep(step, agentId, sessionId)), parallelExecutor))
                        .toArray(CompletableFuture[]::new)).join();
            }
        }
        results.put("status", "completed");
        return results;
    }

    private String processStep(Step step, String agentId, String sessionId) {
        long start = System.currentTimeMillis();
        AgentExecutionLog logRecord = AgentExecutionLog.builder()
                .id(UUID.randomUUID().toString())
                .agentId(agentId)
                .sessionId(sessionId)
                .taskId(step.getId())
                .stepName(step.getDescription())
                .status("running")
                .startTime(start)
                .createdAt(start)
                .build();
        executionLogRepository.save(logRecord);

        int attempts = 0;
        int maxRetries = step.getMaxRetries() > 0 ? step.getMaxRetries() : 1;
        Exception lastEx = null;
        while (attempts < maxRetries) {
            try {
                // 若该步骤需要人工审核，暂停并等待
                if (step.isRequiresReview()) {
                    waitForReview(step, agentId, sessionId);
                }

                String result = toolRegistry.resolve(step.getTool(), null)
                        .orElseThrow(() -> new RuntimeException("工具未注册: " + step.getTool()))
                        .invoker().invoke(step.getArgs() != null ? step.getArgs() : Map.of());

                executionLogRepository.updateStatus(logRecord.getId(), "success",
                        System.currentTimeMillis(), System.currentTimeMillis() - start, null);
                return result;
            } catch (Exception e) {
                lastEx = e;
                attempts++;
                log.warn("步骤 {} 失败 (尝试 {}/{})", step.getId(), attempts, maxRetries, e);
            }
        }
        executionLogRepository.updateStatus(logRecord.getId(), "failed",
                System.currentTimeMillis(), System.currentTimeMillis() - start, lastEx.getMessage());
        return "error: " + (lastEx != null ? lastEx.getMessage() : "unknown");
    }

    private void waitForReview(Step step, String agentId, String sessionId) {
        ReviewRequest req = new ReviewRequest("workflow-step", step.getDescription(), step.getReviewOptions(),
                List.of("approve", "reject"), 600);
        try {
            String decision = reviewHandler.requestReview(sessionId, req).get(600, TimeUnit.SECONDS);
            AgentHumanReview review = AgentHumanReview.builder()
                    .id(UUID.randomUUID().toString())
                    .agentId(agentId)
                    .sessionId(sessionId)
                    .taskId(step.getId())
                    .requestType("confirmation")
                    .requestData(toJson(req))
                    .status(decision != null ? "approved" : "rejected")
                    .createdAt(System.currentTimeMillis())
                    .build();
            reviewRepository.save(review);
            if (!"approve".equals(decision)) throw new RuntimeException("用户拒绝执行");
        } catch (Exception e) {
            throw new RuntimeException("审核异常或超时", e);
        }
    }

    private Map<String, List<Step>> groupByParallelGroup(List<Step> steps) {
        Map<String, List<Step>> map = new LinkedHashMap<>();
        for (Step s : steps) {
            String key = s.getParallelGroup() != null ? s.getParallelGroup() : UUID.randomUUID().toString();
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
        }
        return map;
    }

    private String toJson(Object obj) { /* 使用 ObjectMapper */ return "{}"; }
}