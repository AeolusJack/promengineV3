//package com.thirdexploration.promengine.prompt;
//
//import com.thirdexploration.promengine.core.PromptManager;
//import com.thirdexploration.promengine.core.domain.RenderedPrompt;
//import com.thirdexploration.promengine.core.domain.TaskContext;
//import com.thirdexploration.promengine.core.domain.Template;
//import org.springframework.stereotype.Service;
//
//@Service
//public class SimplePromptManager implements PromptManager {
//
//    @Override
//    public RenderedPrompt render(TaskContext ctx) {
//        String prompt = "你是一个有记忆的智能助手。\n" +
//                "可用工具：\n" + ctx.getVariables().getOrDefault("available_tools", "") + "\n" +
//                "用户输入：" + ctx.getUserInput().getText();
//        return RenderedPrompt.builder()
//                .templateId("default")
//                .version("1.0")
//                .finalPrompt(prompt)
//                .tokenCount(prompt.length() / 4)
//                .build();
//    }
//
//    @Override
//    public void registerTemplate(Template template) {}
//
//    @Override
//    public void recordFeedback(String templateId, String version, Feedback feedback) {}
//}