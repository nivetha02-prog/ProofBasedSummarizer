package com.proofsum.model;

import java.util.List;

/**
 * Represent an answer to a document question and the evidence associated with it.
 *
 * @param documentId identifier of the queried document
 * @param question question that was answered
 * @param answer answer text returned by the configured or fallback responder
 * @param found whether the answer indicates that relevant information was found
 * @param evidence evidence passages returned with the answer
 * @param model identifier of the model or answering mode used
 */
public record AnswerResponse(
        String documentId,
        String question,
        String answer,
        boolean found,
        List<Evidence> evidence,
        String model
) {}
