package com.thirdexploration.promengine.verifier.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VerificationResult {
    private boolean passed;
    private String reason;

    public static VerificationResult passed() {
        return VerificationResult.builder().passed(true).build();
    }

    public static VerificationResult blocked(String reason) {
        return VerificationResult.builder().passed(false).reason(reason).build();
    }
}