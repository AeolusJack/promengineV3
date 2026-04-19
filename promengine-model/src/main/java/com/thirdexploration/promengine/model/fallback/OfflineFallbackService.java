package com.thirdexploration.promengine.model.fallback;

import ai.onnxruntime.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class OfflineFallbackService {

    private OrtEnvironment env;
    private OrtSession session;

    @PostConstruct
    public void init() {
        try {
            env = OrtEnvironment.getEnvironment();
            // 假设模型文件存在于 resources
            Path modelPath = Path.of("models/tinyllama.onnx");
            session = env.createSession(modelPath.toString(), new OrtSession.SessionOptions());
            log.info("Offline fallback model loaded");
        } catch (OrtException e) {
            log.warn("Offline fallback model not available, service will be disabled. Error: {}", e.getMessage());

        }
    }

    public String generate(String prompt) {
        if (session == null) return "离线模型不可用";
        try {
            // 简化的推理过程（实际需 tokenize）
            return "（离线模式回复）我暂时无法处理复杂请求，请稍后再试。";
        } catch (Exception e) {
            return "离线服务异常";
        }
    }
}