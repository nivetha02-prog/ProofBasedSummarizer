package com.proofsum.model;

/**
 * Represent a paper passage together with its document, page, section, and ordering metadata.
 *
 * @param id unique identifier assigned to this chunk
 * @param documentId identifier of the source document
 * @param pageNumber one-based page number containing the passage
 * @param section section associated with the passage
 * @param ordinal position of this chunk in the document's chunk sequence
 * @param text passage text
 */
public record Chunk(
        String id,
        String documentId,
        int pageNumber,
        String section,
        int ordinal,
        String text
) {}
