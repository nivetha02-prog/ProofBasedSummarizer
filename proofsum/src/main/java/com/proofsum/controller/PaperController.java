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
 * Expose endpoints for uploading research papers, asking document questions,
 * retrieving summaries and metadata, and deleting documents.
 */
@RestController
@RequestMapping("/api/papers")
@CrossOrigin
public class PaperController {

    /** Validate, temporarily store, and extract text from uploaded PDFs. */
    private final PdfProcessingService pdf;
    /** Split extracted page text into evidence-bearing chunks. */
    private final ChunkingService chunking;
    /** Index document chunks and retrieve relevant passages. */
    private final RetrievalService retrieval;
    /** Generate answers from retrieved evidence. */
    private final LlmService llm;
    /** Generate fixed-aspect summaries. */
    private final SummaryService summary;
    /** Store document vectors and registered metadata. */
    private final VectorStore store;

    /**
     * Create the paper API controller with its processing and storage collaborators.
     *
     * @param pdf service for uploaded PDF processing
     * @param chunking service for converting page text into chunks
     * @param retrieval service for indexing and searching chunks
     * @param llm service for evidence-grounded answers
     * @param summary service for fixed-aspect summaries
     * @param store vector store holding document metadata and chunks
     */
    public PaperController(PdfProcessingService pdf, ChunkingService chunking, RetrievalService retrieval,
                           LlmService llm, SummaryService summary, VectorStore store) {
        this.pdf = pdf; this.chunking = chunking; this.retrieval = retrieval;
        this.llm = llm; this.summary = summary; this.store = store;
    }

    /**
     * Accept and process a PDF upload, index its chunks, and register document metadata.
     *
     * @param file multipart file supplied in the {@code file} request part
     * @return assigned document identifier, original file name, page and chunk counts, and sections
     * @throws IOException if the upload cannot be stored or the PDF cannot be read
     * @throws IllegalArgumentException if the PDF is invalid or yields no usable chunks
     */
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

    /**
     * Answer a question about an already registered document.
     *
     * @param req request containing the document identifier and non-blank question
     * @return answer and its associated evidence
     * @throws DocumentNotFoundException if the requested document is not registered
     */
    @PostMapping("/ask")
    public AnswerResponse ask(@Valid @RequestBody QuestionRequest req) {
        requireDocument(req.documentId());
        List<ScoredChunk> hits = retrieval.retrieve(req.documentId(), req.question());
        return llm.answer(req.documentId(), req.question(), hits);
    }

    /**
     * Generate the configured summary aspects for a registered document.
     *
     * @param id identifier of the document to summarize
     * @return mapping from summary aspect names to answers
     * @throws DocumentNotFoundException if the requested document is not registered
     */
    @GetMapping("/{id}/summary")
    public Map<String, AnswerResponse> summarize(@PathVariable String id) {
        requireDocument(id);
        return summary.summarize(id);
    }

    /**
     * Return metadata for a registered document.
     *
     * @param id identifier of the document to inspect
     * @return registered document metadata
     * @throws DocumentNotFoundException if the requested document is not registered
     */
    @GetMapping("/{id}")
    public VectorStore.DocumentInfo info(@PathVariable String id) {
        return requireDocument(id);
    }

    /**
     * Delete a document's vectors and metadata.
     *
     * @param id identifier of the document to delete
     * @return an empty {@code 204 No Content} response
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        store.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Resolve registered metadata or raise the controller's not-found exception.
     *
     * @param id identifier of the document to resolve
     * @return registered metadata
     * @throws DocumentNotFoundException if no metadata is registered for the identifier
     */
    private VectorStore.DocumentInfo requireDocument(String id) {
        return store.document(id).orElseThrow(() ->
                new DocumentNotFoundException("No document with id " + id + ". Upload a PDF first."));
    }

    /**
     * Signal that an API operation requires a document that is not registered.
     */
    public static class DocumentNotFoundException extends RuntimeException {

        /**
         * Create a document-not-found exception with a descriptive message.
         *
         * @param m detail explaining which document was not found
         */
        public DocumentNotFoundException(String m) { super(m); }
    }
}
