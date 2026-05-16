package com.thirdexploration.promengine.memory.retrieval;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lucene 索引服务，支持近实时搜索与批量提交。
 */
@Slf4j
@Service
public class LuceneIndexService {

    @Value("${promengine.data-dir:./data}")
    private String dataDir;

    private StandardAnalyzer analyzer;
    private FSDirectory episodicDir;
    private FSDirectory semanticDir;
    private IndexWriter episodicWriter;
    private IndexWriter semanticWriter;
    private final AtomicInteger episodicDocCount = new AtomicInteger(0);
    private final AtomicInteger semanticDocCount = new AtomicInteger(0);
    private static final int COMMIT_BATCH_SIZE = 100;
    private static final long COMMIT_INTERVAL_MS = 5000;

    @PostConstruct
    public void init() throws IOException {
        analyzer = new StandardAnalyzer();
        episodicDir = FSDirectory.open(Paths.get(dataDir, "memory", "lucene", "episodic"));
        semanticDir = FSDirectory.open(Paths.get(dataDir, "memory", "lucene", "semantic"));

        IndexWriterConfig episodicConfig = new IndexWriterConfig(analyzer);
        episodicConfig.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        episodicConfig.setRAMBufferSizeMB(16.0);
        episodicWriter = new IndexWriter(episodicDir, episodicConfig);

        IndexWriterConfig semanticConfig = new IndexWriterConfig(analyzer);
        semanticConfig.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        semanticConfig.setRAMBufferSizeMB(16.0);
        semanticWriter = new IndexWriter(semanticDir, semanticConfig);

        log.info("Lucene indexes initialized with NRT support");
    }

    @PreDestroy
    public void cleanup() throws IOException {
        if (episodicWriter != null) {
            episodicWriter.commit();
            episodicWriter.close();
        }
        if (semanticWriter != null) {
            semanticWriter.commit();
            semanticWriter.close();
        }
        if (episodicDir != null) episodicDir.close();
        if (semanticDir != null) semanticDir.close();
    }

    public void indexEpisodic(String id, String content, String summary) {
        index(episodicWriter, id, content, summary, episodicDocCount);
    }

    public void indexSemantic(String id, String content, String summary) {
        index(semanticWriter, id, content, summary, semanticDocCount);
    }

    private void index(IndexWriter writer, String id, String content, String summary, AtomicInteger counter) {
        Document doc = new Document();
        doc.add(new StringField("id", id, Field.Store.YES));
        doc.add(new TextField("content", content == null ? "" : content, Field.Store.NO));
        doc.add(new TextField("summary", summary == null ? "" : summary, Field.Store.NO));
        try {
            writer.updateDocument(new Term("id", id), doc);
            int count = counter.incrementAndGet();
            if (count % COMMIT_BATCH_SIZE == 0) {
                writer.commit();
                log.debug("Committed Lucene index after {} documents", count);
            }
        } catch (IOException e) {
            log.error("Failed to index document id: {}", id, e);
        }
    }

    public List<String> searchEpisodic(String queryText, int limit) {
        return search(episodicWriter, episodicDir, queryText, limit);
    }

    public List<String> searchSemantic(String queryText, int limit) {
        return search(semanticWriter, semanticDir, queryText, limit);
    }

    private List<String> search(IndexWriter writer, FSDirectory dir, String queryText, int limit) {
        if (queryText == null || queryText.isBlank()) return List.of();
        List<String> ids = new ArrayList<>();
        try {
            // 使用近实时 reader
            try (IndexReader reader = (writer == null) ? DirectoryReader.open(dir) : DirectoryReader.open(writer)) {
                IndexSearcher searcher = new IndexSearcher(reader);
                String escapedQuery = QueryParser.escape(queryText);
                QueryParser parser = new QueryParser("content", analyzer);
                Query query = parser.parse(escapedQuery);
                TopDocs topDocs = searcher.search(query, limit);
                for (ScoreDoc sd : topDocs.scoreDocs) {
                    Document doc = searcher.storedFields().document(sd.doc);
                    ids.add(doc.get("id"));
                }
            }
        } catch (IndexNotFoundException e) {
            log.debug("Index not found");
        } catch (org.apache.lucene.queryparser.classic.ParseException e) {
            log.warn("Query parse failed: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("Search failed", e);
        }
        return ids;
    }

    public void deleteEpisodic(String id) {
        delete(episodicWriter, id);
    }

    public void deleteSemantic(String id) {
        delete(semanticWriter, id);
    }

    private void delete(IndexWriter writer, String id) {
        try {
            writer.deleteDocuments(new Term("id", id));
        } catch (IOException e) {
            log.error("Failed to delete index for id: {}", id, e);
        }
    }

    // 定期调用，也可由外部定时任务触发
    public void forceCommit() {
        try {
            episodicWriter.commit();
            semanticWriter.commit();
            log.debug("Forced commit on Lucene indexes");
        } catch (IOException e) {
            log.error("Failed to commit Lucene indexes", e);
        }
    }
}