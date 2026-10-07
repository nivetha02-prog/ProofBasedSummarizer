package com.proofsum.model;

/**
 * A passage of a paper with the metadata needed to cite it as proof.
 */
public record Chunk(
        String id,
        String documentId,
        int pageNumber,
        String section,
        int ordinal,
        String text
) {}
