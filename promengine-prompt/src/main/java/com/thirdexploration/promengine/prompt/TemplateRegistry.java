package com.thirdexploration.promengine.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thirdexploration.promengine.prompt.model.PromptTemplate;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class TemplateRegistry {

    private final PromptProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, PromptTemplate> templates = new ConcurrentHashMap<>();

    @PostConstruct
    public void loadTemplates() {
        Path dir = Path.of(properties.getTemplatesPath());
        if (!Files.exists(dir)) {
            log.warn("Template directory not found: {}", dir);
            loadDefaultTemplates();
            return;
        }
        try {
            Files.list(dir).filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                try {
                    PromptTemplate t = objectMapper.readValue(p.toFile(), PromptTemplate.class);
                    templates.put(t.getId(), t);
                    log.debug("Loaded template: {}", t.getId());
                } catch (Exception e) {
                    log.error("Failed to load template {}", p, e);
                }
            });
        } catch (Exception e) {
            log.error("Failed to scan templates", e);
        }
        log.info("Loaded {} prompt templates", templates.size());
    }

    private void loadDefaultTemplates() {
        PromptTemplate defaultTemplate = PromptTemplate.builder()
                .id("default")
                .name("Default Chat")
                .version("1.0")
                .content("You are a helpful assistant.\nUser: {{ input }}\nAssistant:")
                .mode("silicon")
                .build();
        templates.put("default", defaultTemplate);
    }

    public PromptTemplate get(String id) {
        return templates.get(id);
    }

    public void register(PromptTemplate template) {
        templates.put(template.getId(), template);
    }
}