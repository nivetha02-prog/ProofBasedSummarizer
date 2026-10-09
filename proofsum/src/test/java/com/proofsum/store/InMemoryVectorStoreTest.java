package com.proofsum.store;

import com.proofsum.model.Chunk;
import com.proofsum.model.ScoredChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryVectorStoreTest {

    private final InMemoryVectorStore store = new InMemoryVectorStore();

    @Test
    void returnsTopMatchesInDescendingSimilarityAndScopesByDocument() {
        Chunk aligned = chunk("doc-one", 0);
        Chunk orthogonal = chunk("doc-one", 1);
        store.upsert("doc-one", List.of(aligned, orthogonal), List.of(
                new float[]{1, 0},
                new float[]{0, 1}
        ));
        store.upsert("doc-two", List.of(chunk("doc-two", 0)), List.of(new float[]{1, 0}));

        List<ScoredChunk> matches = store.search("doc-one", new float[]{1, 0}, 1);

        assertEquals(1, matches.size());
        assertEquals(aligned, matches.get(0).chunk());
        assertEquals(1.0, matches.get(0).score(), 0.000001);
        assertTrue(store.search("missing", new float[]{1, 0}, 5).isEmpty());
        assertEquals(1, store.search("doc-two", new float[]{1, 0}, 5).size());
    }

    @Test
    void registersAndDeletesDocumentMetadataAndVectors() {
        VectorStore.DocumentInfo info = new VectorStore.DocumentInfo(
                "doc-one", "paper.pdf", 2, List.of("Abstract"));
        store.registerDocument(info);
        store.upsert("doc-one", List.of(chunk("doc-one", 0)), List.of(new float[]{1, 0}));

        assertEquals(info, store.document("doc-one").orElseThrow());
        store.delete("doc-one");

        assertFalse(store.document("doc-one").isPresent());
        assertTrue(store.search("doc-one", new float[]{1, 0}, 5).isEmpty());
    }

    private static Chunk chunk(String documentId, int ordinal) {
        return new Chunk(documentId + "-c" + ordinal, documentId, 1, "Abstract", ordinal, "text");
    }
}
