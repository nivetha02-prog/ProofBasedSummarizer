package com.proofsum.model;

import jakarta.validation.constraints.NotBlank;

public record QuestionRequest(
        @NotBlank String documentId,
        @NotBlank String question
) {}
