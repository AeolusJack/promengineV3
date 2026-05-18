package com.thirdexploration.promengine.web.controller;

import com.thirdexploration.promengine.core.AgentRuntime;
import com.thirdexploration.promengine.core.domain.Response;
import com.thirdexploration.promengine.core.domain.UserInput;
import com.thirdexploration.promengine.runtime.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/eval")
@RequiredArgsConstructor
public class EvalController {
    private final AgentRuntime agentRuntime;

    @PostMapping("/run")
    public ApiResponse<Map<String, Object>> runEval(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, String>> cases = (List<Map<String, String>>) body.get("cases");
        if (cases == null || cases.isEmpty()) {
            return ApiResponse.error("No test cases provided");
        }

        int total = cases.size();
        int passed = 0;
        List<Map<String, Object>> results = new ArrayList<>();

        for (Map<String, String> c : cases) {
            String input = c.get("input");
            String expected = c.get("expected");
            Map<String, Object> resultEntry = new LinkedHashMap<>();
            resultEntry.put("input", input);
            resultEntry.put("expected", expected);

            try {
                UserInput userInput = UserInput.builder()
                        .sessionId("eval-" + UUID.randomUUID().toString().substring(0, 8))
                        .text(input)
                        .timestamp(System.currentTimeMillis())
                        .build();
                Response resp = agentRuntime.process(userInput).get(30, TimeUnit.SECONDS);
                String actual = resp.getText();
                boolean ok = actual.contains(expected);
                if (ok) passed++;
                resultEntry.put("actual", actual);
                resultEntry.put("pass", ok);
            } catch (Exception e) {
                resultEntry.put("actual", "ERROR: " + e.getMessage());
                resultEntry.put("pass", false);
            }
            results.add(resultEntry);
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("passed", passed);
        stats.put("passRate", total > 0 ? (double) passed / total : 0.0);
        stats.put("results", results);

        return ApiResponse.ok(stats);
    }
}