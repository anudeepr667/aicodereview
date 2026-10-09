
package com.adgroup.aicodereview.ai;

import com.adgroup.aicodereview.dto.AIReviewResult;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AIController {

    private final AIService aiService;

    public AIController(AIService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/ai/review")
    public AIReviewResult reviewCode(@RequestBody String diff) {
        return aiService.reviewCode(diff);
    }
}
