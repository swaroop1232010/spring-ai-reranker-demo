package com.swaroop.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.decision.DecisionModel;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.postretrieval.document.DecisionDocumentReranker;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagConfig {

    /** How many passages the LLM finally sees, in both modes. */
    public static final int FINAL_K = 3;
    /** How many candidates the vector search fetches before reranking. */
    public static final int CANDIDATE_K = 15;

    /** Baseline: plain vector search, top 3 straight to the LLM. */
    @Bean
    VectorStoreDocumentRetriever baselineRetriever(VectorStore vectorStore) {
        return VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .topK(FINAL_K)
                .build();
    }

    /** Wide net for the reranker: fetch 15 candidates, let the reranker pick the best 3. */
    @Bean
    VectorStoreDocumentRetriever candidateRetriever(VectorStore vectorStore) {
        return VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .topK(CANDIDATE_K)
                .build();
    }

    @Bean
    DecisionDocumentReranker reranker(DecisionModel decisionModel) {
        return DecisionDocumentReranker.builder(decisionModel)
                .topK(FINAL_K)
                .concurrency(8)        // one model call per candidate passage
                .build();
    }

    @Bean
    ChatClient baselineChat(ChatModel chatModel, VectorStoreDocumentRetriever baselineRetriever) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(RetrievalAugmentationAdvisor.builder()
                        .documentRetriever(baselineRetriever)
                        .build())
                .build();
    }

    @Bean
    ChatClient rerankedChat(ChatModel chatModel,
                            VectorStoreDocumentRetriever candidateRetriever,
                            DecisionDocumentReranker reranker) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(RetrievalAugmentationAdvisor.builder()
                        .documentRetriever(candidateRetriever)
                        .documentPostProcessors(reranker)
                        .build())
                .build();
    }
}
