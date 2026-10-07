package com.proofsum.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proofsum.model.AnswerResponse;
import com.proofsum.model.Evidence;
import com.proofsum.model.ScoredChunk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds a grounded prompt from retrieved evidence and asks the LLM to answer
 * using ONLY that evidence, citing [page, section] for every claim.
 * If nothing relevant was retrieved, returns "Not found" without calling the model.
 */
@Service
public class LlmService {

    public static final String NOT_FOUND = "Not found in the provided document.";
    private static final Pattern CITATION = Pattern.compile("\\[(E\\d+)\\]");

    private final String provider;
    private final String anthropicUrl, anthropicModel, anthropicKey;
    private final String openaiUrl, openaiModel, openaiKey;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public LlmService(@Value("${proofsum.llm.provider}") String provider,
                      @Value("${proofsum.llm.anthropic.url}") String anthropicUrl,
                      @Value("${proofsum.llm.anthropic.model}") String anthropicModel,
                      @Value("${proofsum.llm.anthropic.api-key}") String anthropicKey,
                      @Value("${proofsum.llm.openai.url}") String openaiUrl,
                      @Value("${proofsum.llm.openai.model}") String openaiModel,
                      @Value("${proofsum.llm.openai.api-key}") String openaiKey) {
        this.anthropicUrl = anthropicUrl; this.anthropicModel = anthropicModel; this.anthropicKey = anthropicKey;
        this.openaiUrl = openaiUrl; this.openaiModel = openaiModel; this.openaiKey = openaiKey;

        String p = provider == null ? "none" : provider.toLowerCase();
        if (p.equals("anthropic") && anthropicKey.isBlank()) p = "none";
        if (p.equals("openai") && openaiKey.isBlank()) p = "none";
        this.provider = p;
    }

    public AnswerResponse answer(String documentId, String question, List<ScoredChunk> retrieved) {
        List<Evidence> evidence = toEvidence(retrieved);
        if (evidence.isEmpty()) {
            return new AnswerResponse(documentId, question, NOT_FOUND, false, List.of(), "none");
        }

        String answer;
        String modelUsed;
        try {
            switch (provider) {
                case "anthropic" -> { answer = callAnthropic(buildPrompt(question, evidence)); modelUsed = anthropicModel; }
                case "openai"    -> { answer = callOpenAi(buildPrompt(question, evidence));    modelUsed = openaiModel; }
                default          -> { answer = extractiveAnswer(question, evidence);           modelUsed = "extractive-fallback"; }
            }
        } catch (Exception e) {
            answer = extractiveAnswer(question, evidence) +
                     "\n\n(LLM call failed: " + e.getMessage() + " — showing extractive evidence instead.)";
            modelUsed = "extractive-fallback";
        }

        boolean found = !answer.trim().startsWith(NOT_FOUND);
        // Keep only evidence the model actually cited, so every excerpt shown is a real proof.
        List<Evidence> cited = citedOnly(answer, evidence);
        return new AnswerResponse(documentId, question, answer, found, cited.isEmpty() ? evidence : cited, modelUsed);
    }

    /* ---------------- prompt construction ---------------- */

