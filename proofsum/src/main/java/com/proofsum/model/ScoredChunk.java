package com.proofsum.model;

/**
 * Pair a retrieved passage with its similarity score.
 *
 * @param chunk retrieved passage and source metadata
 * @param score similarity score assigned during retrieval
 */
public record ScoredChunk(Chunk chunk, double score) {}
