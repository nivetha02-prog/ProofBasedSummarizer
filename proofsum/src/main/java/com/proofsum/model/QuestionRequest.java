package com.proofsum.model;

import jakarta.validation.constraints.NotBlank;

/**
 * Carry a document-scoped question submitted to the ask endpoint.
 *
 * @param documentId identifier of the document to query; must not be blank
 * @param question question text; must not be blank
 */
public record QuestionRequest(
        @NotBlank String documentId,
        @NotBlank String question
) {}
