package com.proofsum.store;

import com.proofsum.model.Chunk;
import com.proofsum.model.ScoredChunk;

import java.util.List;
import java.util.Optional;

/**
 * Abstraction over the vector database. The default is in-memory; implement this
 * interface to plug in pgvector, Qdrant, Chroma or Pinecone without touching services.
 */
public interface VectorStore {
    void upsert(String documentId, List<Chunk> chunks, List<float[]> vectors);
    List<ScoredChunk> search(String documentId, float[] query, int topK);
    Optional<DocumentInfo> document(String documentId);
    void delete(String documentId);

    record DocumentInfo(String documentId, String fileName, int pages, List<String> sections) {}
    void registerDocument(DocumentInfo info);
}
