package com.watchtower.watchtower.embedding;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * (De)serializes an embedding vector to/from the JSON string stored in
 * Runbook.embedding. Jackson 3's exceptions are unchecked, so callers don't
 * need to handle a checked failure here.
 */
@Component
public class EmbeddingCodec {

    private final ObjectMapper objectMapper;

    public EmbeddingCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encode(double[] vector) {
        return objectMapper.writeValueAsString(vector);
    }

    public double[] decode(String json) {
        return objectMapper.readValue(json, double[].class);
    }
}
