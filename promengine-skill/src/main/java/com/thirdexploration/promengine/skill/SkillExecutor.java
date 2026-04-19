package com.thirdexploration.promengine.skill;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class SkillExecutor {

    private final SkillRegistry registry;

    public Map<String, Object> execute(String skillName, Map<String, Object> input) {
        Skill skill = registry.get(skillName);
        if (skill == null) throw new IllegalArgumentException("Skill not found: " + skillName);
        return skill.execute(input);
    }
}