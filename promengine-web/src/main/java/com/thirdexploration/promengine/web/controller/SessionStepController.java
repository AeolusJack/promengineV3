package com.thirdexploration.promengine.web.controller;

import com.thirdexploration.promengine.memory.agent.model.ReActStepRecord;
import com.thirdexploration.promengine.memory.agent.repository.ReActStepRepository;
import com.thirdexploration.promengine.runtime.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/steps")
@RequiredArgsConstructor
public class SessionStepController {

    private final ReActStepRepository stepRepository;

    @GetMapping
    public ApiResponse<List<ReActStepRecord>> getSteps(@PathVariable String sessionId) {
        return ApiResponse.ok(stepRepository.findBySessionId(sessionId));
    }


}