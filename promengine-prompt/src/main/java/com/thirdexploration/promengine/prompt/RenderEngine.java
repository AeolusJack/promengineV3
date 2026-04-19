package com.thirdexploration.promengine.prompt;

import com.hubspot.jinjava.Jinjava;
import com.thirdexploration.promengine.prompt.model.PromptTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RenderEngine {

    private final Jinjava jinjava = new Jinjava();

    public String render(PromptTemplate template, Map<String, Object> variables) {
        return jinjava.render(template.getContent(), variables);
    }
}