package com.adgroup.aicodereview.ai;

import org.springframework.stereotype.Service;

@Service
public class AIService {

    private final PromptBuilder promptBuilder;
    private final GeminiClient geminiClient;

    public AIService(PromptBuilder promptBuilder, GeminiClient geminiClient) {
        this.promptBuilder = promptBuilder;
        this.geminiClient = geminiClient;
    }

    public String reviewCode(String diff) {

        String prompt = promptBuilder.buildPrompt(diff);

        return geminiClient.generateReview(prompt);
    }
}