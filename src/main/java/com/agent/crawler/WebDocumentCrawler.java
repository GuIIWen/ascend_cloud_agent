package com.agent.crawler;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.io.IOException;

/**
 * 网页文档抓取器 - 基于Jsoup
 */
public class WebDocumentCrawler {

    /**
     * 抓取网页内容
     */
    public Document crawl(String url) throws IOException {
        org.jsoup.nodes.Document doc = fetchDocument(url);

        String title = doc.title();
        String mainContent = extractMainContent(doc);

        Metadata metadata = new Metadata();
        metadata.put("source", url);
        metadata.put("title", title);
        return Document.from(mainContent, metadata);
    }

    /**
     * 抓取原始HTML
     */
    public String fetchHtml(String url) throws IOException {
        return fetchDocument(url).outerHtml();
    }

    /**
     * 抓取并返回原始DOM
     */
    public org.jsoup.nodes.Document fetchDocument(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent("Mozilla/5.0")
                .timeout(10000)
                .get();
    }

    /**
     * 提取主要内容
     */
    private String extractMainContent(org.jsoup.nodes.Document doc) {
        doc.select("nav, header, footer, aside, script, style, .sidebar, .advertisement").remove();

        Element main = doc.selectFirst("main, article, .content, .main-content, #content");
        if (main != null) {
            return main.text();
        }

        return doc.body().text();
    }
}
