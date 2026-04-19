package com.thirdexploration.promengine.core.domain;

import lombok.Builder;
import lombok.Data;

/**
 * 记忆查询条件。
 */
@Data
@Builder
public class Query {
    private String text;
    private String userId;
    private int maxResults;
}