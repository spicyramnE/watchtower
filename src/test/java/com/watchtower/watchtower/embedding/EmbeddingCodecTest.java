package com.watchtower.watchtower.embedding;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingCodecTest {

    private final EmbeddingCodec codec = new EmbeddingCodec(new ObjectMapper());

    @Test
    void encodeThenDecode_roundTrips() {
        double[] vector = {0.1, -0.5, 0.333, 0.0, 1.0};

        String json = codec.encode(vector);
        double[] decoded = codec.decode(json);

        assertThat(decoded).containsExactly(vector);
    }

    @Test
    void encode_producesValidJsonArray() {
        double[] vector = {1.0, 2.0};

        String json = codec.encode(vector);

        assertThat(json).isEqualTo("[1.0,2.0]");
    }

    @Test
    void decode_handlesEmptyArray() {
        double[] decoded = codec.decode("[]");

        assertThat(decoded).isEmpty();
    }
}
