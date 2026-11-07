# ProofSum — proof-based research paper summarizer (Java / Spring Boot)

Upload a research paper (PDF) → text is extracted page-by-page with Apache PDFBox → cleaned, section-detected and chunked → embedded into a vector store → for each question the top-k passages are retrieved → the LLM answers **only** from those passages and cites `[E1]`, `[E2]`… → the frontend shows the answer with the supporting excerpts, page number and section. If no evidence is found, the API returns "Not found".

## Run

```bash
# Works fully offline (local embeddings + extractive answers)
mvn spring-boot:run
# open http://localhost:8080

# With an LLM (either)
export ANTHROPIC_API_KEY=sk-ant-...   ; export LLM_PROVIDER=anthropic
export OPENAI_API_KEY=sk-...          ; export LLM_PROVIDER=openai
mvn spring-boot:run
```

Set `proofsum.embedding.provider=openai` in `application.properties` to use real dense embeddings (needs `OPENAI_API_KEY`).

## API

| Method | Path | Body / Result |
|---|---|---|
| POST | `/api/papers` | multipart `file` → `{documentId, pages, chunks, sections}` |
| POST | `/api/papers/ask` | `{documentId, question}` → `{answer, found, evidence[{page, section, excerpt, score}], model}` |
| GET  | `/api/papers/{id}/summary` | six fixed research questions (problem, method, data, results, limitations, contribution), each with evidence |
| GET  | `/api/papers/{id}` | document info |
| DELETE | `/api/papers/{id}` | remove from the store |

## Structure

```
src/main/java/com/proofsum
├── ProofSumApplication.java
├── controller/PaperController.java        REST endpoints
├── controller/ApiExceptionHandler.java    JSON error responses
├── service/PdfProcessingService.java      validate PDF, temp storage, PDFBox extraction per page
├── service/ChunkingService.java           clean text, detect sections, overlapping chunks + metadata
├── service/EmbeddingService.java          local hashed vectors or OpenAI-compatible embeddings
├── service/RetrievalService.java          index chunks, top-k similarity search
├── service/LlmService.java                grounded prompt, Anthropic/OpenAI call, citation filtering, "Not found"
├── service/SummaryService.java            evidence-based summary over fixed aspects
├── store/VectorStore.java                 interface – swap in pgvector / Qdrant / Chroma / Pinecone
├── store/InMemoryVectorStore.java         default cosine-similarity store
└── model/*.java                           records for chunks, evidence, requests, responses
src/main/resources/static/index.html      frontend (HTML/CSS/JS)
```

## Swapping the vector database

Implement `VectorStore` (four methods) and mark it `@Primary`. Everything else stays unchanged.
