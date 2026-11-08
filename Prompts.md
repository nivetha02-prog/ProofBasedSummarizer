# PROMPTS
## Prompt 1 — Inspect the existing Python implementation and Java project

Act as a senior software engineer helping translate an existing Python application into Java. I already have the Python ProofSum implementation and a Java/Spring Boot project.

Before changing any code, inspect both codebases. For the Python project, identify the application entry point, modules, functions/classes, dependencies, configuration, input/output contracts, and important processing steps. For the Java project, inspect the directory structure, `pom.xml`, Java version, Spring Boot version, packages, classes, resources, and tests.

Create a mapping table from each Python module/function to its Java counterpart. Identify implemented, partially implemented, missing, and behaviorally different areas. Use only evidence from the source files; do not invent components. Return the findings and proposed conversion plan first. Do not modify files yet.

## Prompt 2 — Plan the Python-to-Java architecture and conversion order

Using the source-code mapping from the previous analysis, propose a maintainable Java architecture that preserves the Python application's behavior while fitting the existing Maven/Spring Boot project.

Consider the actual ProofSum responsibilities: PDF processing, page-level extracted text, cleaning and section detection, chunking with metadata, embedding generation, vector storage, similarity retrieval, LLM prompting and provider calls, evidence/citation handling, summary generation, REST API, DTOs/models, and exception responses. Map responsibilities to existing packages such as `controller`, `service`, `store`, and `model` where appropriate.

Recommend a dependency-aware conversion order. Reuse existing Java classes where they already implement the required behavior. Do not perform broad refactoring or add unnecessary frameworks.

## Prompt 3 — Convert PDF ingestion and page extraction

Compare the Python PDF ingestion/extraction logic with the Java `PdfProcessingService` implementation. Translate any missing behavior into Java using the project's existing Apache PDFBox dependency and current conventions.

Preserve supported file validation, temporary-file handling, page-by-page text extraction, page numbering, and error behavior as established by the Python source. Keep extracted page content and metadata compatible with downstream chunking. Do not change API contracts unless the Python source demonstrates a required difference.

Add or update focused tests for valid PDFs, invalid/non-PDF inputs, empty or textless documents, and extraction errors where testable. Explain any Python-library behavior that has no direct Java equivalent and how it was handled.

## Prompt 4 — Convert text cleaning, section detection, and chunking

Compare the Python text-preprocessing and chunking module against Java `ChunkingService`. Implement the equivalent cleaning rules, section detection, chunk boundaries, overlap behavior, and metadata retention based on the actual Python code and configuration.

Check edge cases such as blank text, short sections, long paragraphs, page boundaries, and overlapping chunks. Preserve source page and section metadata so that evidence can later be cited. Avoid changing chunk size or overlap defaults without documenting the reason.

Add/update JUnit tests for normal and boundary cases. Summarize the mapping between the Python functions and Java methods.

## Prompt 5 — Convert embeddings and vector-store behavior

Translate the Python embedding generation and vector-store logic into the existing Java `EmbeddingService`, `VectorStore` interface, and `InMemoryVectorStore` implementation.

Preserve the supported embedding-provider behavior, vector dimensions, normalization assumptions, document association, similarity metric, and result ordering as evidenced by the Python source. Keep the vector-store abstraction intact so alternate stores can be introduced without changing retrieval or controller logic.

Inspect handling of empty vectors, incompatible dimensions, duplicate or missing document IDs, and store removal. Add focused JUnit tests and note any behavior that differs from Python due to library/API differences.


## Prompt 6 — Convert LLM prompting, grounded answers, and evidence citations

Compare the Python LLM/prompting code with Java `LlmService`. Implement equivalent supported provider interactions and prompt construction using the existing project structure.

The answer must be grounded in retrieved passages rather than unsupported outside knowledge. Preserve the evidence-label format (such as `[E1]`, `[E2]`), citation filtering/validation, and the “Not found” behavior when the retrieved evidence does not answer the question, as applicable in the source. Maintain configuration for supported LLM providers and avoid exposing API keys in code or logs.

