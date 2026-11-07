package com.proofsum.model;

/**
 * Represent a retrieved passage that can support an answer.
 *
 * @param chunkId identifier of the source chunk
 * @param page one-based source page number
 * @param section source section name
 * @param excerpt passage text shown as evidence
 * @param score retrieval similarity score
 */
public record Evidence(String chunkId, int page, String section, String excerpt, double score) {}
