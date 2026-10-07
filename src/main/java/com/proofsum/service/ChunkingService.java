package com.proofsum.service;

import com.proofsum.model.Chunk;
import com.proofsum.model.PageText;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Cleans extracted text, detects section headings and splits pages into
 * overlapping word-window chunks that preserve page number and section metadata.
 */
@Service
public class ChunkingService {

    private static final Pattern SECTION_HEADING = Pattern.compile(
            "^\\s*(?:(?:\\d+(?:\\.\\d+)*|[IVX]+)\\.?\\s+)?" +
            "(abstract|introduction|background|related work|literature review|" +
            "methods?|methodology|materials and methods|experimental setup|experiments?|" +
            "results?|results and discussion|discussion|analysis|evaluation|" +
            "conclusions?|conclusion and future work|future work|limitations|" +
            "acknowledg(?:e)?ments?|references|bibliography|appendix(?:\\s+[a-z])?)\\s*$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern HYPHEN_BREAK = Pattern.compile("(\\w)-\\n(\\w)");
    private static final Pattern MULTI_SPACE = Pattern.compile("[ \\t]+");
    private static final Pattern MULTI_NEWLINE = Pattern.compile("\\n{3,}");

    private final int chunkWords;
    private final int overlapWords;

    public ChunkingService(@Value("${proofsum.chunk.size-words}") int chunkWords,
                           @Value("${proofsum.chunk.overlap-words}") int overlapWords) {
        this.chunkWords = chunkWords;
        this.overlapWords = Math.min(overlapWords, chunkWords / 2);
    }

    public List<Chunk> chunk(String documentId, List<PageText> pages) {
        List<Chunk> chunks = new ArrayList<>();
        String currentSection = "Front matter";
        int ordinal = 0;

        for (PageText page : pages) {
            String cleaned = clean(page.text());
            if (cleaned.isBlank()) continue;

            // Walk the page line by line so a heading switches the section
            // for everything that follows it, even mid-page.
            List<String> buffer = new ArrayList<>();
            for (String line : cleaned.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                if (isHeading(trimmed)) {
                    ordinal = flush(chunks, buffer, documentId, page.pageNumber(), currentSection, ordinal);
                    currentSection = normalizeHeading(trimmed);
                    continue;
                }
                buffer.add(trimmed);
            }
            ordinal = flush(chunks, buffer, documentId, page.pageNumber(), currentSection, ordinal);
        }
        return chunks;
    }

    private int flush(List<Chunk> out, List<String> buffer, String docId, int page, String section, int ordinal) {
        if (buffer.isEmpty()) return ordinal;
        String[] words = String.join(" ", buffer).split("\\s+");
        buffer.clear();

        int start = 0;
        while (start < words.length) {
            int end = Math.min(words.length, start + chunkWords);
            String text = String.join(" ", java.util.Arrays.copyOfRange(words, start, end));
            String id = docId.substring(0, 8) + "-p" + page + "-c" + ordinal;
            out.add(new Chunk(id, docId, page, section, ordinal, text));
            ordinal++;
            if (end == words.length) break;
            start = end - overlapWords;
        }
        return ordinal;
    }

    private boolean isHeading(String line) {
        return line.length() <= 60 && SECTION_HEADING.matcher(line).matches();
    }

    private String normalizeHeading(String line) {
        String h = line.replaceFirst("^\\s*(?:\\d+(?:\\.\\d+)*|[IVX]+)\\.?\\s+", "").trim();
        return Character.toUpperCase(h.charAt(0)) + h.substring(1).toLowerCase();
    }

    String clean(String raw) {
        if (raw == null) return "";
        String t = raw.replace("\r", "");
        t = HYPHEN_BREAK.matcher(t).replaceAll("$1$2");      // re-join hyphenated line breaks
        t = t.replaceAll("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]", "");
        t = t.replaceAll("\\uFB01", "fi").replaceAll("\\uFB02", "fl");
        t = MULTI_SPACE.matcher(t).replaceAll(" ");
        t = MULTI_NEWLINE.matcher(t).replaceAll("\n\n");
        return t.trim();
    }
}
