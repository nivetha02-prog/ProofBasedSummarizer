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
 * Generate answers grounded in retrieved document evidence, using a configured language model
 * when available and an extractive response when no model is configured or a call fails.
 */
@Service
public class LlmService {

    /** Answer text used when evidence does not support a response. */
    public static final String NOT_FOUND = "Not found in the provided document.";
    /** Recognize evidence identifiers in the form used by the generated prompt. */
    private static final Pattern CITATION = Pattern.compile("\\[(E\\d+)\\]");

    /** Effective provider name after considering configured API keys. */
    private final String provider;
    /** Anthropic endpoint, model, and credential. */
    private final String anthropicUrl, anthropicModel, anthropicKey;
    /** OpenAI-compatible endpoint, model, and credential. */
    private final String openaiUrl, openaiModel, openaiKey;
    /** HTTP client shared by remote model calls. */
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    /** JSON mapper used for remote request and response payloads. */
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Configure the selected language-model provider and its endpoint credentials.
     *
     * @param provider requested provider name ({@code anthropic}, {@code openai}, or another value)
     * @param anthropicUrl Anthropic messages endpoint
     * @param anthropicModel Anthropic model name
     * @param anthropicKey Anthropic API key; a blank key disables that provider
     * @param openaiUrl OpenAI-compatible chat-completions endpoint
     * @param openaiModel OpenAI-compatible model name
     * @param openaiKey OpenAI-compatible API key; a blank key disables that provider
     */
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

    /**
     * Answer a question from retrieved chunks and attach the evidence associated with the response.
     *
     * @param documentId identifier of the source document
     * @param question question to answer
     * @param retrieved retrieved chunks supplied as the sole grounding evidence
     * @return answer, found status, evidence excerpts, and the model or fallback identifier
     */
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

    /**
     * Build the user prompt containing the question and numbered evidence passages.
     *
     * @param question question to include in the prompt
     * @param evidence evidence passages numbered for citation
     * @return formatted prompt directing the model to answer using only the supplied evidence
     */
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

    /**
     * Send a prompt to Anthropic and extract text blocks from the response.
     *
     * @param prompt prompt sent as the user message
     * @return concatenated response text, trimmed at both ends
     * @throws IOException if the HTTP request fails, the response is unsuccessful, or JSON encoding fails
     * @throws InterruptedException if the HTTP request is interrupted
     */
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

    /**
     * Send a prompt to an OpenAI-compatible chat-completions endpoint.
     *
     * @param prompt prompt sent as the user message
     * @return first choice's message content, trimmed at both ends
     * @throws IOException if the HTTP request fails, the response is unsuccessful, or JSON encoding fails
     * @throws InterruptedException if the HTTP request is interrupted
     */
    private String callOpenAi(String prompt) throws IOException, InterruptedException {
        Map<String, Object> body = Map.of(
                "model", openaiModel,
                "temperature", 0,
                "max_tokens", 1024,
                "messages", List.of(Map.of("role", "user", "content", prompt)));
        HttpRequest req = HttpRequest.newBuilder(URI.create(openaiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + openaiKey)
                .timeout(Duration.ofSeconds(180))
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
        JsonNode json = send(req);
        return json.get("choices").get(0).get("message").get("content").asText().trim();
    }

    /**
     * Execute a prepared request and parse its successful response body as JSON.
     *
     * @param req HTTP request to send
     * @return parsed JSON response
     * @throws IOException if the request fails, the response status is not successful, or JSON parsing fails
     * @throws InterruptedException if the request is interrupted
     */
    private JsonNode send(HttpRequest req) throws IOException, InterruptedException {
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) throw new IOException("HTTP " + res.statusCode() + ": " + res.body());
        return mapper.readTree(res.body());
    }

    /**
     * Select question-relevant sentences from evidence and tag each sentence with its evidence identifier.
     *
     * @param question question terms used to rank candidate sentences
     * @param evidence source passages to search
     * @return up to four matching sentences with citation tags, or {@link #NOT_FOUND} if none match
     */
    String extractiveAnswer(String question, List<Evidence> evidence) {
        java.util.Set<String> qTerms = new java.util.HashSet<>();
        for (String w : question.toLowerCase().split("[^a-z0-9]+")) if (w.length() > 3) qTerms.add(w);

        /**
         * Hold a candidate sentence, its one-based evidence identifier, and query-term match count.
         *
         * @param text candidate sentence text
         * @param idx one-based evidence identifier used in the citation
         * @param hits number of distinct query terms found in the sentence
         */
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

    /**
     * Convert retrieved chunks into evidence records, rounding their scores to three decimals.
     *
     * @param retrieved chunks returned by retrieval
     * @return evidence entries in the same order as the supplied chunks
     */
    private List<Evidence> toEvidence(List<ScoredChunk> retrieved) {
        List<Evidence> out = new ArrayList<>();
        for (ScoredChunk sc : retrieved) {
            out.add(new Evidence(sc.chunk().id(), sc.chunk().pageNumber(), sc.chunk().section(),
                    sc.chunk().text(), Math.round(sc.score() * 1000.0) / 1000.0));
        }
        return out;
    }

    /**
     * Keep evidence entries referenced by valid numbered citation tags in an answer.
     *
     * @param answer answer text to inspect
     * @param evidence evidence list addressed by one-based citation numbers
     * @return cited evidence in first-citation order, with invalid or out-of-range identifiers omitted
     */
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
