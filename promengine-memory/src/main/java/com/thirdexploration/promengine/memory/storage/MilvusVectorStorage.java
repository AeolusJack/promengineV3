package com.thirdexploration.promengine.memory.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.thirdexploration.promengine.memory.config.AeonMemoryProperties;
import com.thirdexploration.promengine.memory.exception.MemoryStorageException;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.*;
import io.milvus.v2.service.index.request.CreateIndexReq;
import io.milvus.v2.service.index.request.DropIndexReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Slf4j
@Component
@ConditionalOnProperty(name = "aeon.memory.vector.engine", havingValue = "milvus")
@Primary
public class MilvusVectorStorage implements VectorStorage {

    private final AeonMemoryProperties properties;
    private final ObjectMapper objectMapper;
    private final Gson gson = new Gson();
    private MilvusClientV2 client;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private static final String COLLECTION_NAME = "aeon_memories";
    private int dimension;

    public MilvusVectorStorage(AeonMemoryProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        try {
            Integer configDim = properties.getVector().getDimension();
            dimension = (configDim != null && configDim > 0) ? configDim : 768;

            String host = properties.getVector().getMilvusHost();
            int port = properties.getVector().getMilvusPort();
            ConnectConfig connectConfig = ConnectConfig.builder()
                    .uri("http://" + host + ":" + port)
                    .build();
            client = new MilvusClientV2(connectConfig);

            Boolean exists = client.hasCollection(HasCollectionReq.builder()
                    .collectionName(COLLECTION_NAME)
                    .build());

            if (!exists) {
                createCollection();
            } else {
                log.info("Milvus collection '{}' already exists", COLLECTION_NAME);
                loadCollection();
            }

            log.info("Milvus vector storage initialized, dimension={}", dimension);
        } catch (Exception e) {
            log.error("Failed to initialize Milvus", e);
            throw new MemoryStorageException("Milvus initialization failed", e);
        }
    }

    private void createCollection() {
        // 定义 Schema
        CreateCollectionReq.FieldSchema idField = CreateCollectionReq.FieldSchema.builder()
                .name("id")
                .dataType(DataType.VarChar)
                .isPrimaryKey(true)
                .maxLength(128)
                .build();

        CreateCollectionReq.FieldSchema vectorField = CreateCollectionReq.FieldSchema.builder()
                .name("vector")
                .dataType(DataType.FloatVector)
                .dimension(dimension)
                .build();

        CreateCollectionReq.FieldSchema metadataField = CreateCollectionReq.FieldSchema.builder()
                .name("metadata")
                .dataType(DataType.VarChar)
                .maxLength(65535)
                .build();

        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
                .fieldSchemaList(Arrays.asList(idField, vectorField, metadataField))
                .build();

        CreateCollectionReq createReq = CreateCollectionReq.builder()
                .collectionName(COLLECTION_NAME)
                .collectionSchema(schema)
                .build();
        client.createCollection(createReq);

        // 创建索引
        IndexParam indexParam = IndexParam.builder()
                .fieldName("vector")
                .indexType(IndexParam.IndexType.HNSW)
                .metricType(IndexParam.MetricType.COSINE)
                .extraParams(Map.of("M", 16, "efConstruction", 200))
                .build();

        CreateIndexReq createIndexReq = CreateIndexReq.builder()
                .collectionName(COLLECTION_NAME)
                .indexParams(Collections.singletonList(indexParam))
                .build();
        client.createIndex(createIndexReq);

        loadCollection();
        log.info("Created Milvus collection '{}' with dimension {}", COLLECTION_NAME, dimension);
    }

    private void loadCollection() {
        LoadCollectionReq loadReq = LoadCollectionReq.builder()
                .collectionName(COLLECTION_NAME)
                .build();
        client.loadCollection(loadReq);
    }

    @PreDestroy
    public void cleanup() {
        if (client != null) {
            client.close();
        }
        log.info("Milvus vector storage shut down");
    }

