package com.watchtower.watchtower.embedding;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Comparator;
import java.util.List;

/**
 * Thin client for Voyage AI's text embeddings API
 * (https://docs.voyageai.com/reference/embeddings-api). Anthropic doesn't
 * offer an embeddings API itself and recommends Voyage as its embedding
 * partner - see README architecture notes for the full reasoning.
 */
@Component
public class VoyageEmbeddingClient {

    public static final String INPUT_TYPE_QUERY = "query";
    public static final String INPUT_TYPE_DOCUMENT = "document";

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public VoyageEmbeddingClient(@Value("${voyage.api-key}") String apiKey,
                                  @Value("${voyage.base-url}") String baseUrl,
                                  @Value("${voyage.model}") String model) {
        this.apiKey = apiKey;
        this.model = model;
        // RestClient's default HttpClient-based request factory opens an
        // NIO selector internally, which hits the same Windows/sandbox
        // loopback-socket bug documented in README (Phase 1 notes). The
        // classic HttpURLConnection-based factory avoids NIO entirely.
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build();
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Embeds the given texts, preserving input order. inputType should be
     * {@link #INPUT_TYPE_QUERY} or {@link #INPUT_TYPE_DOCUMENT} - Voyage
     * embeds these asymmetrically for better retrieval quality.
     */
    public List<double[]> embed(List<String> texts, String inputType) {
        if (!isConfigured()) {
            throw new IllegalStateException("voyage.api-key (VOYAGE_API_KEY) is not configured");
        }
        if (texts.isEmpty()) {
            return List.of();
        }

        EmbeddingRequest request = new EmbeddingRequest(texts, model, inputType);

        EmbeddingResponse response = restClient.post()
                .uri("/embeddings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(EmbeddingResponse.class);

        if (response == null || response.data() == null) {
            throw new IllegalStateException("Voyage embeddings API returned an empty response");
        }

        return response.data().stream()
                .sorted(Comparator.comparingInt(EmbeddingData::index))
                .map(data -> data.embedding().stream().mapToDouble(Double::doubleValue).toArray())
                .toList();
    }

    private record EmbeddingRequest(
            List<String> input,
            String model,
            @JsonProperty("input_type") String inputType) {
    }

    private record EmbeddingResponse(List<EmbeddingData> data) {
    }

    private record EmbeddingData(List<Double> embedding, int index) {
    }
}
