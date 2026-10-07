package com.proofsum.model;

import java.util.List;

public record AnswerResponse(
        String documentId,
        String question,
        String answer,
        boolean found,
        List<Evidence> evidence,
        String model
) {}
