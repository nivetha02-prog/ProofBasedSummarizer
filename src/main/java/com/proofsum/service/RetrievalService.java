package com.proofsum.service;

import com.proofsum.model.Chunk;
import com.proofsum.model.ScoredChunk;
import com.proofsum.store.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/** Embeds chunks into the vector store and pulls back the top-k passages for a question. */
@Service
public class RetrievalService {

    private final EmbeddingService embeddings;
    private final VectorStore store;
    private final int topK;
    private final double minScore;

    public RetrievalService(EmbeddingService embeddings, VectorStore store,
                            @Value("${proofsum.retrieval.top-k}") int topK,
                            @Value("${proofsum.retrieval.min-score}") double minScore) {
        this.embeddings = embeddings;
        this.store = store;
        this.topK = topK;
        this.minScore = minScore;
    }

    public void index(String documentId, List<Chunk> chunks) {
        List<String> texts = chunks.stream().map(Chunk::text).collect(Collectors.toList());
        List<float[]> vectors = embeddings.embedAll(texts);
        store.upsert(documentId, chunks, vectors);
    }

    public List<ScoredChunk> retrieve(String documentId, String question) {
        float[] q = embeddings.embed(question);
        return store.search(documentId, q, topK).stream()
                .filter(sc -> sc.score() >= minScore)
                .collect(Collectors.toList());
    }
}
