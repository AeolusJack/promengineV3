package com.thirdexploration.promengine.web.controller;

import com.thirdexploration.promengine.runtime.review.ReviewHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewHandler reviewHandler;

    @PostMapping("/{reviewId}/decision")
    public void submitDecision(@PathVariable String reviewId,
                               @RequestBody Map<String, String> body) {
        String decision = body.get("decision"); // "approve" / "reject"
        reviewHandler.submitDecision(reviewId, decision);
    }
}