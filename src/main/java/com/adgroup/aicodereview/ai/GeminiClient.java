
package com.adgroup.aicodereview.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

@Component
public class GeminiClient {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(GeminiClient.class);

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.model}")
    private String model;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GeminiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;

        HttpClientSettings settings =
                HttpClientSettings.defaults()
                        .withReadTimeout(Duration.ofMinutes(2));

        ClientHttpRequestFactory requestFactory =
                ClientHttpRequestFactoryBuilder.detect()
                        .build(settings);

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    public String generateReview(String prompt) {

        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException(
                    "Cannot request an AI review with an empty prompt.");
        }

        String url =
                "https://generativelanguage.googleapis.com/v1beta/models/"
                        + model
                        + ":generateContent?key="
                        + apiKey;

        Map<String, Object> body = Map.of(
                "contents",
                new Object[]{
                        Map.of(
                                "parts",
                                new Object[]{
                                        Map.of("text", prompt)
                                }
                        )
                },
                "generationConfig",
                Map.of("responseMimeType", "application/json")
        );

        try {
            String response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            if (response == null || response.isBlank()) {
                throw new IllegalStateException(
                        "Gemini returned an empty HTTP response body.");
            }

            JsonNode root = objectMapper.readTree(response);
            JsonNode candidates = root.path("candidates");

            if (!candidates.isArray() || candidates.isEmpty()) {
                String blockReason =
                        root.path("promptFeedback")
                                .path("blockReason")
                                .asText("not provided");

                LOGGER.warn(
                        "Gemini returned no candidates. blockReason={}",
                        blockReason);

                throw new IllegalStateException(
                        "Gemini returned no review candidate. "
                                + "Check API response and safety feedback.");
            }

            JsonNode candidate = candidates.get(0);
            String finishReason =
                    candidate.path("finishReason").asText("not provided");

            JsonNode parts = candidate.path("content").path("parts");

            if (!parts.isArray() || parts.isEmpty()) {
                LOGGER.warn(
                        "Gemini candidate contains no text parts. finishReason={}",
                        finishReason);

                throw new IllegalStateException(
                        "Gemini returned a candidate without text content.");
            }

            StringBuilder text = new StringBuilder();

            for (JsonNode part : parts) {
                JsonNode textNode = part.get("text");

                if (textNode != null && textNode.isTextual()) {
                    text.append(textNode.asText());
                }
            }

            String result = text.toString().trim();

            if (result.isBlank()) {
                LOGGER.warn(
                        "Gemini returned blank text. finishReason={}",
                        finishReason);

                throw new IllegalStateException(
                        "Gemini returned blank review text.");
            }

            LOGGER.debug(
                    "Gemini response received. finishReason={}, responseLength={}",
                    finishReason, result.length());

            return result;

        } catch (Exception e) {
            LOGGER.error(
                    "Gemini review request or response processing failed: {}",
                    e.getMessage());

            throw new RuntimeException(
                    "Unable to generate AI review at the moment.", e);
        }
    }
}
