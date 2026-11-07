package com.proofsum.service;

import com.proofsum.model.AnswerResponse;
import com.proofsum.model.ScoredChunk;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Produce a proof-based summary by answering a fixed set of research questions
 * and retaining the evidence returned for each answer.
 */
@Service
public class SummaryService {

    /** Map summary aspect names to the questions used to retrieve and answer each aspect. */
    private static final Map<String, String> ASPECTS = new LinkedHashMap<>();
    static {
        ASPECTS.put("problem",      "What problem or research question does this paper address?");
        ASPECTS.put("method",       "What method, model, or approach does the paper propose or use?");
        ASPECTS.put("data",         "What datasets, materials, or experimental setup are used?");
        ASPECTS.put("results",      "What are the main results and key quantitative findings?");
        ASPECTS.put("limitations",  "What limitations or future work do the authors mention?");
        ASPECTS.put("contribution", "What are the stated contributions or conclusions?");
    }

    /** Retrieve supporting passages for each aspect. */
    private final RetrievalService retrieval;
    /** Generate an evidence-associated answer for each aspect. */
    private final LlmService llm;

    /**
     * Create summary generation with retrieval and answering collaborators.
     *
     * @param retrieval service used to find passages for each summary question
     * @param llm service used to produce each answer
     */
    public SummaryService(RetrievalService retrieval, LlmService llm) {
        this.retrieval = retrieval;
        this.llm = llm;
    }

    /**
     * Answer every configured summary question for a document.
     *
     * @param documentId identifier of the document to summarize
     * @return insertion-ordered mapping from aspect names to their answers
     */
    public Map<String, AnswerResponse> summarize(String documentId) {
        Map<String, AnswerResponse> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> a : ASPECTS.entrySet()) {
            List<ScoredChunk> hits = retrieval.retrieve(documentId, a.getValue());
            out.put(a.getKey(), llm.answer(documentId, a.getValue(), hits));
        }
        return out;
    }

    /**
     * List the configured summary aspect names in processing order.
     *
     * @return a new list of aspect names
     */
    public List<String> aspects() { return new ArrayList<>(ASPECTS.keySet()); }
}
