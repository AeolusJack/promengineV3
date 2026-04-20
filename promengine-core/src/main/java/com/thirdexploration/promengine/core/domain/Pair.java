package com.thirdexploration.promengine.core.domain;

/**
     * 简单的 Pair 实现。
     */
    public record Pair<L, R>(L left, R right) {
        public static <L, R> Pair<L, R> of(L left, R right) {
            return new Pair<>(left, right);
        }
    }