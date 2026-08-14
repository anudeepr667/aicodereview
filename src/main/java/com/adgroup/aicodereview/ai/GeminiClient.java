package com.adgroup.aicodereview.ai;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class GeminiClient {

    @Value("${gemini.api.key}")
    private String apiKey;
    @Value("${gemini.model}")
     private String model;

    private final RestClient restClient;

    public GeminiClient() {
        this.restClient = RestClient.create();
    }

    public String generateReview(String prompt) {

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
                }
        );

        try {

    String response = restClient.post()
            .uri(url)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(String.class);

    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(response);

    return root.path("candidates")
            .get(0)
            .path("content")
            .path("parts")
            .get(0)
            .path("text")
            .asText();

} catch (Exception e) {
    throw new RuntimeException("Unable to generate AI review at the moment.", e);
}
    }
}