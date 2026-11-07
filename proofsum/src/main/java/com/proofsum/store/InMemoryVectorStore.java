package com.proofsum.store;

import com.proofsum.model.Chunk;
import com.proofsum.model.ScoredChunk;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Store document chunks and their vectors in process memory and rank them by cosine similarity.
 * The implementation uses concurrent maps for document-level storage and is intended for
 * deployments whose indexed data need not be persisted outside the running process.
 */
@Component
public class InMemoryVectorStore implements VectorStore {

    /**
     * Associate one indexed chunk with the vector used to search for it.
     *
     * @param chunk indexed passage and its source metadata
     * @param vector vector compared with query vectors during search
     */
    private record Entry(Chunk chunk, float[] vector) {}

    /** Hold indexed chunks and vectors grouped by document identifier. */
    private final Map<String, List<Entry>> byDocument = new ConcurrentHashMap<>();
    /** Hold registered document metadata grouped by document identifier. */
    private final Map<String, DocumentInfo> docs = new ConcurrentHashMap<>();

    /**
     * Replace all indexed entries for a document with the supplied chunks and vectors.
     *
     * @param documentId identifier used to group the indexed entries
     * @param chunks chunks to index
     * @param vectors vectors corresponding by index to {@code chunks}
     */
    @Override
    public void upsert(String documentId, List<Chunk> chunks, List<float[]> vectors) {
        List<Entry> entries = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) entries.add(new Entry(chunks.get(i), vectors.get(i)));
        byDocument.put(documentId, entries);
    }

    /**
     * Return up to {@code topK} chunks for a document, ranked by descending cosine similarity.
     *
     * @param documentId identifier of the document to search
     * @param query query vector compared against each stored vector
     * @param topK maximum number of chunks to return
     * @return matching chunks in descending similarity order, or an empty list when the document
     *         has no indexed entries
     */
    @Override
    public List<ScoredChunk> search(String documentId, float[] query, int topK) {
        List<Entry> entries = byDocument.get(documentId);
        if (entries == null) return List.of();
        PriorityQueue<ScoredChunk> heap = new PriorityQueue<>(Comparator.comparingDouble(ScoredChunk::score));
        for (Entry e : entries) {
            double s = cosine(query, e.vector());
            if (heap.size() < topK) heap.add(new ScoredChunk(e.chunk(), s));
            else if (s > heap.peek().score()) { heap.poll(); heap.add(new ScoredChunk(e.chunk(), s)); }
        }
        List<ScoredChunk> out = new ArrayList<>(heap);
        out.sort((a, b) -> Double.compare(b.score(), a.score()));
        return out;
    }

    /**
     * Look up metadata currently registered for a document.
     *
     * @param documentId identifier of the document to look up
     * @return the metadata when present, otherwise an empty optional
     */
    @Override
    public Optional<DocumentInfo> document(String documentId) {
        return Optional.ofNullable(docs.get(documentId));
    }

    /**
     * Register or replace a document's metadata.
     *
     * @param info metadata to store, keyed by its document identifier
     */
    @Override
    public void registerDocument(DocumentInfo info) {
        docs.put(info.documentId(), info);
    }

    /**
     * Remove a document's indexed entries and registered metadata.
     *
     * @param documentId identifier of the document to remove
     */
    @Override
    public void delete(String documentId) {
        byDocument.remove(documentId);
        docs.remove(documentId);
    }

    /**
     * Calculate cosine similarity, treating vectors of different dimensions or zero norm as dissimilar.
     *
     * @param a first vector
     * @param b second vector
     * @return cosine similarity, or zero when dimensions differ or either vector has zero norm
     */
    private static double cosine(float[] a, float[] b) {
        if (a.length != b.length) return 0;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i]; }
        return (na == 0 || nb == 0) ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
