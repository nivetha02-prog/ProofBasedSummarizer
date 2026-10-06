# PROMPTS

1. Before writing any code, inspect the entire existing Java/Maven project.

Determine:

1. Current directory structure
2. pom.xml dependencies
3. Java version
4. Spring Boot version
5. Existing packages
6. Existing classes
7. Existing tests
8. Existing configuration files
9. Existing database configuration
10. Existing Git/CI configuration

Do NOT modify anything yet.

Create a proposed architecture for the project based on the following modules:

M1 — Ingestion
M2 — Chunking & Indexing
M3 — Retrieval
M4 — LLM & Prompting
M5 — Evidence Verifier
M6 — API & UI
M7 — Evaluation

Also identify:
- what already exists
- what is missing
- what should be reused
- what should be created

Do not invent functionality that is already present.

Return the analysis before making changes.