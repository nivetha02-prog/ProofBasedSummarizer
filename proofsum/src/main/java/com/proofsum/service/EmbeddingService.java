package com.proofsum.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Convert text to vectors using local hashed term-frequency features or a configured
 * OpenAI-compatible embeddings endpoint.
 */
@Service
public class EmbeddingService {

    /** Number of dimensions used by the local hashed-vector representation. */
    private static final int LOCAL_DIM = 4096;
    /** Token pattern used by the local text representation. */
    private static final Pattern TOKEN = Pattern.compile("[a-z0-9]+");

    /** Selected embedding provider; a missing API key forces local mode. */
    private final String provider;

    /** Configured remote embeddings endpoint. */
    private final String openaiUrl;
    /** Model name supplied to the remote endpoint. */
    private final String openaiModel;
    /** Credential used for remote requests, if configured. */
    private final String openaiKey;
    /** HTTP client used for remote embedding requests. */
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    /** JSON mapper used to encode requests and decode responses. */
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Configure local or OpenAI-compatible embeddings.
     *
     * @param provider requested provider name
     * @param openaiUrl remote embeddings endpoint
     * @param openaiModel model name sent to the remote endpoint
     * @param openaiKey remote API key; a null or blank value selects the local provider
     */
    public EmbeddingService(@Value("${proofsum.embedding.provider}") String provider,
                            @Value("${proofsum.embedding.openai.url}") String openaiUrl,
                            @Value("${proofsum.embedding.openai.model}") String openaiModel,
                            @Value("${proofsum.embedding.openai.api-key}") String openaiKey) {
        this.provider = (openaiKey == null || openaiKey.isBlank()) ? "local" : provider;
        this.openaiUrl = openaiUrl;
        this.openaiModel = openaiModel;
        this.openaiKey = openaiKey;
    }

    /**
     * Return the effective provider selected by this service.
     *
     * @return provider name, including {@code local} when no remote key is configured
     */
    public String provider() { return provider; }

    /**
     * Embed one text value using the configured provider and fallback behavior.
     *
     * @param text text to convert to a vector
     * @return vector corresponding to the text
     * @throws NullPointerException if {@code text} is null
     */
    public float[] embed(String text) {
        return embedAll(List.of(text)).get(0);
    }

    /**
     * Embed a batch of texts, falling back to local vectors if a remote request fails.
     *
     * @param texts texts to convert, in output order
     * @return one vector per input text, in the same order
     */
    public List<float[]> embedAll(List<String> texts) {
        if ("openai".equalsIgnoreCase(provider)) {
            try {
                return embedRemote(texts);
            } catch (Exception e) {
                // Fall back so the app keeps working if the API is down.
                System.err.println("[EmbeddingService] remote failed, using local: " + e.getMessage());
            }
        }
        List<float[]> out = new ArrayList<>(texts.size());
        for (String t : texts) out.add(embedLocal(t));
        return out;
    }

    /**
     * Build a normalized local vector from hashed tokens and adjacent-token features.
     *
     * @param text text to tokenize and represent
     * @return normalized vector of {@value #LOCAL_DIM} dimensions, or a zero vector when no terms remain
     */
    private float[] embedLocal(String text) {
        float[] v = new float[LOCAL_DIM];
        Matcher m = TOKEN.matcher(text.toLowerCase());
        String prev = null;
        while (m.find()) {
            String tok = m.group();
            if (tok.length() < 2 || STOP.contains(tok)) { prev = null; continue; }
            tok = stem(tok);
            v[bucket(tok)] += 1f;
            if (prev != null) v[bucket(prev + "_" + tok)] += 0.6f;
            prev = tok;
        }
        double norm = 0;
        for (int i = 0; i < LOCAL_DIM; i++) {
            if (v[i] > 0) v[i] = (float) (1 + Math.log(v[i]));
            norm += v[i] * v[i];
        }
        norm = Math.sqrt(norm);
        if (norm > 0) for (int i = 0; i < LOCAL_DIM; i++) v[i] /= norm;
        return v;
    }

    /**
     * Map a token or token pair deterministically into the local vector dimensions.
     *
     * @param s token or token-pair feature to map
     * @return vector index in the range from zero to {@code LOCAL_DIM - 1}
     */
    private static int bucket(String s) {
        return Math.floorMod(s.hashCode() * 31 + 7, LOCAL_DIM);
    }

    /**
     * Remove one supported suffix to improve simple lexical matching.
     *
     * @param w lowercase token to stem
     * @return token with a recognized suffix removed, or the original token
     */
    private static String stem(String w) {
        if (w.length() > 5 && w.endsWith("ing")) return w.substring(0, w.length() - 3);
        if (w.length() > 4 && w.endsWith("ies")) return w.substring(0, w.length() - 3) + "y";
        if (w.length() > 4 && w.endsWith("es"))  return w.substring(0, w.length() - 2);
        if (w.length() > 3 && w.endsWith("s") && !w.endsWith("ss")) return w.substring(0, w.length() - 1);
        return w;
    }

    /** Terms excluded from local vector features. */
    private static final java.util.Set<String> STOP = java.util.Set.of(
            "the","a","an","and","or","of","to","in","on","for","with","is","are","was","were","be","been",
            "by","as","at","that","this","these","those","it","its","from","we","our","which","can","has",
            "have","had","not","but","also","than","their","there","into","such","using","used","use",
            "what","how","does","do","did","why","when","where","who","paper","study","show","shows");

    /**
     * Request vectors from the configured OpenAI-compatible endpoint.
     *
     * @param texts input texts in request order
     * @return vectors parsed from the endpoint response
     * @throws IOException if request serialization, transport, response status, or response parsing fails
     * @throws InterruptedException if the request is interrupted
     */
    private List<float[]> embedRemote(List<String> texts) throws IOException, InterruptedException {
        String body = mapper.writeValueAsString(Map.of("model", openaiModel, "input", texts));
        HttpRequest req = HttpRequest.newBuilder(URI.create(openaiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + openaiKey)
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) throw new IOException("HTTP " + res.statusCode() + ": " + res.body());

        JsonNode data = mapper.readTree(res.body()).get("data");
        List<float[]> out = new ArrayList<>();
        for (JsonNode item : data) {
            JsonNode arr = item.get("embedding");
            float[] v = new float[arr.size()];
            for (int i = 0; i < arr.size(); i++) v[i] = (float) arr.get(i).asDouble();
            out.add(v);
        }
        return out;
    }
}