    @Override
    public void add(String id, float[] vector, String metadataJson) {
        lock.writeLock().lock();
        try {
            InsertReq insertReq = InsertReq.builder()
                    .collectionName(COLLECTION_NAME)
                    .data(Collections.singletonList(buildJsonObject(id, vector, metadataJson)))
                    .build();
            client.insert(insertReq);
            log.debug("Added vector for id={}", id);
        } catch (Exception e) {
            log.error("Failed to add vector for id={}", id, e);
            throw new MemoryStorageException("Vector add failed", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void batchAdd(List<VectorRecord> records) {
        if (records.isEmpty()) return;
        lock.writeLock().lock();
        try {
            List<JsonObject> dataList = new ArrayList<>();
            for (VectorRecord rec : records) {
                dataList.add(buildJsonObject(rec.id(), rec.vector(), rec.metadata()));
            }
            InsertReq insertReq = InsertReq.builder()
                    .collectionName(COLLECTION_NAME)
                    .data(dataList)
                    .build();
            client.insert(insertReq);
            log.info("Batch added {} vectors", records.size());
        } catch (Exception e) {
            log.error("Failed to batch add vectors", e);
            throw new MemoryStorageException("Vector batch add failed", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 构建符合 Milvus SDK 要求的 JsonObject。
     */
    private JsonObject buildJsonObject(String id, float[] vector, String metadata) {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.add("vector", gson.toJsonTree(convertToFloatList(vector)));
        json.addProperty("metadata", metadata);
        return json;
    }

    private List<Float> convertToFloatList(float[] array) {
        List<Float> list = new ArrayList<>(array.length);
        for (float v : array) {
            list.add(v);
        }
        return list;
    }

    @Override
    public List<SearchHit> search(float[] queryVector, int topK) {
        lock.readLock().lock();
        try {
            Map<String, Object> searchParams = Map.of("ef", 100);

            SearchReq searchReq = SearchReq.builder()
                    .collectionName(COLLECTION_NAME)
                    .data(Collections.singletonList(new FloatVec(queryVector)))
                    .topK(topK)
                    .outputFields(Arrays.asList("id", "metadata"))
                    .searchParams(searchParams)
                    .build();

            SearchResp searchResp = client.search(searchReq);

            List<SearchHit> hits = new ArrayList<>();
            List<List<SearchResp.SearchResult>> allSearchResults = searchResp.getSearchResults();

            if (allSearchResults != null && !allSearchResults.isEmpty()) {
                for (SearchResp.SearchResult result : allSearchResults.get(0)) {
                    String id = (String) result.getId();
                    float score = result.getScore();
                    hits.add(new SearchHit(id, score));
                }
            }
            return hits;
        } catch (Exception e) {
            log.error("Vector search failed", e);
            return List.of();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void delete(String id) {
        lock.writeLock().lock();
        try {
            DeleteReq deleteReq = DeleteReq.builder()
                    .collectionName(COLLECTION_NAME)
                    .filter("id == \"" + id + "\"")
                    .build();
            client.delete(deleteReq);
            log.debug("Deleted vector for id={}", id);
        } catch (Exception e) {
            log.error("Failed to delete vector id={}", id, e);
            throw new MemoryStorageException("Vector delete failed", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void rebuildIndex() {
        lock.writeLock().lock();
        try {
            DropIndexReq dropIndexReq = DropIndexReq.builder()
                    .collectionName(COLLECTION_NAME)
                    .fieldName("vector")
                    .build();
            client.dropIndex(dropIndexReq);

            IndexParam indexParam = IndexParam.builder()
                    .fieldName("vector")
                    .indexType(IndexParam.IndexType.HNSW)
                    .metricType(IndexParam.MetricType.COSINE)
                    .extraParams(Map.of("M", 16, "efConstruction", 200))
                    .build();

            CreateIndexReq createIndexReq = CreateIndexReq.builder()
                    .collectionName(COLLECTION_NAME)
                    .indexParams(Collections.singletonList(indexParam))
                    .build();
            client.createIndex(createIndexReq);
            log.info("Milvus index rebuilt");
        } catch (Exception e) {
            log.error("Failed to rebuild index", e);
        } finally {
            lock.writeLock().unlock();
        }
    }
}