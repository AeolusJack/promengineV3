package com.thirdexploration.promengine.memory.retrieval;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * aeon
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LuceneIndexService {

    @Value("${promengine.data-dir:./data}")
    private String dataDir;

    private IndexWriter episodicWriter;
    private IndexWriter semanticWriter;
    private StandardAnalyzer analyzer;
    private FSDirectory episodicDir;
    private FSDirectory semanticDir;

    @PostConstruct
    public void init() throws IOException {
        analyzer = new StandardAnalyzer();
        episodicDir = FSDirectory.open(Paths.get(dataDir, "memory", "lucene", "episodic"));
        semanticDir = FSDirectory.open(Paths.get(dataDir, "memory", "lucene", "semantic"));

        // 为每个 IndexWriter 创建独立的 IndexWriterConfig
        IndexWriterConfig episodicConfig = new IndexWriterConfig(analyzer);
        episodicConfig.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        episodicWriter = new IndexWriter(episodicDir, episodicConfig);

        IndexWriterConfig semanticConfig = new IndexWriterConfig(analyzer);
        semanticConfig.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        semanticWriter = new IndexWriter(semanticDir, semanticConfig);

        log.info("Lucene indexes initialized");
    }



    @PreDestroy
    public void cleanup() throws IOException {
        if (episodicWriter != null) episodicWriter.close();
        if (semanticWriter != null) semanticWriter.close();
        if (episodicDir != null) episodicDir.close();
        if (semanticDir != null) semanticDir.close();
    }

    public void indexEpisodic(String id, String content, String summary) {
        Document doc = new Document();
        doc.add(new StringField("id", id, Field.Store.YES));
        doc.add(new TextField("content", content, Field.Store.NO));
        doc.add(new TextField("summary", summary != null ? summary : "", Field.Store.NO));
        try {
            episodicWriter.updateDocument(new Term("id", id), doc);
            episodicWriter.commit();
        } catch (IOException e) {
            log.error("Failed to index episodic memory: {}", id, e);
        }
    }

    public void indexSemantic(String id, String content, String summary) {
        Document doc = new Document();
        doc.add(new StringField("id", id, Field.Store.YES));
        doc.add(new TextField("content", content, Field.Store.NO));
        doc.add(new TextField("summary", summary != null ? summary : "", Field.Store.NO));
        try {
            semanticWriter.updateDocument(new Term("id", id), doc);
            semanticWriter.commit();
        } catch (IOException e) {
            log.error("Failed to index semantic memory: {}", id, e);
        }
    }

    public List<String> searchEpisodic(String queryText, int limit) {
        return search(episodicDir, queryText, limit);
    }

    public List<String> searchSemantic(String queryText, int limit) {
        return search(semanticDir, queryText, limit);
    }

    private List<String> search(FSDirectory dir, String queryText, int limit) {
        List<String> ids = new ArrayList<>();
        try {
            if (!DirectoryReader.indexExists(dir)) {
                log.debug("Lucene index does not exist at {}, returning empty results", dir);
                return ids;
            }
            try (IndexReader reader = DirectoryReader.open(dir)) {
                IndexSearcher searcher = new IndexSearcher(reader);
                // 关键修复：对查询文本进行转义，避免特殊字符导致解析错误
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
            log.debug("Lucene index not found at {}", dir);
        } catch (ParseException e) {
            log.warn("Lucene query parse failed for '{}': {}", queryText, e.getMessage());
        } catch (Exception e) {
            log.warn("Lucene search failed: {}", e.getMessage());
        }
        return ids;
    }

    public void deleteEpisodic(String id) {
        try {
            episodicWriter.deleteDocuments(new Term("id", id));
            episodicWriter.commit();
        } catch (IOException e) {
            log.error("Failed to delete episodic index: {}", id, e);
        }
    }

    public void deleteSemantic(String id) {
        try {
            semanticWriter.deleteDocuments(new Term("id", id));
            semanticWriter.commit();
        } catch (IOException e) {
            log.error("Failed to delete semantic index: {}", id, e);
        }
    }
}