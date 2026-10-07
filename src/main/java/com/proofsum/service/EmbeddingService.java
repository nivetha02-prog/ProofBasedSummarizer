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
 * Turns text into dense vectors. Two providers:
 *  - "local": offline hashed term-frequency vectors (no API key, decent lexical retrieval)
 *  - "openai": any OpenAI-compatible /v1/embeddings endpoint
 */
@Service
public class EmbeddingService {

    private static final int LOCAL_DIM = 4096;
    private static final Pattern TOKEN = Pattern.compile("[a-z0-9]+");

    private final String provider;
    private final String openaiUrl;
    private final String openaiModel;
    private final String openaiKey;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public EmbeddingService(@Value("${proofsum.embedding.provider}") String provider,
                            @Value("${proofsum.embedding.openai.url}") String openaiUrl,
                            @Value("${proofsum.embedding.openai.model}") String openaiModel,
                            @Value("${proofsum.embedding.openai.api-key}") String openaiKey) {
        this.provider = (openaiKey == null || openaiKey.isBlank()) ? "local" : provider;
        this.openaiUrl = openaiUrl;
        this.openaiModel = openaiModel;
        this.openaiKey = openaiKey;
    }

    public String provider() { return provider; }

    public float[] embed(String text) {
        return embedAll(List.of(text)).get(0);
    }

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

    /* ---------- local hashed TF vector with bigrams and sub-linear scaling ---------- */

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

    private static int bucket(String s) {
        return Math.floorMod(s.hashCode() * 31 + 7, LOCAL_DIM);
    }

    /** Very small suffix stemmer: enough to match "models" with "model". */
    private static String stem(String w) {
        if (w.length() > 5 && w.endsWith("ing")) return w.substring(0, w.length() - 3);
        if (w.length() > 4 && w.endsWith("ies")) return w.substring(0, w.length() - 3) + "y";
        if (w.length() > 4 && w.endsWith("es"))  return w.substring(0, w.length() - 2);
        if (w.length() > 3 && w.endsWith("s") && !w.endsWith("ss")) return w.substring(0, w.length() - 1);
        return w;
    }

    private static final java.util.Set<String> STOP = java.util.Set.of(
            "the","a","an","and","or","of","to","in","on","for","with","is","are","was","were","be","been",
            "by","as","at","that","this","these","those","it","its","from","we","our","which","can","has",
            "have","had","not","but","also","than","their","there","into","such","using","used","use",
            "what","how","does","do","did","why","when","where","who","paper","study","show","shows");

    /* ---------- remote OpenAI-compatible embeddings ---------- */

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
