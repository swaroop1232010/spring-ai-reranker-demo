package com.swaroop.rag;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.postretrieval.document.DecisionDocumentReranker;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AskController {

    private final ChatClient baselineChat;
    private final ChatClient rerankedChat;

    public AskController(@Qualifier("baselineChat") ChatClient baselineChat,
                         @Qualifier("rerankedChat") ChatClient rerankedChat) {
        this.baselineChat = baselineChat;
        this.rerankedChat = rerankedChat;
    }

    /** GET /ask?q=...&mode=baseline|rerank — answer plus the passages the LLM saw. */
    @GetMapping("/ask")
    public Map<String, Object> ask(@RequestParam String q,
                                   @RequestParam(defaultValue = "rerank") String mode) {
        ChatClient client = "baseline".equals(mode) ? baselineChat : rerankedChat;

        long start = System.nanoTime();
        ChatClientResponse response = client.prompt().user(q).call().chatClientResponse();
        long millis = (System.nanoTime() - start) / 1_000_000;

        @SuppressWarnings("unchecked")
        List<Document> used = (List<Document>) response.context()
                .getOrDefault(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT, List.of());

        return Map.of(
                "mode", mode,
                "answer", response.chatResponse().getResult().getOutput().getText(),
                "latencyMs", millis,
                "passages", used.stream().map(AskController::describe).toList());
    }

    static Map<String, Object> describe(Document d) {
        Object score = d.getMetadata().get(DecisionDocumentReranker.SCORE_METADATA_KEY);
        return Map.of(
                "id", String.valueOf(d.getMetadata().get("passage_id")),
                "rerankScore", score == null ? "n/a" : score,
                "text", d.getText().length() > 120 ? d.getText().substring(0, 120) + "…" : d.getText());
    }
}