Handle provider errors, malformed responses, missing credentials, and empty context. Add tests using mocks or controlled responses; do not require live API calls for ordinary unit tests. Explain any prompt changes and why they preserve the intended behavior.

## Prompt 7 — Convert evidence-based summary generation

Compare the Python summary-generation workflow with Java `SummaryService`. Preserve the summary aspects supported by the application (problem, method, data, results, limitations, and contribution) and the requirement that each response be backed by retrieved evidence.

Reuse existing retrieval and LLM services instead of duplicating logic. Keep response structure and evidence metadata consistent with the Java models. Ensure that an aspect with insufficient supporting evidence is represented according to the existing “Not found” convention rather than fabricated.

Add/update tests for the expected summary aspects, evidence propagation, and missing-evidence cases.

## Prompt 8 — Convert API endpoints, models, and exception handling

Compare the Python application's exposed endpoints and request/response schemas with Java `PaperController`, the model classes under `com.proofsum.model`, and `ApiExceptionHandler`.

Preserve the application operations supported by the Java project: paper upload, question answering, fixed-aspect summary retrieval, document information lookup, and document deletion. Verify HTTP methods, route paths, multipart field names, JSON properties, status codes, validation behavior, and error response structure against the Python source and project README.

Do not rename or change public API contracts without a clear compatibility reason. Add controller/service tests for success and failure cases where practical. List any differences between the Python and Java APIs.

## Prompt 9 — Verify end-to-end behavior and parity with Python

Perform a systematic behavior comparison between the supplied Python implementation and the Java project. Trace a paper through the full pipeline: upload → PDF extraction → cleaning/section detection → chunking → embeddings/indexing → retrieval → evidence-grounded answer or summary → API response.

Use the same sample inputs and equivalent configuration wherever possible. Compare extracted pages, chunk counts and metadata, retrieval results/ranking, answer structure, evidence references, and error cases. Separate expected implementation/library differences from genuine defects.

Fix only verified parity issues. Report which checks were performed, what matched, what differed, and any remaining limitations. Do not claim full parity without evidence from tests or comparison runs.

## Prompt 10 — Generate and review Javadoc

Act as a senior Java developer responsible for accurate documentation. Inspect all source files under `src/main/java` and add or improve JavaDoc for classes, interfaces, records, constructors, methods, and relevant fields.

Document each declaration based on its actual behavior. Include meaningful `@param`, `@return`, and `@throws` tags where appropriate. Pay special attention to controllers, services, model/DTO classes, vector-store contracts, configuration, and exception handling. Do not invent constraints or guarantees; preserve method signatures, annotations, imports, and implementation logic.

Review the changes to ensure they contain documentation-only edits. Generate JavaDoc and compile/run tests if configured. Report documented files, unresolved TODOs, and validation results.

## Prompt 12 — Run JUnit tests and improve test coverage

Inspect the Maven project and its current JUnit 5 tests. Maintain and extend tests for `ChunkingService`, `EmbeddingService`, `RetrievalService`, and `InMemoryVectorStore`, and add tests for other components when practical.

Focus on observable behavior, edge cases, error handling, and Python-to-Java parity. Use mocks or deterministic local implementations for external LLM and embedding calls. Avoid brittle tests that depend on network access, credentials, or timing.

Run the Maven test lifecycle, review failures, and fix the underlying verified issue without weakening assertions or changing expected behavior merely to make tests pass. Report the test classes, cases covered, and actual test results.

## Prompt 13 — ADD JavaDoc Documentation

Act as a senior Java developer responsible for code documentation.

TASK:
Generate JavaDoc documentation for every class, interface, enum,
constructor, and method in the existing ProofSum Java project.

PROJECT CONTEXT:
- Language: Java
- Framework: Spring Boot
- Build tool: Maven
- Project: ProofSum — a proof-based research paper summarizer.
- Architecture includes PDF processing, text chunking, embeddings,
  retrieval, LLM integration, evidence handling, REST APIs, and storage.

