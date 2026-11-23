package com.swaroop.rag;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DecisionDocumentReranker;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.json.JsonMapper;

/**
 * POST /eval — retrieval-only evaluation, no LLM answer needed.
 * For every question in eval-questions.json we know which passage holds the answer,
 * so we check whether it lands at rank 1 / in the top 3, with and without reranking.
 */
@RestController
public class EvalController {

    record EvalQuestion(String question, String expected) {}

    private final VectorStoreDocumentRetriever baselineRetriever;
    private final VectorStoreDocumentRetriever candidateRetriever;
    private final DecisionDocumentReranker reranker;

    public EvalController(@Qualifier("baselineRetriever") VectorStoreDocumentRetriever baselineRetriever,
                          @Qualifier("candidateRetriever") VectorStoreDocumentRetriever candidateRetriever,
                          DecisionDocumentReranker reranker) {
        this.baselineRetriever = baselineRetriever;
        this.candidateRetriever = candidateRetriever;
        this.reranker = reranker;
    }

    @PostMapping("/eval")
    public Map<String, Object> eval() throws IOException {
        List<EvalQuestion> questions = List.of(JsonMapper.builder().build().readValue(
                new ClassPathResource("eval-questions.json").getInputStream(),
                EvalQuestion[].class));

        int baseTop1 = 0, baseTop3 = 0, rrTop1 = 0, rrTop3 = 0;
        long baseMs = 0, rrMs = 0;
        List<Map<String, Object>> rows = new ArrayList<>();

        for (EvalQuestion q : questions) {
            Query query = new Query(q.question());

            long t0 = System.nanoTime();
            List<String> base = ids(baselineRetriever.retrieve(query));
            long t1 = System.nanoTime();
            List<String> rr = ids(reranker.process(query, candidateRetriever.retrieve(query)));
            long t2 = System.nanoTime();

            baseMs += (t1 - t0) / 1_000_000;
            rrMs += (t2 - t1) / 1_000_000;
            if (!base.isEmpty() && base.get(0).equals(q.expected())) baseTop1++;
            if (base.contains(q.expected())) baseTop3++;
            if (!rr.isEmpty() && rr.get(0).equals(q.expected())) rrTop1++;
            if (rr.contains(q.expected())) rrTop3++;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("question", q.question());
            row.put("expected", q.expected());
            row.put("baseline", base);
            row.put("reranked", rr);
            rows.add(row);
        }

        int n = questions.size();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("questions", n);
        summary.put("baseline_top1", pct(baseTop1, n));
        summary.put("baseline_top3", pct(baseTop3, n));
        summary.put("reranked_top1", pct(rrTop1, n));
        summary.put("reranked_top3", pct(rrTop3, n));
        summary.put("baseline_avg_ms", baseMs / n);
        summary.put("reranked_avg_ms", rrMs / n);
        summary.put("decision_calls_per_question", RagConfig.CANDIDATE_K);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("details", rows);
        return result;
    }

    private static List<String> ids(List<Document> docs) {
        return docs.stream().map(d -> String.valueOf(d.getMetadata().get("passage_id"))).toList();
    }

    private static String pct(int hits, int n) {
        return hits + "/" + n + " (" + Math.round(100.0 * hits / n) + "%)";
    }
}
