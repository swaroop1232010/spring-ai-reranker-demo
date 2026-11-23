package com.swaroop.rag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Loads knowledge-base.md into pgvector on first start.
 * Each passage starts with "## <id>" so the eval can check which passage was retrieved.
 */
@Component
public class KnowledgeBaseLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseLoader.class);
    private final VectorStore vectorStore;

    public KnowledgeBaseLoader(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        boolean alreadyLoaded = !vectorStore.similaritySearch(
                SearchRequest.builder().query("delivery").topK(1).build()).isEmpty();
        if (alreadyLoaded) {
            log.info("Knowledge base already in pgvector, skipping load");
            return;
        }
        String text = new ClassPathResource("knowledge-base.md")
                .getContentAsString(StandardCharsets.UTF_8);

        List<Document> docs = new ArrayList<>();
        for (String block : text.split("(?m)^## ")) {
            if (block.isBlank() || block.startsWith("#")) continue; // skip the file's title
            int newline = block.indexOf('\n');
            String id = block.substring(0, newline).trim();
            String body = block.substring(newline + 1).trim();
            docs.add(new Document(body, Map.of("passage_id", id)));
        }
        vectorStore.add(docs);
        log.info("Loaded {} passages into pgvector", docs.size());
    }
}
