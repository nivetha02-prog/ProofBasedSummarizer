package com.proofsum.service;

import com.proofsum.model.Chunk;
import com.proofsum.model.PageText;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Clean extracted page text and split it into overlapping word-window chunks.
 * Each generated chunk retains its source page and the active recognized section.
 */
@Service
public class ChunkingService {

    /** Recognize supported research-paper section titles, optionally preceded by a number. */
    private static final Pattern SECTION_HEADING = Pattern.compile(
            "^\\s*(?:(?:\\d+(?:\\.\\d+)*|[IVX]+)\\.?\\s+)?" +
            "(abstract|introduction|background|related work|literature review|" +
            "methods?|methodology|materials and methods|experimental setup|experiments?|" +
            "results?|results and discussion|discussion|analysis|evaluation|" +
            "conclusions?|conclusion and future work|future work|limitations|" +
            "acknowledg(?:e)?ments?|references|bibliography|appendix(?:\\s+[a-z])?)\\s*$",
            Pattern.CASE_INSENSITIVE);

    /** Rejoin words separated by a hyphen at a line break. */
    private static final Pattern HYPHEN_BREAK = Pattern.compile("(\\w)-\\n(\\w)");
    /** Collapse repeated horizontal whitespace. */
    private static final Pattern MULTI_SPACE = Pattern.compile("[ \\t]+");
    /** Limit runs of blank lines while retaining paragraph separation. */
    private static final Pattern MULTI_NEWLINE = Pattern.compile("\\n{3,}");

    /** Maximum number of words in a generated chunk. */
    private final int chunkWords;
    /** Number of trailing words repeated at the start of the next chunk. */
    private final int overlapWords;

    /**
     * Configure chunk word count and overlap.
     *
     * @param chunkWords maximum number of words in each chunk
     * @param overlapWords requested number of overlapping words, capped at half the chunk size
     */
    public ChunkingService(@Value("${proofsum.chunk.size-words}") int chunkWords,
                           @Value("${proofsum.chunk.overlap-words}") int overlapWords) {
        this.chunkWords = chunkWords;
        this.overlapWords = Math.min(overlapWords, chunkWords / 2);
    }

    /**
     * Clean and split pages into chunks while retaining page, section, and ordinal metadata.
     *
     * @param documentId identifier included in each generated chunk
     * @param pages extracted page text in document order
     * @return chunks generated from non-blank page content; empty when no usable page text exists
     */
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

    /**
     * Split buffered text into word-window chunks and append them to the output.
     *
     * @param out destination list receiving generated chunks
     * @param buffer lines of content accumulated for the current section
     * @param docId identifier used to build chunk identifiers
     * @param page source page number for the buffered content
     * @param section active section name for the buffered content
     * @param ordinal ordinal assigned to the first generated chunk
     * @return ordinal to assign to the next chunk
     */
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

    /**
     * Determine whether a line matches one of the supported section headings.
     *
     * @param line trimmed candidate heading
     * @return {@code true} if the line is at most 60 characters and matches a recognized heading
     */
    private boolean isHeading(String line) {
        return line.length() <= 60 && SECTION_HEADING.matcher(line).matches();
    }

    /**
     * Remove an optional numeric or Roman-numeral prefix and normalize a heading's capitalization.
     *
     * @param line recognized heading line
     * @return normalized section name
     */
    private String normalizeHeading(String line) {
        String h = line.replaceFirst("^\\s*(?:\\d+(?:\\.\\d+)*|[IVX]+)\\.?\\s+", "").trim();
        return Character.toUpperCase(h.charAt(0)) + h.substring(1).toLowerCase();
    }

    /**
     * Normalize line endings, broken words, control characters, ligatures, and repeated whitespace.
     *
     * @param raw extracted text; {@code null} is treated as empty text
     * @return cleaned text with leading and trailing whitespace removed
     */
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
