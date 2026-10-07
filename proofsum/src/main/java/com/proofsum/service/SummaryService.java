package com.proofsum.service;

import com.proofsum.model.AnswerResponse;
import com.proofsum.model.ScoredChunk;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "Proof-based summary": instead of free summarising, it asks a fixed set of
 * research questions and returns each answer with its page/section evidence.
 */
@Service
public class SummaryService {

    private static final Map<String, String> ASPECTS = new LinkedHashMap<>();
    static {
        ASPECTS.put("problem",      "What problem or research question does this paper address?");
        ASPECTS.put("method",       "What method, model, or approach does the paper propose or use?");
        ASPECTS.put("data",         "What datasets, materials, or experimental setup are used?");
        ASPECTS.put("results",      "What are the main results and key quantitative findings?");
        ASPECTS.put("limitations",  "What limitations or future work do the authors mention?");
        ASPECTS.put("contribution", "What are the stated contributions or conclusions?");
    }

    private final RetrievalService retrieval;
    private final LlmService llm;

    public SummaryService(RetrievalService retrieval, LlmService llm) {
        this.retrieval = retrieval;
        this.llm = llm;
    }

    public Map<String, AnswerResponse> summarize(String documentId) {
        Map<String, AnswerResponse> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> a : ASPECTS.entrySet()) {
            List<ScoredChunk> hits = retrieval.retrieve(documentId, a.getValue());
            out.put(a.getKey(), llm.answer(documentId, a.getValue(), hits));
        }
        return out;
    }

    public List<String> aspects() { return new ArrayList<>(ASPECTS.keySet()); }
}
