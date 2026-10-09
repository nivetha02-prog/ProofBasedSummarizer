package com.proofsum.service;

import com.proofsum.model.Chunk;
import com.proofsum.model.ScoredChunk;
import com.proofsum.store.VectorStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RetrievalServiceTest {

    @Test
    void indexesChunksAndFiltersResultsBelowMinimumScore() {
        EmbeddingService embeddings = new EmbeddingService("local", "", "", "");
        RecordingVectorStore store = new RecordingVectorStore();
        RetrievalService service = new RetrievalService(embeddings, store, 2, 0.5);
        Chunk relevantChunk = new Chunk("chunk-1", "doc-1", 1, "Methods", 0, "relevant");
        Chunk irrelevantChunk = new Chunk("chunk-2", "doc-1", 1, "Methods", 1, "irrelevant");
        ScoredChunk relevant = new ScoredChunk(relevantChunk, 0.8);
        ScoredChunk irrelevant = new ScoredChunk(irrelevantChunk, 0.4);
        store.searchResults = List.of(relevant, irrelevant);

        service.index("doc-1", List.of(relevantChunk, irrelevantChunk));
        List<ScoredChunk> results = service.retrieve("doc-1", "question");

        assertEquals("doc-1", store.indexedDocumentId);
        assertEquals(List.of(relevantChunk, irrelevantChunk), store.indexedChunks);
        assertEquals(2, store.indexedVectors.size());
        assertEquals(4096, store.indexedVectors.get(0).length);
        assertEquals("doc-1", store.searchedDocumentId);
        assertEquals(2, store.searchedTopK);
        assertEquals(List.of(relevant), results);
    }

    private static class RecordingVectorStore implements VectorStore {
        private String indexedDocumentId;
        private List<Chunk> indexedChunks;
        private List<float[]> indexedVectors;
        private String searchedDocumentId;
        private int searchedTopK;
        private List<ScoredChunk> searchResults = List.of();

        @Override
        public void upsert(String documentId, List<Chunk> chunks, List<float[]> vectors) {
            indexedDocumentId = documentId;
            indexedChunks = chunks;
            indexedVectors = vectors;
        }

        @Override
        public List<ScoredChunk> search(String documentId, float[] query, int topK) {
            searchedDocumentId = documentId;
            searchedTopK = topK;
            return searchResults;
        }

        @Override
        public Optional<DocumentInfo> document(String documentId) {
            return Optional.empty();
        }

        @Override
        public void registerDocument(DocumentInfo info) {}

        @Override
        public void delete(String documentId) {}
    }
}
