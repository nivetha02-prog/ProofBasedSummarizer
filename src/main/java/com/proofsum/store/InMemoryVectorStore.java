package com.proofsum.store;

import com.proofsum.model.Chunk;
import com.proofsum.model.ScoredChunk;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Simple cosine-similarity store held in memory. Good for single-node demos. */
@Component
public class InMemoryVectorStore implements VectorStore {

    private record Entry(Chunk chunk, float[] vector) {}

    private final Map<String, List<Entry>> byDocument = new ConcurrentHashMap<>();
    private final Map<String, DocumentInfo> docs = new ConcurrentHashMap<>();

    @Override
    public void upsert(String documentId, List<Chunk> chunks, List<float[]> vectors) {
        List<Entry> entries = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) entries.add(new Entry(chunks.get(i), vectors.get(i)));
        byDocument.put(documentId, entries);
    }

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

    @Override public Optional<DocumentInfo> document(String documentId) { return Optional.ofNullable(docs.get(documentId)); }
    @Override public void registerDocument(DocumentInfo info) { docs.put(info.documentId(), info); }
    @Override public void delete(String documentId) { byDocument.remove(documentId); docs.remove(documentId); }

    private static double cosine(float[] a, float[] b) {
        if (a.length != b.length) return 0;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i]; }
        return (na == 0 || nb == 0) ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