    String buildPrompt(String question, List<Evidence> evidence) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a careful research assistant. Answer the question using ONLY the evidence passages below.\n")
          .append("Rules:\n")
          .append("1. Every factual sentence must end with a citation tag like [E1] or [E2][E4] referring to the passages.\n")
          .append("2. Do not use outside knowledge. Do not guess.\n")
          .append("3. If the passages do not contain the answer, reply exactly: \"").append(NOT_FOUND).append("\"\n")
          .append("4. Be concise. Prefer quoting short phrases from the passages.\n\n")
          .append("EVIDENCE PASSAGES:\n");
        for (int i = 0; i < evidence.size(); i++) {
            Evidence e = evidence.get(i);
            sb.append("[E").append(i + 1).append("] (page ").append(e.page())
              .append(", section: ").append(e.section()).append(")\n")
              .append(e.excerpt()).append("\n\n");
        }
        sb.append("QUESTION: ").append(question).append("\n\nANSWER:");
        return sb.toString();
    }

    /* ---------------- providers ---------------- */

    private String callAnthropic(String prompt) throws IOException, InterruptedException {
        Map<String, Object> body = Map.of(
                "model", anthropicModel,
                "max_tokens", 800,
                "temperature", 0,
                "messages", List.of(Map.of("role", "user", "content", prompt)));
        HttpRequest req = HttpRequest.newBuilder(URI.create(anthropicUrl))
                .header("Content-Type", "application/json")
                .header("x-api-key", anthropicKey)
                .header("anthropic-version", "2023-06-01")
                .timeout(Duration.ofSeconds(90))
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
        JsonNode json = send(req);
        StringBuilder out = new StringBuilder();
        for (JsonNode block : json.get("content")) if (block.has("text")) out.append(block.get("text").asText());
        return out.toString().trim();
    }

    private String callOpenAi(String prompt) throws IOException, InterruptedException {
        Map<String, Object> body = Map.of(
                "model", openaiModel,
                "temperature", 0,
                "messages", List.of(Map.of("role", "user", "content", prompt)));
        HttpRequest req = HttpRequest.newBuilder(URI.create(openaiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + openaiKey)
                .timeout(Duration.ofSeconds(90))
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
        JsonNode json = send(req);
        return json.get("choices").get(0).get("message").get("content").asText().trim();
    }

    private JsonNode send(HttpRequest req) throws IOException, InterruptedException {
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) throw new IOException("HTTP " + res.statusCode() + ": " + res.body());
        return mapper.readTree(res.body());
    }

    /* ---------------- no-LLM fallback: purely extractive ---------------- */

    /** Returns the most question-relevant sentences from the evidence, each tagged with its source. */
    String extractiveAnswer(String question, List<Evidence> evidence) {
        java.util.Set<String> qTerms = new java.util.HashSet<>();
        for (String w : question.toLowerCase().split("[^a-z0-9]+")) if (w.length() > 3) qTerms.add(w);

        record Sent(String text, int idx, int hits) {}
        List<Sent> sents = new ArrayList<>();
        for (int i = 0; i < evidence.size(); i++) {
            for (String s : evidence.get(i).excerpt().split("(?<=[.!?])\\s+")) {
                if (s.length() < 30) continue;
                int hits = 0;
                String low = s.toLowerCase();
                for (String t : qTerms) if (low.contains(t)) hits++;
                if (hits > 0) sents.add(new Sent(s.trim(), i + 1, hits));
            }
        }
        if (sents.isEmpty()) return NOT_FOUND;
        sents.sort((a, b) -> Integer.compare(b.hits(), a.hits()));

        StringBuilder sb = new StringBuilder("Based on the document (extractive answer, no LLM configured):\n\n");
        for (Sent s : sents.subList(0, Math.min(4, sents.size()))) {
            sb.append("• ").append(s.text()).append(" [E").append(s.idx()).append("]\n");
        }
        return sb.toString().trim();
    }

    /* ---------------- helpers ---------------- */

    private List<Evidence> toEvidence(List<ScoredChunk> retrieved) {
        List<Evidence> out = new ArrayList<>();
        for (ScoredChunk sc : retrieved) {
            out.add(new Evidence(sc.chunk().id(), sc.chunk().pageNumber(), sc.chunk().section(),
                    sc.chunk().text(), Math.round(sc.score() * 1000.0) / 1000.0));
        }
        return out;
    }

    private List<Evidence> citedOnly(String answer, List<Evidence> evidence) {
        java.util.LinkedHashSet<Integer> ids = new java.util.LinkedHashSet<>();
        Matcher m = CITATION.matcher(answer);
        while (m.find()) {
            try { ids.add(Integer.parseInt(m.group(1).substring(1))); } catch (NumberFormatException ignored) {}
        }
        List<Evidence> out = new ArrayList<>();
        for (int id : ids) if (id >= 1 && id <= evidence.size()) out.add(evidence.get(id - 1));
        return out;
    }
}
