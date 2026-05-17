package com.thirdexploration.promengine.skill;

import com.thirdexploration.promengine.skill.config.SkillProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor  // ← 自动为 final 字段和 @NonNull 字段生成构造器注入
public class SkillLoader {

    private final SkillRegistry registry;
    private final SkillProperties properties;  // ← 原来缺失，现在声明为 final，由 Lombok 注入

    private final Map<String, ClassLoader> classLoaders = new ConcurrentHashMap<>();

    /**
     * 从技能目录加载单个技能
     */
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

            URLClassLoader classLoader = new URLClassLoader(
                    new URL[]{jarFile.toURI().toURL()},
                    getClass().getClassLoader()
            );
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

    /**
     * 卸载指定技能
     */
    public void unload(String name) {
        Skill skill = registry.unregister(name);
        if (skill != null) {
            skill.onUnload();
        }
        ClassLoader cl = classLoaders.remove(name);
        if (cl instanceof URLClassLoader ucl) {
            try {
                ucl.close();
            } catch (IOException ignored) {}
        }
    }

    /**
     * 启动时扫描技能目录，若启用热加载则开始监听文件变化
     */
    @PostConstruct
    public void startWatching() {
        if (!properties.isHotReload()) {
            return;
        }

        Thread watcher = new Thread(() -> {
            try {
                WatchService watchService = FileSystems.getDefault().newWatchService();
                Path skillDir = Path.of(properties.getDirectory());
                if (!Files.exists(skillDir)) {
                    Files.createDirectories(skillDir);
                }
                skillDir.register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY);

                while (true) {
                    WatchKey key = watchService.take();
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;

                        Path changed = (Path) event.context();
                        if (changed.toString().endsWith(".jar")) {
                            log.info("Detected skill change: {}", changed);
                            loadFromDirectory(skillDir.resolve(changed).toFile());
                        }
                    }
                    key.reset();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("Skill watcher interrupted");
            } catch (IOException e) {
                log.error("Skill watcher IO error", e);
            }
        });
        watcher.setDaemon(true);
        watcher.setName("skill-watcher");
        watcher.start();
        log.info("Skill hot-reload watcher started for directory: {}", properties.getDirectory());
    }
}