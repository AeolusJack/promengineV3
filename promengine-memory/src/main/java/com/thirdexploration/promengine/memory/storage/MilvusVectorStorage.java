package com.thirdexploration.promengine.memory.storage;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.thirdexploration.promengine.memory.config.MemoryProperties;
import com.thirdexploration.promengine.memory.exception.MemoryStorageException;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.request.*;
import io.milvus.v2.service.index.request.CreateIndexReq;
import io.milvus.v2.service.vector.request.*;
import io.milvus.v2.service.vector.response.*;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.CreateCollectionReq.CollectionSchema;
import io.milvus.v2.service.collection.request.CreateCollectionReq.FieldSchema;
import io.milvus.orm.iterator.QueryIterator;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.request.data.BaseVector;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Slf4j
@Component
@ConditionalOnProperty(name = "promengine.memory.vector.engine", havingValue = "milvus")
@Primary
@RequiredArgsConstructor
public class MilvusVectorStorage implements VectorStorage {

    private final MemoryProperties properties;
    private MilvusClientV2 client;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private static final String COLLECTION_NAME = "promengine_memories";
    private int dimension;

    @PostConstruct
    public void init() {
        try {
            Integer configDim = properties.getVector().getDimension();
            dimension = (configDim != null && configDim > 0) ? configDim : 768;
            
            // 1. 连接到 Milvus 服务
            ConnectConfig connectConfig = ConnectConfig.builder()
                    .uri("http://" + properties.getMilvus().getHost() + ":" + properties.getMilvus().getPort())
                    .build();
            client = new MilvusClientV2(connectConfig);

            // 2. 检查 Collection 是否存在，如果不存在则创建
            Boolean exists = client.hasCollection(HasCollectionReq.builder()
                    .collectionName(COLLECTION_NAME)
                    .build());

            if (!exists) {
                createCollection();
            } else {
                log.info("Milvus collection '{}' already exists", COLLECTION_NAME);
                // 确保 Collection 已加载到内存
                loadCollection();
            }
            
            log.info("Milvus vector storage initialized, dimension={}", dimension);
        } catch (Exception e) {
            log.error("Failed to initialize Milvus", e);
            throw new MemoryStorageException("Milvus initialization failed", e);
        }
    }

    private void createCollection() {
        // 1. 定义 Schema
        FieldSchema idField = FieldSchema.builder()
                .name("id")
                .dataType(DataType.VarChar)
                .isPrimaryKey(true)
                .maxLength(100)
                .build();
                
        FieldSchema vectorField = FieldSchema.builder()
                .name("vector")
                .dataType(DataType.FloatVector)
                .dimension(dimension)
                .build();
                
        FieldSchema metadataField = FieldSchema.builder()
                .name("metadata")
                .dataType(DataType.VarChar)
                .maxLength(65535)
                .build();

        CollectionSchema schema = CollectionSchema.builder()
                .fieldSchemaList(Arrays.asList(idField, vectorField, metadataField))
                .build();

        // 2. 创建 Collection
        CreateCollectionReq createReq = CreateCollectionReq.builder()
                .collectionName(COLLECTION_NAME)
                .collectionSchema(schema)
                .build();
        client.createCollection(createReq);
        
        // 3. 创建索引 (Milvus 要求在加载 Collection 前必须为向量字段创建索引)
        IndexParam indexParam = IndexParam.builder()
                .fieldName("vector")
                .indexType(IndexParam.IndexType.HNSW)
                .metricType(IndexParam.MetricType.COSINE)
                .build();
                
        CreateIndexReq createIndexReq = CreateIndexReq.builder()
                .collectionName(COLLECTION_NAME)
                .indexParams(Collections.singletonList(indexParam))
                .build();
        client.createIndex(createIndexReq);
        
        // 4. 加载 Collection 到内存
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

    private final Gson gson = new Gson(); // 创建 Gson 实例用于对象转换

    @Override
    public void add(String id, float[] vector, String metadataJson) {
        lock.writeLock().lock();
        try {
            // 1. 将向量和元数据构造成一个 JsonObject
            JsonObject data = new JsonObject();
            data.addProperty("id", id);
            data.add("vector", gson.toJsonTree(convertToFloatList(vector))); // 转换为List<Float>
            data.addProperty("metadata", metadataJson);

            // 2. 构建插入请求，data() 方法接受一个 List<JsonObject>
            InsertReq insertReq = InsertReq.builder()
                    .collectionName(COLLECTION_NAME)
                    .data(Collections.singletonList(data)) // 关键修改点：使用 JSONObject 列表
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
                JsonObject data = new JsonObject();
                data.addProperty("id", rec.id());
                data.add("vector", gson.toJsonTree(convertToFloatList(rec.vector())));
                data.addProperty("metadata", rec.metadata());
                dataList.add(data);
            }

            // 构建批量插入请求
            InsertReq insertReq = InsertReq.builder()
                    .collectionName(COLLECTION_NAME)
                    .data(dataList) // 直接传入 JSONObject 列表
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

    // 工具方法：将 float[] 转换为 List<Float>
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
            // 1. 构建搜索参数
            Map<String, Object> searchParams = new HashMap<>();
            searchParams.put("ef", 100);

            SearchReq searchReq = SearchReq.builder()
                    .collectionName(COLLECTION_NAME)
                    .data(Collections.singletonList(new FloatVec(queryVector)))
                    .topK(topK)
                    .outputFields(Arrays.asList("id", "metadata"))
                    .searchParams(searchParams)
                    .build();

            // 2. 执行搜索
            SearchResp searchResp = client.search(searchReq);

            // 3. 处理结果
            List<SearchHit> hits = new ArrayList<>();
            List<List<SearchResp.SearchResult>> allSearchResults = searchResp.getSearchResults();

            if (allSearchResults != null && !allSearchResults.isEmpty()) {
                List<SearchResp.SearchResult> resultsForSingleQuery = allSearchResults.get(0);
                for (SearchResp.SearchResult result : resultsForSingleQuery) {
                    // 修正点：尝试通过 getEntity() 获取字段 Map
                    // 如果 getEntity() 不可用，直接使用 getId() 和 getScore() 方法
                    Map<String, Object> fields = result.getEntity();
                    String id = (String) fields.get("id");
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
            IndexParam indexParam = IndexParam.builder()
                    .fieldName("vector")
                    .indexType(IndexParam.IndexType.HNSW)
                    .metricType(IndexParam.MetricType.COSINE)
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