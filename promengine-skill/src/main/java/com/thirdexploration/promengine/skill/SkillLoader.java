package com.thirdexploration.promengine.skill;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class SkillLoader {

    private final SkillRegistry registry;
    private final Map<String, ClassLoader> classLoaders = new ConcurrentHashMap<>();

    public void loadFromDirectory(File skillDir) {
        File configFile = new File(skillDir, "skill.yaml");
        if (!configFile.exists()) return;

        try (FileInputStream fis = new FileInputStream(configFile)) {
            Yaml yaml = new Yaml();
            Map<String, Object> config = yaml.load(fis);
            String name = (String) config.get("name");
            String mainClass = (String) config.get("mainClass");

            File jarFile = new File(skillDir, name + ".jar");
            if (!jarFile.exists()) {
                log.warn("Skill JAR not found: {}", jarFile);
                return;
            }

            URLClassLoader classLoader = new URLClassLoader(new URL[]{jarFile.toURI().toURL()}, getClass().getClassLoader());
            Class<?> clazz = classLoader.loadClass(mainClass);
            Skill skill = (Skill) clazz.getDeclaredConstructor().newInstance();
            skill.onLoad();
            registry.register(skill);
            classLoaders.put(name, classLoader);
            log.info("Loaded skill: {}", name);
        } catch (Exception e) {
            log.error("Failed to load skill from {}", skillDir, e);
        }
    }

    public void unload(String name) {
        Skill skill = registry.unregister(name);
        if (skill != null) {
            skill.onUnload();
        }
        ClassLoader cl = classLoaders.remove(name);
        if (cl instanceof URLClassLoader ucl) {
            try { ucl.close(); } catch (Exception ignored) {}
        }
    }
}