INSTRUCTIONS:

1. SCOPE
- Inspect the entire Java source tree under src/main/java.
- Document every class, interface, enum, constructor, and method.
- Include public, protected, package-private, and private members
  where appropriate.
- Include overloaded methods separately.
- Document fields when their purpose or constraints are not obvious.
- Do not omit methods simply because they are short or private.

2. CLASS-LEVEL JAVADOC
For every class, interface, and enum:
- Provide a concise, one-line summary beginning with an
  appropriate action verb where applicable.
- Explain its responsibility and role in the application.
- Describe important contracts, constraints, and relationships
  with other components when evident from the code.
- Do not describe implementation details unnecessarily.
- Do not invent functionality or assumptions.

3. METHOD-LEVEL JAVADOC
For every method and constructor:
- Begin with a concise summary in imperative mood.
- Describe the method's contract, not its implementation.
- Document the purpose and expected behaviour.
- Include @param for every parameter, describing its meaning,
  type-related constraints, and valid values where evident.
- Include @return for every non-void method, describing the
  returned value and relevant guarantees.
- Include @throws for declared exceptions and unchecked
  exceptions that callers should reasonably be aware of,
  provided they are supported by the code.
- Document nullability, validation rules, boundary conditions,
  and side effects when evident.
- Document thread-safety or mutability constraints only when
  they can be established from the code.

4. DOCUMENTATION ACCURACY
- Derive documentation from the actual source code.
- Do not invent parameter constraints, exceptions, return-value
  guarantees, or business rules.
- Do not claim that a method validates something unless it does.
- Do not describe expected behaviour as guaranteed behaviour
  unless the code establishes that guarantee.
- If important behaviour cannot be determined from the code,
  write "TODO" for that specific detail and skip unsupported claims.
- Prefer concise, precise documentation over lengthy explanations.

5. SPRING BOOT COMPONENTS
Pay particular attention to:
- REST controllers: endpoint purpose, request parameters,
  request bodies, response types, and relevant HTTP errors.
- Services: input contracts, returned values, and failure conditions.
- Models and DTOs: field meanings and constraints.
- Configuration classes: configuration responsibilities.
- Storage components: storage and retrieval contracts.
- Exception handlers: errors handled and corresponding responses.
- Utility classes: valid inputs and expected outputs.

Do not duplicate framework documentation or document inherited
methods unnecessarily.

6. JAVADOC TAGS
Use standard JavaDoc tags appropriately:
- @param
- @return
- @throws
- @since, only when version information is available
- @deprecated, only when a declaration is actually deprecated

Do not add empty tags or unnecessary tags.

7. PRESERVE THE IMPLEMENTATION
- Do not modify method bodies, business logic, method signatures,
  annotations, imports, or application configuration.
- Do not rename classes, fields, methods, or parameters.
- Do not change access modifiers or refactor existing code.
- Only add or improve JavaDoc comments.
- Preserve existing valid documentation unless improvement is needed.

8. WORKFLOW
Before making changes:
- Inspect the project structure and identify all Java source files.
- Determine which declarations already have JavaDoc.
- Identify undocumented declarations and existing documentation
  that may be inaccurate or incomplete.

Then:
- Document one class at a time.
- Review each comment against the corresponding declaration.
- Ensure that all relevant parameters and return values are covered.
- Avoid modifying source code outside the documentation comments.

After completing each file:
- Review the diff.
- Confirm that only documentation comments were changed.
- Check for malformed JavaDoc tags or invalid comment syntax.

9. VALIDATION
After completing the documentation:
- Compile the project using Maven.
- Run the existing automated tests if available.
- Generate JavaDoc using the Maven Javadoc plugin if configured
  or configure it without changing application behaviour.
- Address genuine JavaDoc errors without changing business logic.
- Report any unresolved documentation TODOs.

