package com.proofsum.store;

import com.proofsum.model.Chunk;
import com.proofsum.model.ScoredChunk;

import java.util.List;
import java.util.Optional;

/**
 * Define operations for indexing, searching, and managing document vectors and metadata.
 * Implementations supply the storage behavior used by retrieval and document APIs.
 */
public interface VectorStore {
    /**
     * Replace the indexed chunks and vectors associated with a document.
     *
     * @param documentId identifier of the document whose entries are stored
     * @param chunks chunks to associate with the document
     * @param vectors vectors corresponding by index to {@code chunks}
     */
    void upsert(String documentId, List<Chunk> chunks, List<float[]> vectors);

    /**
     * Find up to the requested number of chunks most similar to a query vector.
     *
     * @param documentId identifier of the document to search
     * @param query query vector to compare with stored vectors
     * @param topK maximum number of results requested
     * @return matching chunks and their similarity scores, ordered according to the implementation's search contract
     */
    List<ScoredChunk> search(String documentId, float[] query, int topK);

    /**
     * Look up metadata registered for a document.
     *
     * @param documentId identifier of the document to look up
     * @return document metadata when registered, otherwise an empty optional
     */
    Optional<DocumentInfo> document(String documentId);

    /**
     * Remove indexed vectors and metadata for a document.
     *
     * @param documentId identifier of the document to remove
     */
    void delete(String documentId);

    /**
     * Describe document metadata retained alongside its indexed chunks.
     *
     * @param documentId identifier assigned to the document
     * @param fileName original document file name, when available
     * @param pages number of pages in the document
     * @param sections distinct section names found during chunking
     */
    record DocumentInfo(String documentId, String fileName, int pages, List<String> sections) {}

    /**
     * Register or replace metadata for a document.
     *
     * @param info metadata to associate with its document identifier
     */
    void registerDocument(DocumentInfo info);
}
