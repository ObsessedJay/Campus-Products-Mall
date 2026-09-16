package com.sk.onlinemall.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sk.onlinemall.common.api.PageResponse;
import com.sk.onlinemall.product.model.ProductEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "app.search", name = "enabled", havingValue = "true")
public class ElasticsearchProductSearchGateway implements ProductSearchGateway {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String endpoint;
    private final String index;

    /**
     * 创建 Elasticsearch 商品搜索网关。
     *
     * @param objectMapper JSON 映射组件
     * @param endpoint Elasticsearch 地址
     * @param index 商品索引名
     */
    public ElasticsearchProductSearchGateway(ObjectMapper objectMapper,
                                               @Value("${app.search.endpoint:http://localhost:9200}") String endpoint,
                                               @Value("${app.search.index:campus-products}") String index) {
        this.objectMapper = objectMapper;
        this.endpoint = endpoint.replaceAll("/+$", "");
        this.index = index;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    /**
     * 使用 Elasticsearch 组合查询商品。
     *
     * @param keyword 关键词
     * @param categoryId 分类主键
     * @param saleType 发售方式
     * @param minPrice 最低价格
     * @param maxPrice 最高价格
     * @param sort 排序方式
     * @param page 页码
     * @param size 每页数量
     * @return 搜索分页结果
     */
    @Override
    public PageResponse<ProductEntity> search(String keyword, Long categoryId, String saleType,
                                               BigDecimal minPrice, BigDecimal maxPrice, String sort,
                                               int page, int size) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("from", (page - 1) * size);
            root.put("size", size);
            ObjectNode bool = root.putObject("query").putObject("bool");
            ArrayNode must = bool.putArray("must");
            if (keyword != null) {
                ObjectNode multi = must.addObject().putObject("multi_match");
                multi.put("query", keyword);
                multi.putArray("fields").add("name^3").add("subtitle^2").add("description");
            } else {
                must.addObject().putObject("match_all");
            }
            ArrayNode filters = bool.putArray("filter");
            filters.addObject().putObject("term").put("status", "ON_SALE");
            if (categoryId != null) filters.addObject().putObject("term").put("categoryId", categoryId);
            if (saleType != null) filters.addObject().putObject("term").put("saleType", saleType);
            if (minPrice != null || maxPrice != null) {
                ObjectNode range = filters.addObject().putObject("range").putObject("price");
                if (minPrice != null) range.put("gte", minPrice);
                if (maxPrice != null) range.put("lte", maxPrice);
            }
            ArrayNode sorts = root.putArray("sort");
            switch (sort) {
                case "priceAsc" -> sorts.addObject().putObject("price").put("order", "asc");
                case "priceDesc" -> sorts.addObject().putObject("price").put("order", "desc");
                case "sales" -> sorts.addObject().putObject("soldCount").put("order", "desc");
                default -> sorts.addObject().putObject("createdAt").put("order", "desc");
            }
            JsonNode response = send("POST", "/" + index + "/_search", objectMapper.writeValueAsString(root));
            JsonNode hits = response.path("hits");
            List<ProductEntity> products = new ArrayList<>();
            for (JsonNode hit : hits.path("hits")) {
                products.add(objectMapper.treeToValue(hit.path("_source"), ProductEntity.class));
            }
            return PageResponse.of(products, hits.path("total").path("value").asLong(), page, size);
        } catch (Exception exception) {
            throw new IllegalStateException("Elasticsearch product search failed", exception);
        }
    }

    /**
     * 删除旧索引并批量写入商品快照。
     *
     * @param products 商品快照
     */
    @Override
    public void rebuild(List<ProductEntity> products) {
        try {
            sendAllowNotFound("DELETE", "/" + index, null);
            ObjectNode mapping = objectMapper.createObjectNode();
            ObjectNode properties = mapping.putObject("mappings").putObject("properties");
            properties.putObject("name").put("type", "text");
            properties.putObject("subtitle").put("type", "text");
            properties.putObject("description").put("type", "text");
            for (String field : List.of("status", "saleType")) properties.putObject(field).put("type", "keyword");
            properties.putObject("categoryId").put("type", "long");
            properties.putObject("price").put("type", "scaled_float").put("scaling_factor", 100);
            properties.putObject("soldCount").put("type", "integer");
            properties.putObject("createdAt").put("type", "date");
            send("PUT", "/" + index, objectMapper.writeValueAsString(mapping));
            if (!products.isEmpty()) {
                StringBuilder bulk = new StringBuilder();
                for (ProductEntity product : products) {
                    bulk.append("{\"index\":{\"_id\":\"").append(product.getId()).append("\"}}\n");
                    bulk.append(objectMapper.writeValueAsString(product)).append('\n');
                }
                send("POST", "/" + index + "/_bulk?refresh=true", bulk.toString());
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Elasticsearch product index rebuild failed", exception);
        }
    }

    /**
     * 发送 JSON 请求并要求成功状态。
     *
     * @param method HTTP 方法
     * @param path 请求路径
     * @param body 可选请求体
     * @return JSON 响应
     * @throws Exception 请求或解析失败
     */
    private JsonNode send(String method, String path, String body) throws Exception {
        HttpResponse<String> response = exchange(method, path, body);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Elasticsearch returned status " + response.statusCode());
        }
        return response.body().isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(response.body());
    }

    /**
     * 发送允许资源不存在的请求。
     *
     * @param method HTTP 方法
     * @param path 请求路径
     * @param body 可选请求体
     * @throws Exception 请求失败
     */
    private void sendAllowNotFound(String method, String path, String body) throws Exception {
        HttpResponse<String> response = exchange(method, path, body);
        if ((response.statusCode() < 200 || response.statusCode() >= 300) && response.statusCode() != 404) {
            throw new IllegalStateException("Elasticsearch returned status " + response.statusCode());
        }
    }

    /**
     * 执行底层 HTTP 交换。
     *
     * @param method HTTP 方法
     * @param path 请求路径
     * @param body 可选请求体
     * @return HTTP 响应
     * @throws Exception 网络失败或线程中断
     */
    private HttpResponse<String> exchange(String method, String path, String body) throws Exception {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body);
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint + path))
                .timeout(Duration.ofSeconds(3)).header("Content-Type", "application/json")
                .method(method, publisher).build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
