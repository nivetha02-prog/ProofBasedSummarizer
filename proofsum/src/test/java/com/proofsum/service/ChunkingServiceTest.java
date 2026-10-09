package com.proofsum.service;

import com.proofsum.model.Chunk;
import com.proofsum.model.PageText;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChunkingServiceTest {

    @Test
    void chunksTextWithOverlapAndTracksSectionsAndPages() {
        ChunkingService service = new ChunkingService(3, 1);

        List<Chunk> chunks = service.chunk("12345678-abcd", List.of(
                new PageText(1, "Abstract\nalpha beta gamma delta epsilon\n2. Methods\none two three"),
                new PageText(2, "four five")
        ));

        assertEquals(4, chunks.size());
        assertEquals(new Chunk("12345678-p1-c0", "12345678-abcd", 1, "Abstract", 0,
                "alpha beta gamma"), chunks.get(0));
        assertEquals(new Chunk("12345678-p1-c1", "12345678-abcd", 1, "Abstract", 1,
                "gamma delta epsilon"), chunks.get(1));
        assertEquals(new Chunk("12345678-p1-c2", "12345678-abcd", 1, "Methods", 2,
                "one two three"), chunks.get(2));
        assertEquals(new Chunk("12345678-p2-c3", "12345678-abcd", 2, "Methods", 3,
                "four five"), chunks.get(3));
    }

    @Test
    void cleansHyphenatedWordsAndSkipsBlankPages() {
        ChunkingService service = new ChunkingService(20, 0);

        List<Chunk> chunks = service.chunk("12345678-abcd", List.of(
                new PageText(1, " \n "),
                new PageText(2, "inter-\nnational research")
        ));

        assertEquals(1, chunks.size());
        assertEquals(2, chunks.get(0).pageNumber());
        assertEquals("international research", chunks.get(0).text());
    }
}
