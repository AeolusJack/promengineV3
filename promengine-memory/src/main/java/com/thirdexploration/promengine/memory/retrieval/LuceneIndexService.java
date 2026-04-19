package com.thirdexploration.promengine.memory.retrieval;

import com.thirdexploration.promengine.memory.config.MemoryProperties;
import com.thirdexploration.promengine.memory.exception.MemoryStorageException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 Lucene 的关键词索引服务，用于温存储的快速关键词检索。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LuceneIndexService {

    private final MemoryProperties properties;
    private IndexWriter writer;
    private FSDirectory directory;
    private StandardAnalyzer analyzer;

    @PostConstruct
    public void init() {
        try {
            var indexDir = Paths.get(properties.getDataDir(), "memory", "lucene");
            directory = FSDirectory.open(indexDir);
            analyzer = new StandardAnalyzer();
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            writer = new IndexWriter(directory, config);
            log.info("Lucene index initialized at {}", indexDir);
        } catch (IOException e) {
            log.error("Failed to initialize Lucene", e);
            throw new MemoryStorageException("Lucene init failed", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        try {
            if (writer != null) writer.close();
            if (directory != null) directory.close();
        } catch (IOException e) {
            log.warn("Error closing Lucene resources", e);
        }
    }

    /**
     * 索引一条记忆摘要。
     */
    public void index(String id, String userId, long timestamp, String text) {
        Document doc = new Document();
        doc.add(new StringField("id", id, Field.Store.YES));
        doc.add(new StringField("userId", userId, Field.Store.YES));
        doc.add(new LongPoint("timestamp", timestamp));
        doc.add(new TextField("content", text, Field.Store.NO));
        doc.add(new StoredField("timestampVal", timestamp));
        try {
            writer.updateDocument(new Term("id", id), doc);
        } catch (IOException e) {
            log.error("Failed to index document id={}", id, e);
        }
    }

    /**
     * 批量索引。
     */
    public void batchIndex(List<IndexEntry> entries) {
        List<Document> docs = new ArrayList<>();
        for (var e : entries) {
            Document doc = new Document();
            doc.add(new StringField("id", e.id(), Field.Store.YES));
            doc.add(new StringField("userId", e.userId(), Field.Store.YES));
            doc.add(new LongPoint("timestamp", e.timestamp()));
            doc.add(new TextField("content", e.text(), Field.Store.NO));
            doc.add(new StoredField("timestampVal", e.timestamp()));
            docs.add(doc);
        }
        try {
            writer.addDocuments(docs);
            writer.commit();
            log.debug("Batch indexed {} documents", docs.size());
        } catch (IOException e) {
            log.error("Batch indexing failed", e);
        }
    }

    /**
     * 关键词检索，返回 ID 列表。
     */
    public List<String> search(String userId, String queryText, int limit) {
        try {
            IndexReader reader = DirectoryReader.open(directory);
            IndexSearcher searcher = new IndexSearcher(reader);
            QueryParser parser = new QueryParser("content", analyzer);
            Query query = parser.parse(queryText);

            BooleanQuery.Builder builder = new BooleanQuery.Builder()
                    .add(query, BooleanClause.Occur.MUST)
                    .add(new TermQuery(new Term("userId", userId)), BooleanClause.Occur.FILTER);

            TopDocs topDocs = searcher.search(builder.build(), limit);
            List<String> ids = new ArrayList<>();
            for (ScoreDoc sd : topDocs.scoreDocs) {
                Document doc = searcher.storedFields().document(sd.doc);
                ids.add(doc.get("id"));
            }
            return ids;
        } catch (Exception e) {
            log.error("Lucene search failed", e);
            return List.of();
        }
    }

    /**
     * 删除索引。
     */
    public void delete(String id) {
        try {
            writer.deleteDocuments(new Term("id", id));
            writer.commit();
        } catch (IOException e) {
            log.error("Failed to delete from Lucene id={}", id, e);
        }
    }

    public record IndexEntry(String id, String userId, long timestamp, String text) {}
}