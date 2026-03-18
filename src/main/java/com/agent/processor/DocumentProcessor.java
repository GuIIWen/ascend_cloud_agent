package com.agent.processor;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 文档处理器 - 基于LangChain4j
 */
public class DocumentProcessor {
    private static final Logger logger = LoggerFactory.getLogger(DocumentProcessor.class);
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final DocumentSplitter splitter;

    public DocumentProcessor(EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> embeddingStore) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.splitter = DocumentSplitters.recursive(500, 50);
    }

    /**
     * 处理并存储文档
     */
    public void processAndStore(Document document) {
        try {
            logger.debug("Processing document from: {}", document.metadata("source"));
            List<TextSegment> segments = splitter.split(document);
            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
            embeddingStore.addAll(embeddings, segments);
            logger.info("Successfully processed document with {} segments", segments.size());
        } catch (Exception e) {
            logger.error("Failed to process document: {}", document.metadata("source"), e);
            throw new RuntimeException("Document processing failed", e);
        }
    }

    /**
     * 批量处理文档
     */
    public void processAndStoreAll(List<Document> documents) {
        logger.info("Processing {} documents", documents.size());
        for (Document doc : documents) {
            processAndStore(doc);
        }
    }
}
