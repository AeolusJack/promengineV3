package com.thirdexploration.promengine.swarm;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class Blackboard {

    private final Map<String, Object> data = new ConcurrentHashMap<>();

    public void put(String key, Object value) {
        data.put(key, value);
    }

    public Object get(String key) {
        return data.get(key);
    }

    public Map<String, Object> getAll() {
        return Map.copyOf(data);
    }

    public void clear() {
        data.clear();
    }
}