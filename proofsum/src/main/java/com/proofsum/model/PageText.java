package com.proofsum.model;

/** Raw text extracted from a single PDF page. */
public record PageText(int pageNumber, String text) {}
