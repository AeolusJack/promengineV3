package com.thirdexploration.promengine.model.routing;

import com.thirdexploration.promengine.core.domain.CompletionRequest;
import com.thirdexploration.promengine.model.config.ModelGatewayProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class SemanticRouter {

    private final ComplexityEvaluator complexityEvaluator;
    private final ModelGatewayProperties properties;

    // 匹配模型名中的参数规模，如 1.5b, 3b, 7b, 14b, 70b
    private static final Pattern SIZE_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)[-_]?b", Pattern.CASE_INSENSITIVE);

    /**
     * 根据提示词复杂度选择最合适的模型名。
     */
    public String select(CompletionRequest request) {
        String prompt = request.getPrompt();
        double complexity = complexityEvaluator.evaluate(prompt);
        log.debug("Prompt complexity score: {}", complexity);

        List<ModelInfo> models = collectAllModels();
        if (models.isEmpty()) {
            log.warn("No models configured, falling back to 'default'");
            return "default";
        }

        // 按尺寸分类
        List<ModelInfo> smallModels = models.stream().filter(m -> m.sizeCategory == SizeCategory.SMALL).toList();
        List<ModelInfo> mediumModels = models.stream().filter(m -> m.sizeCategory == SizeCategory.MEDIUM).toList();
        List<ModelInfo> largeModels = models.stream().filter(m -> m.sizeCategory == SizeCategory.LARGE).toList();

        ModelInfo selected = null;
        if (complexity < 0.3) {
            selected = pickBest(smallModels, models);
        } else if (complexity < 0.7) {
            selected = pickBest(mediumModels, models);
        } else {
            selected = pickBest(largeModels, models);
        }

        if (selected == null) {
            selected = models.get(0); // fallback
        }

        log.info("Semantic router selected model '{}' (category: {}) for complexity {}",
                selected.model.getName(), selected.sizeCategory, complexity);
        request.setModelId( selected.model.getName());
        return selected.providerId;
    }

    /**
     * 从候选列表中挑选最优模型，若候选为空则回退到全局列表的第一个。
     */
    private ModelInfo pickBest(List<ModelInfo> candidates, List<ModelInfo> all) {
        if (!candidates.isEmpty()) {
            // 过滤掉 model 为 null 的异常项（防御）
            List<ModelInfo> validCandidates = candidates.stream()
                    .filter(m -> m.model != null)
                    .toList();

            if (!validCandidates.isEmpty()) {
                return validCandidates.stream()
                        .min(Comparator.comparingDouble(m -> {
                            Double cost = m.model.getCostPer1kTokens();
                            return cost != null ? cost : 0.0;
                        }))
                        .orElse(validCandidates.get(0));
            }
        }

        // 候选为空或全部无效，回退到全局列表的第一个有效模型
        if (all != null && !all.isEmpty()) {
            return all.stream()
                    .filter(m -> m.model != null)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    /**
     * 收集所有配置的模型，并推断尺寸分类。
     */
    private List<ModelInfo> collectAllModels() {
        return properties.getProviders().stream()
                .flatMap(provider -> provider.getModels().stream()
                        .map(model -> new ModelInfo(provider.getId(), model, inferSizeCategory(model.getName()))))
                .toList();
    }

    /**
     * 推断模型的尺寸分类。
     * 优先使用配置中的显式标签（如果将来扩展），否则根据名称推断。
     */
    private SizeCategory inferSizeCategory(String modelName) {
        // 可扩展：从 model.getExtraParams() 中读取 "size" 字段
        // 当前基于名称启发式
        int paramB = extractParamBillion(modelName);
        if (paramB <= 3) {
            return SizeCategory.SMALL;
        } else if (paramB <= 14) {
            return SizeCategory.MEDIUM;
        } else {
            return SizeCategory.LARGE;
        }
    }

    /**
     * 从模型名提取参数规模（单位：B）。
     * 例如：qwen2.5:14b → 14，gemma4-custom:q4 → 无法识别，返回默认值 1（视为小模型）
     */
    private int extractParamBillion(String modelName) {
        Matcher matcher = SIZE_PATTERN.matcher(modelName);
        if (matcher.find()) {
            try {
                double val = Double.parseDouble(matcher.group(1));
                return (int) Math.round(val);
            } catch (NumberFormatException ignored) {
            }
        }
        // 针对已知系列但无明确数字的情况（如 gemma4-custom:q4）
        if (modelName.toLowerCase().contains("gemma")) {
            // Gemma 系列：2B 或 7B，保守估计为 2B（小模型）
            return 2;
        }
        // 默认当作小模型处理
        return 1;
    }

    // 内部数据类
    private static class ModelInfo {
        final String providerId;
        final ModelGatewayProperties.ProviderConfig.ModelConfig model;
        final SizeCategory sizeCategory;

        ModelInfo(String providerId, ModelGatewayProperties.ProviderConfig.ModelConfig model, SizeCategory sizeCategory) {
            this.providerId = providerId;
            this.model = model;
            this.sizeCategory = sizeCategory;
        }
    }

    private enum SizeCategory {
        SMALL, MEDIUM, LARGE
    }
}