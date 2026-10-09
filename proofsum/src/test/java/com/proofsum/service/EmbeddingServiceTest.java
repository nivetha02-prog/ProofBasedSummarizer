package com.proofsum.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddingServiceTest {

    @Test
    void usesDeterministicNormalizedLocalVectorsWhenNoApiKeyIsConfigured() {
        EmbeddingService service = new EmbeddingService("openai", "http://localhost/embeddings", "model", "");

        float[] first = service.embed("models");
        float[] second = service.embed("model");

        assertEquals("local", service.provider());
        assertEquals(4096, first.length);
        assertEquals(first.length, second.length);

        double norm = 0;
        for (int i = 0; i < first.length; i++) {
            norm += first[i] * first[i];
            assertEquals(first[i], second[i], 0.000001f);
        }
        assertEquals(1.0, Math.sqrt(norm), 0.000001);
        assertTrue(norm > 0);
    }
}
