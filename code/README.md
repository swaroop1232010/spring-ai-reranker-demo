# RAG with the Spring AI 2.1 Decision Model reranker (article 02)

Spring Boot 4.2 · Spring AI 2.1.0 · Java 17+ · PostgreSQL 17 + pgvector · Ollama (fully local, no API key)

A small courier help-centre bot (fictional "QuickParcel" data) that answers questions
two ways so you can compare them:

- **baseline** — vector search, top 3 passages → LLM
- **rerank** — vector search, top 15 candidates → `DecisionDocumentReranker` keeps the best 3 → LLM

## Prerequisites
- Docker Desktop running (for pgvector)
- Ollama 0.40.4 or newer (`ollama --version`)

Models used (downloaded automatically on first start):
`qwen2.5:3b` (chat), `nomic-embed-text` (embeddings), `d1` (decisions, 3.7 GB).
To compare with the small default model: `./gradlew bootRun --args="--spring.ai.ollama.decision.model=d1-omni:600m"`.

Results from my run (i7-12700H, CPU only) are in `../proofs/`.

## Run

```powershell
docker compose up -d
./gradlew bootRun
```

## Try it

```powershell
# same question, both modes — compare "passages" and "answer"
Invoke-RestMethod "http://localhost:8080/ask?mode=baseline&q=My Express package came a day and a half late, do I get my money back?" | ConvertTo-Json -Depth 5
Invoke-RestMethod "http://localhost:8080/ask?mode=rerank&q=My Express package came a day and a half late, do I get my money back?" | ConvertTo-Json -Depth 5

# retrieval accuracy on 20 labelled questions (top-1 / top-3 hit rate + latency)
Invoke-RestMethod -Method Post "http://localhost:8080/eval" -TimeoutSec 1800 | ConvertTo-Json -Depth 5
```

`/eval` makes ~300 decision calls; with `d1` on a CPU-only laptop it took 19 minutes.
