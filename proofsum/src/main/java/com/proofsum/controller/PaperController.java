package com.proofsum.controller;

import com.proofsum.model.*;
import com.proofsum.service.*;
import com.proofsum.store.VectorStore;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * REST API consumed by the frontend.
 *
 *  POST /api/papers                 multipart "file"  -> UploadResponse
 *  POST /api/papers/ask             {documentId, question} -> AnswerResponse
 *  GET  /api/papers/{id}/summary    -> Map<aspect, AnswerResponse>
 *  GET  /api/papers/{id}            -> document info
 *  DELETE /api/papers/{id}
 */
@RestController
@RequestMapping("/api/papers")
@CrossOrigin
public class PaperController {

    private final PdfProcessingService pdf;
    private final ChunkingService chunking;
    private final RetrievalService retrieval;
    private final LlmService llm;
    private final SummaryService summary;
    private final VectorStore store;

    public PaperController(PdfProcessingService pdf, ChunkingService chunking, RetrievalService retrieval,
                           LlmService llm, SummaryService summary, VectorStore store) {
        this.pdf = pdf; this.chunking = chunking; this.retrieval = retrieval;
        this.llm = llm; this.summary = summary; this.store = store;
    }

    @PostMapping
    public UploadResponse upload(@RequestParam("file") MultipartFile file) throws IOException {
        String documentId = PdfProcessingService.newDocumentId();
        Path stored = pdf.store(file, documentId);
        try {
            List<PageText> pages = pdf.extractPages(stored);
            List<Chunk> chunks = chunking.chunk(documentId, pages);
            if (chunks.isEmpty()) throw new IllegalArgumentException("No usable text could be chunked from this PDF.");

            retrieval.index(documentId, chunks);
            List<String> sections = chunks.stream().map(Chunk::section).distinct().toList();
            store.registerDocument(new VectorStore.DocumentInfo(documentId, file.getOriginalFilename(), pages.size(), sections));

            return new UploadResponse(documentId, file.getOriginalFilename(), pages.size(), chunks.size(), sections);
        } finally {
            pdf.delete(stored);   // temporary storage only; the chunks live in the vector store
        }
    }

    @PostMapping("/ask")
    public AnswerResponse ask(@Valid @RequestBody QuestionRequest req) {
        requireDocument(req.documentId());
        List<ScoredChunk> hits = retrieval.retrieve(req.documentId(), req.question());
        return llm.answer(req.documentId(), req.question(), hits);
    }

    @GetMapping("/{id}/summary")
    public Map<String, AnswerResponse> summarize(@PathVariable String id) {
        requireDocument(id);
        return summary.summarize(id);
    }

    @GetMapping("/{id}")
    public VectorStore.DocumentInfo info(@PathVariable String id) {
        return requireDocument(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        store.delete(id);
        return ResponseEntity.noContent().build();
    }

    private VectorStore.DocumentInfo requireDocument(String id) {
        return store.document(id).orElseThrow(() ->
                new DocumentNotFoundException("No document with id " + id + ". Upload a PDF first."));
    }

    public static class DocumentNotFoundException extends RuntimeException {
        public DocumentNotFoundException(String m) { super(m); }
    }
}
