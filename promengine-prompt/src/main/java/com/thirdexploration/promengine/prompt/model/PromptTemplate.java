package com.thirdexploration.promengine.prompt.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PromptTemplate {
    private String id;
    private String name;
    private String version;
    private String content;
    private String mode;
}