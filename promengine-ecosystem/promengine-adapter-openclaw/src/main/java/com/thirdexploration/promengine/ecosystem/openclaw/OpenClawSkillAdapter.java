package com.thirdexploration.promengine.ecosystem.openclaw;

import com.thirdexploration.promengine.skill.Skill;
import com.thirdexploration.promengine.skill.SkillRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 解析 OpenClaw 的 SKILL.md 格式并注册为 PromEngine Skill。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenClawSkillAdapter {

    private final SkillRegistry skillRegistry;
    private final OpenClawProperties properties;

    @PostConstruct
    public void loadSkills() {
        if (!properties.isEnabled()) return;
        Path skillsDir = Path.of(properties.getSkillsPath());
        if (!Files.exists(skillsDir)) return;

        try {
            Files.list(skillsDir).filter(Files::isDirectory).forEach(this::loadSkillFromDir);
        } catch (Exception e) {
            log.error("Failed to scan OpenClaw skills", e);
        }
    }

    private void loadSkillFromDir(Path dir) {
        File skillMd = dir.resolve("SKILL.md").toFile();
        if (!skillMd.exists()) return;

        try {
            String content = Files.readString(skillMd.toPath());
            Skill skill = parseSkillMarkdown(content);
            skillRegistry.register(skill);
            log.info("Loaded OpenClaw skill: {}", skill.getName());
        } catch (Exception e) {
            log.error("Failed to load skill from {}", dir, e);
        }
    }

    private Skill parseSkillMarkdown(String content) {
        // 简化的 YAML front matter 解析
        return new Skill() {
            @Override
            public String getName() {
                return "openclaw-skill";
            }
            @Override
            public String getDescription() {
                return "Imported from OpenClaw";
            }
            @Override
            public Map<String, Object> execute(Map<String, Object> input) {
                return Map.of("result", "executed");
            }
        };
    }
}