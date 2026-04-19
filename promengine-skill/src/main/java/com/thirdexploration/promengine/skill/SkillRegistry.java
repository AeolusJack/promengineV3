package com.thirdexploration.promengine.skill;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SkillRegistry {

    private final Map<String, Skill> skills = new ConcurrentHashMap<>();

    public void register(Skill skill) {
        skills.put(skill.getName(), skill);
    }

    public Skill unregister(String name) {
        return skills.remove(name);
    }

    public Skill get(String name) {
        return skills.get(name);
    }

    public Collection<Skill> listAll() {
        return skills.values();
    }
}