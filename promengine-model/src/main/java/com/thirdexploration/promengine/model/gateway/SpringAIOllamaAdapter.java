//package com.thirdexploration.promengine.model.gateway;
//
//import com.thirdexploration.promengine.core.domain.CompletionChunk;
//import com.thirdexploration.promengine.core.domain.CompletionRequest;
//import com.thirdexploration.promengine.core.domain.CompletionResult;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.ai.chat.client.ChatClient;
//import org.springframework.ai.chat.model.ChatModel;
//import org.springframework.ai.chat.model.ChatResponse;
//import org.springframework.ai.chat.prompt.Prompt;
//import org.springframework.ai.ollama.api.OllamaOptions;
//import org.springframework.ai.chat.messages.UserMessage;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Component;
//
//import java.util.stream.Stream;
//
///**
// * 基于 Spring AI 的 Ollama 适配器实现，替代原有的原生 HTTP 实现。
// * 保持与原有 ModelAdapter 接口完全兼容。
// */
//@Slf4j
//@Component
//public class SpringAIOllamaAdapter implements ModelAdapter {
//
//    private final ChatClient chatClient;
//    // 如果你愿意，也可以直接注入 ChatModel 或 ChatClient.Builder
//    // private final ChatModel chatModel;
//
//    @Autowired
//    public SpringAIOllamaAdapter(ChatModel chatModel) { // 或者注入 ChatClient.Builder
//        // 这是另一种用法，直接注入 ChatClient，可以省去每次都 build 的步骤
//        this.chatClient = ChatClient.builder(chatModel).build();
//    }
//
//    @Override
//    public String getProviderId() {
//        return "ollama";
//    }
//
//    @Override
//    public CompletionResult complete(CompletionRequest request) {
//        long startTime = System.currentTimeMillis();
//        String modelId = request.getModelId();
//        log.info("Spring AI Ollama call with model: {}", modelId);
//
//        // 1. 配置模型参数
//        OllamaOptions options = OllamaOptions.builder()
//                .model(modelId)
//                .temperature((double) request.getTemperature())
//                // 注意这里使用 numPredict 而不是 maxTokens，因为在 M6 版本中，OllamaOptions 的 maxTokens 可能已被弃用或修改[reference:2]
//                .numPredict(request.getMaxTokens() > 0 ? request.getMaxTokens() : 512)
//                .build();
//
//        // 2. 构建请求
//        Prompt prompt = new Prompt(new UserMessage(request.getPrompt()), options);
//
//        try {
//            // 3. 发送请求并获取响应 (使用 call() 方法)[reference:3]
//            ChatResponse response = this.chatClient.prompt(prompt).call().chatResponse();
//
//            // 4. 从响应中提取内容 (使用 getOutput().getText() 方法)[reference:4]
//            String content = response.getResult().getOutput().getText();
//
//            // 5. 获取 Token 用量信息
//            long promptTokens = 0;
//            long completionTokens = 0;
//            if (response.getMetadata() != null) {
//                // 在 M6 中，Usage 对象可能被包装在 metadata 里，需要手动获取
//                var usage = response.getMetadata().getUsage();
//                promptTokens = usage.getPromptTokens();
//                completionTokens = usage.getCompletionTokens();
//            }
//
//            long latencyMs = System.currentTimeMillis() - startTime;
//            log.debug("Ollama response: tokens(prompt={}, completion={}), latency={}ms",
//                    promptTokens, completionTokens, latencyMs);
//
//            return CompletionResult.builder()
//                    .content(content)
//                    .finishReason("stop")
//                    .promptTokens(promptTokens)
//                    .completionTokens(completionTokens)
//                    .latencyMs(latencyMs)
//                    .build();
//
//        } catch (Exception e) {
//            log.error("Spring AI Ollama request failed", e);
//            throw new RuntimeException("Ollama request failed", e);
//        }
//    }
//
//    @Override
//    public Stream<CompletionChunk> stream(CompletionRequest request) {
//        // Spring AI 支持流式调用，但原有接口返回 Stream，我们暂不实现流式，
//        // 可保持 UnsupportedOperationException，或使用 Flux 转 Stream 实现。
//        throw new UnsupportedOperationException("Streaming not implemented in SpringAIOllamaAdapter");
//    }
//
//    @Override
//    public boolean isAvailable() {
//        // 简单检查：尝试调用 ChatClient 的健康检查或直接返回 true（由 Spring 自动配置保证）
//        return true;
//    }
//
//    /**
//     * 简单 token 估算（英文按空格，中文按字符数/2）
//     */
//    private long estimateTokens(String text) {
//        if (text == null) return 0;
//        // 中文字符粗略按 2 字符 1 token，英文按 4 字符 1 token
//        int chineseCount = 0;
//        int englishCount = 0;
//        for (char c : text.toCharArray()) {
//            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
//                chineseCount++;
//            } else if (Character.isLetter(c)) {
//                englishCount++;
//            }
//        }
//        return (chineseCount / 2) + (englishCount / 4) + (text.length() / 10);
//    }
//}