
package com.adgroup.aicodereview.ai;

import com.adgroup.aicodereview.dto.AIReviewResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class AIService {

    private final PromptBuilder promptBuilder;
    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    public AIService(
            PromptBuilder promptBuilder,
            GeminiClient geminiClient,
            ObjectMapper objectMapper) {

        this.promptBuilder = promptBuilder;
        this.geminiClient = geminiClient;
        this.objectMapper = objectMapper;
    }

    public AIReviewResult reviewCode(String diff) {

        String prompt = promptBuilder.buildPrompt(diff);

        String response = geminiClient.generateReview(prompt);

        try {
            return objectMapper.readValue(response, AIReviewResult.class);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Unable to parse the AI review response.", e);
        }
    }
}
