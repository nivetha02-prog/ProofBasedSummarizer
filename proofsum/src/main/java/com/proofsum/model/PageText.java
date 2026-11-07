package com.proofsum.model;

/**
 * Represent the text extracted from one page of a PDF.
 *
 * @param pageNumber one-based page number in the source document
 * @param text extracted page text; may be empty when no text was extracted
 */
public record PageText(int pageNumber, String text) {}