10. FINAL REPORT
Provide:
- The Java files documented.
- The number of classes, constructors, and methods documented.
- Any declarations that still contain TODOs.
- The validation commands executed and their results.
- Any remaining JavaDoc warnings or errors.
- Confirmation that application logic was not modified.

Begin by inspecting the project. Do not modify any files until
the initial inspection is complete.

## Prompt 13 — Configure JaCoCo and generate the coverage report
Act as a senior Java developer experienced in Maven, Spring Boot,
JUnit 5, and JaCoCo code coverage analysis.

PROJECT:
ProofSum — Proof-Based Research Paper Summarizer Using Java.

OBJECTIVE:
Integrate JaCoCo into the existing Maven project to measure
test coverage and generate an HTML coverage report.

The project already contains JUnit test classes for:
- ChunkingService
- EmbeddingService
- RetrievalService
- InMemoryVectorStore

TASKS:

1. INSPECT THE EXISTING PROJECT
- Examine pom.xml and identify the Java version, Spring Boot
  version, Maven plugins, and existing test dependencies.
- Inspect the existing test directory and identify available
  JUnit test classes.
- Check whether JaCoCo is already configured.
- Identify existing build configuration that could conflict
  with the proposed changes.
- Do not modify any files before completing this inspection.

2. CONFIGURE JACOCO
- Add the appropriate JaCoCo Maven plugin configuration
  to pom.xml if it is not already present.
- Select a stable plugin version compatible with the project's
  Java and Maven configuration.
- Configure the plugin to collect execution data during tests.
- Configure report generation during the appropriate Maven
  build phase.
- Avoid duplicate plugin declarations or unnecessary dependencies.
- Preserve all existing Spring Boot, Maven, and JUnit settings.
- Do not modify application business logic.

3. GENERATE COVERAGE REPORTS
Configure JaCoCo to generate an HTML coverage report containing:
- Overall instruction and branch coverage.
- Coverage by package and class.
- Line and method coverage.
- Uncovered code that may require additional tests.

The standard HTML report should be generated at:
target/site/jacoco/index.html

4. VALIDATE THE CONFIGURATION
After making the changes:
- Run the Maven test lifecycle.
- Generate the JaCoCo report.
- Confirm that the expected report files are created.
- Check that the report contains coverage data from the tests
  that actually executed.
- Confirm that existing tests still pass.
- Report any build failures or configuration issues.

Use the following command when appropriate:
mvn clean verify

Do not claim that coverage has been measured successfully
unless the build completes and the report contains execution data.

5. ANALYSE COVERAGE
Review the generated report and identify:
- Classes with low or zero coverage.
- Important methods that are not exercised by tests.
- Conditional branches that remain untested.
- Areas where additional unit tests would improve confidence.

Pay particular attention to:
- PDF processing and validation.
- Text chunking.
- Embedding generation.
- Retrieval and similarity thresholds.
- Vector-store operations.
- LLM error handling and fallback behaviour.
- REST API and exception handling, where tests exist.

Do not assume that every uncovered line represents a defect.
Prioritise testing according to functionality and risk.

6. TESTING RECOMMENDATIONS
Based on the actual coverage report:
- Recommend additional tests for important uncovered behaviour.
- Identify edge cases and failure conditions.
- Do not generate unnecessary tests merely to increase
  the coverage percentage.
- Do not modify existing tests without explaining why.
- Do not invent coverage results.

7. FINAL REPORT
Provide:
- The changes made to pom.xml.
- The JaCoCo configuration and report location.
- The Maven commands executed.
- The number of tests executed, passed, failed, or skipped.
- The measured coverage percentages, if available.
- Important uncovered classes or methods.
- Recommended next steps.

IMPORTANT CONSTRAINTS:
- Inspect before modifying.
- Preserve existing project architecture and application logic.
- Make only the changes required for JaCoCo integration.
- Explain significant configuration decisions.
- Verify all reported results against actual command output.

