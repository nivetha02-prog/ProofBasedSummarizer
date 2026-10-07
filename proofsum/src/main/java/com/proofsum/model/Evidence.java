package com.proofsum.model;

/** A supporting excerpt returned to the user alongside the answer. */
public record Evidence(String chunkId, int page, String section, String excerpt, double score) {}
