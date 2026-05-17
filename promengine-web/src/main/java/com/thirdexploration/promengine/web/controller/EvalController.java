package com.thirdexploration.promengine.web.controller;

import com.thirdexploration.promengine.core.AgentRuntime;
import com.thirdexploration.promengine.core.domain.Response;
import com.thirdexploration.promengine.core.domain.UserInput;
import com.thirdexploration.promengine.runtime.dto.ApiResponse;
import com.thirdexploration.promengine.runtime.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/eval")

public class EvalController {
    private final AgentRuntime agentRuntime;
    private final ChatMessageRepository chatMessageRepo;
    public EvalController(
            AgentRuntime agentRuntime,
            @Qualifier("chatMessageRepository") ChatMessageRepository chatMessageRepo) {
        this.agentRuntime = agentRuntime;
        this.chatMessageRepo = chatMessageRepo;
    }
    @PostMapping("/run")
    public ApiResponse<Map<String, Object>> runEval(@RequestBody Map<String, String> body) {
        String testFilePath = body.get("testFile");
        // 从文件加载测试用例（JSON Lines 格式：{"input":"...","expected":"..."})
        List<Map<String, String>> cases = loadTestCases(testFilePath);
        int total = cases.size();
        int passed = 0;
        List<String> results = new ArrayList<>();
        for (Map<String, String> c : cases) {
            String input = c.get("input");
            String expected = c.get("expected");
            UserInput userInput = UserInput.builder()
                    .sessionId("eval-" + UUID.randomUUID())
                    .text(input)
                    .timestamp(System.currentTimeMillis())
                    .build();
            try {
                Response resp = agentRuntime.process(userInput).get(30, TimeUnit.SECONDS);
                String actual = resp.getText();
                boolean ok = evaluateResponse(actual, expected);
                if (ok) passed++;
                results.add(String.format("Q: %s\nExpected: %s\nActual: %s\nPass: %s", 
                        input, expected, actual, ok));
            } catch (Exception e) {
                results.add(String.format("Q: %s ERROR: %s", input, e.getMessage()));
            }
        }
        Map<String, Object> stats = Map.of(
                "total", total,
                "passed", passed,
                "passRate", total > 0 ? (double) passed / total : 0.0,
                "details", results
        );
        return ApiResponse.ok(stats);
    }

    private List<Map<String, String>> loadTestCases(String path) {
        // 从 JSONL 文件读取，示例省略解析细节
        return List.of(); // 实际需实现文件读取
    }

    private boolean evaluateResponse(String actual, String expected) {
        // 简单包含判断，后续可集成 LLM-as-Judge
        return actual.contains(expected);
    }
}