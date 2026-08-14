package com.adgroup.aicodereview.ai;

import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {

    public String buildPrompt(String diff) {

        return """
                You are a senior Java code reviewer.

                Analyze the following Git diff and provide a detailed code review.

                Focus on:
                1. Bugs or logical errors
                2. Security vulnerabilities
                3. Performance improvements
                4. Code quality and readability
                5. Java and Spring Boot best practices

                If no issues are found, mention positive aspects of the code.

                Return your review in bullet points.

                Git Diff:
                ----------------------------
                %s
                ----------------------------
                """.formatted(diff);
    }
}