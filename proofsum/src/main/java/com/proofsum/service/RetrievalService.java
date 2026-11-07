package com.proofsum.service;

import com.proofsum.model.Chunk;
import com.proofsum.model.ScoredChunk;
import com.proofsum.store.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Embed document chunks for indexing and retrieve the highest-scoring passages for questions.
 * Results are limited by the configured top-k value and minimum similarity score.
 */
@Service
public class RetrievalService {

    /** Convert input text to vectors. */
    private final EmbeddingService embeddings;
    /** Store and search the indexed vectors. */
    private final VectorStore store;
    /** Maximum number of candidates requested from the vector store per retrieval. */
    private final int topK;
    /** Minimum score a candidate must meet to be returned. */
    private final double minScore;

    /**
     * Create retrieval behavior with embedding, storage, and result-filter settings.
     *
     * @param embeddings service used to create document and query vectors
     * @param store vector storage implementation
     * @param topK maximum number of candidates requested from the store
     * @param minScore minimum inclusive similarity score for returned candidates
     */
    public RetrievalService(EmbeddingService embeddings, VectorStore store,
                            @Value("${proofsum.retrieval.top-k}") int topK,
                            @Value("${proofsum.retrieval.min-score}") double minScore) {
        this.embeddings = embeddings;
        this.store = store;
        this.topK = topK;
        this.minScore = minScore;
    }

    /**
     * Embed each chunk's text and replace the document's indexed entries.
     *
     * @param documentId identifier of the document being indexed
     * @param chunks chunks whose text is embedded and stored
     */
    public void index(String documentId, List<Chunk> chunks) {
        List<String> texts = chunks.stream().map(Chunk::text).collect(Collectors.toList());
        List<float[]> vectors = embeddings.embedAll(texts);
        store.upsert(documentId, chunks, vectors);
    }

    /**
     * Retrieve matching passages for a question, excluding scores below the configured threshold.
     *
     * @param documentId identifier of the document to search
     * @param question text to embed as the search query
     * @return store results whose scores are greater than or equal to the minimum score
     */
    public List<ScoredChunk> retrieve(String documentId, String question) {
        float[] q = embeddings.embed(question);
        return store.search(documentId, q, topK).stream()
                .filter(sc -> sc.score() >= minScore)
                .collect(Collectors.toList());
    }
}
