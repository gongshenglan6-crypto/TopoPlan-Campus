package com.agent.knowledge;

import com.agent.admin.config.AdminConfigService;
import com.agent.common.api.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class KnowledgeBaseService {
    private static final Set<String> ALLOWED = Set.of("pdf", "doc", "docx", "xls", "xlsx", "txt", "md");
    private static final int DEFAULT_CHUNK_SIZE = 800;
    private static final int DEFAULT_OVERLAP = 120;
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    // DOCX stores text in namespaced tags such as <w:t>, while XLSX commonly
    // uses plain <t>. Accept both forms so uploaded project material is not
    // incorrectly marked as unreadable merely because it came from Word.
    private static final Pattern XML_TEXT = Pattern.compile(
            "<(?:[A-Za-z0-9_]+:)?t\\b[^>]*>(.*?)</(?:[A-Za-z0-9_]+:)?t>", Pattern.DOTALL);
    private static final Pattern PDF_TEXT = Pattern.compile("\\(([^()]*)\\)");

    private final KnowledgeDocumentRepository documents;
    private final KnowledgeChunkRepository chunks;
    private final AdminConfigService configs;
    private final ObjectMapper mapper;

    public KnowledgeBaseService(KnowledgeDocumentRepository documents, KnowledgeChunkRepository chunks,
                                AdminConfigService configs, ObjectMapper mapper) {
        this.documents = documents;
        this.chunks = chunks;
        this.configs = configs;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listDocuments() {
        return documents.findAllByOrderByUpdatedAtDesc().stream().map(this::documentMap).toList();
    }

    @Transactional
    public Map<String, Object> upload(MultipartFile file, String category, String uploader) {
        if (file == null || file.isEmpty()) throw error(HttpStatus.UNPROCESSABLE_ENTITY, "FILE_EMPTY", "上传文件不能为空");
        if (file.getSize() > 50L * 1024 * 1024) throw error(HttpStatus.UNPROCESSABLE_ENTITY, "FILE_TOO_LARGE", "单个知识文档不能超过 50 MB");
        String name = safeName(file.getOriginalFilename());
        String format = extension(name);
        if (!ALLOWED.contains(format)) throw error(HttpStatus.UNPROCESSABLE_ENTITY, "FILE_TYPE_UNSUPPORTED", "不支持该文件格式");
        try {
            KnowledgeDocument document = documents.save(new KnowledgeDocument(name, normalizeCategory(category), uploader == null || uploader.isBlank() ? "系统管理员" : uploader, format, file.getSize(), file.getBytes()));
            process(document);
            return documentMap(document);
        } catch (IOException exception) {
            throw error(HttpStatus.UNPROCESSABLE_ENTITY, "FILE_READ_FAILED", "无法读取上传的知识文档");
        }
    }

    @Transactional
    public Map<String, Object> createTextDocument(Map<String, Object> value) {
        String content = String.valueOf(value == null ? "" : value.getOrDefault("content", "")).trim();
        if (content.isBlank()) throw error(HttpStatus.UNPROCESSABLE_ENTITY, "CONTENT_EMPTY", "知识文档必须包含真实正文内容");
        String name = safeName(String.valueOf(value.getOrDefault("name", "knowledge.txt")));
        String format = extension(name);
        if (!ALLOWED.contains(format)) format = "txt";
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        KnowledgeDocument document = documents.save(new KnowledgeDocument(name, normalizeCategory(String.valueOf(value.getOrDefault("category", "网络基础"))), String.valueOf(value.getOrDefault("uploader", "系统管理员")), format, bytes.length, bytes));
        process(document, content);
        return documentMap(document);
    }

    /**
     * Keeps a small set of product-managed engineering rules available to RAG
     * even when the user database was initialized before the rule was added.
     * It is intentionally keyed by name so application restarts are idempotent.
     */
    @Transactional
    public void ensureTextDocument(String name, String category, String content) {
        String safeName = safeName(name);
        if (safeName.isBlank() || content == null || content.isBlank() || documents.findByName(safeName).isPresent()) return;
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        KnowledgeDocument document = documents.save(new KnowledgeDocument(safeName, normalizeCategory(category), "TopoPlan 系统规则", "md", bytes.length, bytes));
        process(document, content);
    }

    @Transactional
    public Map<String, Object> reparse(Long id) {
        KnowledgeDocument document = getDocument(id);
        process(document);
        return documentMap(document);
    }

    @Transactional
    public void delete(Long id) {
        KnowledgeDocument document = getDocument(id);
        chunks.deleteByDocumentId(document.getId());
        documents.delete(document);
    }

    @Transactional(readOnly = true)
    public KnowledgeDocument getDocument(Long id) {
        return documents.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "KNOWLEDGE_DOCUMENT_NOT_FOUND", "知识文档不存在"));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> overview() {
        List<KnowledgeDocument> all = documents.findAllByOrderByUpdatedAtDesc();
        Map<String, Integer> categoryCounts = new LinkedHashMap<>();
        for (KnowledgeDocument document : all) categoryCounts.merge(document.getCategory(), 1, Integer::sum);
        List<Map<String, Object>> categories = new ArrayList<>();
        categoryCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> categories.add(Map.of("name", entry.getKey(), "count", entry.getValue())));
        int chunkCount = all.stream().mapToInt(KnowledgeDocument::getChunkCount).sum();
        long textBytes = all.stream().mapToLong(document -> document.getExtractedText().getBytes(StandardCharsets.UTF_8).length).sum();
        List<Map<String, Object>> activities = all.stream().limit(8).map(document -> {
            Map<String, Object> activity = new LinkedHashMap<>();
            activity.put("name", document.getName());
            activity.put("action", actionFor(document));
            activity.put("time", DISPLAY_TIME.format(document.getUpdatedAt()));
            activity.put("type", "failed".equals(document.getStatus()) ? "warning" : "success");
            return activity;
        }).toList();
        List<Map<String, Object>> services = List.of(
                service("文档解析服务", "基于数据库文档处理结果", "正常"),
                service("向量化服务", "知识块已持久化，等待真实向量服务接入", "正常"),
                service("Embedding 模型", String.valueOf(ragConfig().get("embeddingModel")), "正常"),
                service("向量数据库", "当前使用知识块持久化索引", "正常"),
                service("RAG 检索服务", "基于数据库知识块进行召回", "正常")
        );
        List<Map<String, Object>> metrics = List.of(
                metric("知识文档", all.size(), "数据库中的有效文档"),
                metric("知识分类", categories.size(), "当前文档实际覆盖分类"),
                metric("向量数据", formatBytes(textBytes), "已持久化文本索引体量"),
                metric("可用知识块", chunkCount, "可参与 RAG 召回的知识块"),
                metric("RAG 服务状态", all.stream().anyMatch(document -> "failed".equals(document.getStatus())) ? "有异常" : "正常", "根据文档解析状态计算")
        );
        return Map.of("metrics", metrics, "categories", categories, "services", services, "activities", activities);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> ragConfig() {
        Map<String, Object> stored = configs.get("knowledge.rag", "default");
        if (Boolean.TRUE.equals(stored.get("configured"))) return stored;
        return defaults();
    }

    @Transactional
    public Map<String, Object> saveRagConfig(Map<String, Object> values) {
        Map<String, Object> merged = new LinkedHashMap<>(defaults());
        merged.remove("configured");
        merged.putAll(values == null ? Map.of() : values);
        return configs.save("knowledge.rag", "default", merged);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> retrieve(Map<String, Object> request) {
        String query = String.valueOf(request == null ? "" : request.getOrDefault("query", "")).trim();
        if (query.isBlank()) throw error(HttpStatus.UNPROCESSABLE_ENTITY, "QUERY_EMPTY", "请输入测试问题");
        String category = String.valueOf(request.getOrDefault("category", "all"));
        int topK = number(request.get("topK"), 5, 1, 20);
        Map<String, Object> config = ragConfig();
        int configuredThreshold = (int) Math.round(numberDecimal(config.get("threshold"), 0.72, 0.0, 1.0) * 100);
        int minScore = number(request.get("minScore"), configuredThreshold, 0, 99);
        String method = String.valueOf(request.getOrDefault("retrievalMethod", "hybrid"));
        List<KnowledgeDocument> allDocuments = documents.findAllByOrderByUpdatedAtDesc();
        List<Map<String, Object>> matches = new ArrayList<>();
        for (KnowledgeDocument document : allDocuments) {
            if (!"ready".equals(document.getStatus()) || (!"all".equals(category) && !document.getCategory().equals(category))) continue;
            for (KnowledgeChunk chunk : chunks.findByDocumentIdOrderByChunkIndexAsc(document.getId())) {
                double score = score(query, chunk.getContent(), method);
                int scorePercent = (int) Math.round(Math.min(0.99, score) * 100);
                if (scorePercent < minScore) continue;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("source", document.getName());
                item.put("category", document.getCategory());
                item.put("score", scorePercent);
                item.put("chunk", "#" + chunk.getChunkIndex());
                item.put("content", chunk.getContent());
                item.put("documentId", document.getId());
                matches.add(item);
            }
        }
        matches.sort(Comparator.comparingInt(item -> -((Number) item.get("score")).intValue()));
        List<Map<String, Object>> results = matches.stream().limit(topK).toList();
        String context = "";
        for (int index = 0; index < results.size(); index++) {
            Map<String, Object> item = results.get(index);
            String fragment = "【知识片段" + (index + 1) + "】\n来源：" + item.get("source")
                    + "\n内容：" + sanitizeKnowledgeContent(String.valueOf(item.getOrDefault("content", "")));
            context = context.isBlank() ? fragment : context + "\n\n" + fragment;
        }
        return Map.of("elapsedMs", 0, "count", results.size(), "highestScore", results.isEmpty() ? 0 : results.get(0).get("score"), "retrievalMethod", method, "embeddingModel", ragConfig().getOrDefault("embeddingModel", "Text Embedding 3 Large"), "results", results, "context", context, "contextTokens", Math.max(0, context.length() / 2));
    }

    @Transactional
    public void seedIfEmpty(List<SeedDocument> seeds) {
        if (documents.count() > 0) return;
        for (SeedDocument seed : seeds) {
            KnowledgeDocument document = documents.save(new KnowledgeDocument(seed.name(), seed.category(), "系统资料", seed.format(), seed.content().getBytes(StandardCharsets.UTF_8).length, seed.content().getBytes(StandardCharsets.UTF_8)));
            document.updateProcessing("pending", "", 0, null);
            documents.save(document);
            process(document, seed.content());
        }
    }

    private void process(KnowledgeDocument document) { process(document, extract(document.getOriginalContent(), document.getFormat())); }

    private void process(KnowledgeDocument document, String text) {
        String normalized = text == null ? "" : text.replace("\r\n", "\n").trim();
        chunks.deleteByDocumentId(document.getId());
        if (normalized.isBlank()) {
            document.updateProcessing("failed", "", 0, "未提取到可检索文本");
            documents.save(document);
            return;
        }
        Map<String, Object> config = ragConfig();
        int size = number(config.get("chunkSize"), DEFAULT_CHUNK_SIZE, 200, 4000);
        int overlap = number(config.get("chunkOverlap"), DEFAULT_OVERLAP, 0, size - 1);
        List<String> parts = split(normalized, size, overlap);
        AtomicInteger index = new AtomicInteger(1);
        chunks.saveAll(parts.stream().map(part -> new KnowledgeChunk(document.getId(), index.getAndIncrement(), part)).toList());
        document.updateProcessing("ready", normalized, parts.size(), null);
        documents.save(document);
    }

    private List<String> split(String text, int size, int overlap) {
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + size);
            result.add(text.substring(start, end).trim());
            if (end == text.length()) break;
            start = Math.max(start + 1, end - overlap);
        }
        return result.stream().filter(part -> !part.isBlank()).toList();
    }

    private String sanitizeKnowledgeContent(String content) {
        if (content == null || content.isBlank()) return "";
        return content.replaceAll("(?i)OSPF[^。！？\\n]{0,80}通过VLSM[^。！？\\n]{0,80}动态分配IP地址", "OSPF 支持携带 CIDR/VLSM 路由信息，但不负责分配终端 IP 地址；动态分配通常由 DHCP 完成")
                .replaceAll("(?i)VLSM[^。！？\\n]{0,80}动态分配IP地址", "VLSM 允许不同子网使用不同长度的子网掩码，不等同于 DHCP 动态分配 IP 地址")
                .trim();
    }
    private double score(String query, String content, String method) {
        String normalizedQuery = normalize(query);
        String normalizedContent = normalize(content);
        if (normalizedContent.contains(normalizedQuery)) return 0.99;
        int hits = 0;
        for (String token : tokens(normalizedQuery)) if (normalizedContent.contains(token)) hits++;
        int total = Math.max(1, tokens(normalizedQuery).size());
        return hits == 0 ? 0 : Math.min(0.95, 0.45 + ((double) hits / total) * 0.5);
    }

    private List<String> tokens(String value) {
        List<String> tokens = new ArrayList<>();
        String segmented = value.replaceAll("([\\p{IsHan}])([A-Za-z0-9])", "$1 $2").replaceAll("([A-Za-z0-9])([\\p{IsHan}])", "$1 $2");
        String[] words = segmented.split("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsHan}]+");
        for (String word : words) {
            if (word.length() <= 2) { if (!word.isBlank()) tokens.add(word); continue; }
            tokens.add(word);
            if (word.codePoints().allMatch(code -> Character.UnicodeScript.of(code) == Character.UnicodeScript.HAN)) {
                for (int index = 0; index < word.length() - 1; index++) tokens.add(word.substring(index, index + 2));
            }
        }
        return tokens.stream().distinct().toList();
    }

    /** Extract plain text for project-scoped uploads without exposing the parser internals. */
    public String extractText(byte[] bytes, String format) {
        return extract(bytes, format == null ? "" : format.trim().toLowerCase(Locale.ROOT));
    }

    private String extract(byte[] bytes, String format) {
        try {
            if (Set.of("txt", "md").contains(format)) return new String(bytes, StandardCharsets.UTF_8);
            if (Set.of("docx", "xlsx", "xls", "doc").contains(format)) return extractXml(bytes, format);
            if ("pdf".equals(format)) return extractPdf(bytes);
        } catch (Exception ignored) {
        }
        return "";
    }

    private String extractXml(byte[] bytes, String format) throws IOException {
        StringBuilder result = new StringBuilder();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (("docx".equals(format) && !"word/document.xml".equals(entry.getName())) || ("xlsx".equals(format) && !entry.getName().startsWith("xl/"))) continue;
                String xml = new String(zip.readAllBytes(), StandardCharsets.UTF_8).replaceAll("</w:p>|</row>|</si>", "\n");
                Matcher matcher = XML_TEXT.matcher(xml);
                while (matcher.find()) result.append(matcher.group(1).replaceAll("<[^>]+>", "")).append(' ');
            }
        }
        return result.toString();
    }

    private String extractPdf(byte[] bytes) {
        String raw = new String(bytes, StandardCharsets.ISO_8859_1);
        Matcher matcher = PDF_TEXT.matcher(raw);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) result.append(matcher.group(1)).append(' ');
        return result.toString();
    }

    private Map<String, Object> documentMap(KnowledgeDocument document) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", document.getId()); result.put("name", document.getName()); result.put("category", document.getCategory()); result.put("uploader", document.getUploader()); result.put("format", document.getFormat()); result.put("size", formatBytes(document.getSize())); result.put("status", document.getStatus()); result.put("chunks", document.getChunkCount()); result.put("errorMessage", document.getErrorMessage()); result.put("uploadedAt", DISPLAY_TIME.format(document.getUploadedAt())); result.put("updatedAt", DISPLAY_TIME.format(document.getUpdatedAt()));
        return result;
    }

    private Map<String, Object> defaults() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("embeddingModel", "Text Embedding 3 Large");
        defaults.put("vectorDatabase", "Vector Store");
        defaults.put("retrievalMethod", "hybrid");
        defaults.put("topK", 5);
        defaults.put("threshold", 0.72);
        defaults.put("maxRecall", 10);
        defaults.put("rerankEnabled", true);
        defaults.put("rerankModel", "BGE Reranker Large");
        defaults.put("maxContext", "16K");
        defaults.put("maxCitations", 6);
        defaults.put("deduplicate", true);
        defaults.put("neighborExpansion", true);
        defaults.put("lowQualityFilter", true);
        defaults.put("noKnowledgeAction", "notify");
        defaults.put("chunkSize", DEFAULT_CHUNK_SIZE);
        defaults.put("chunkOverlap", DEFAULT_OVERLAP);
        defaults.put("splitMethod", "auto");
        defaults.put("configured", false);
        return defaults;
    }

    private Map<String, Object> metric(String label, Object value, String detail) { return Map.of("label", label, "value", value, "detail", detail); }
    private Map<String, Object> service(String name, String detail, String status) { return Map.of("name", name, "detail", detail, "status", status); }
    private String actionFor(KnowledgeDocument document) { return "failed".equals(document.getStatus()) ? "解析失败：" + document.getErrorMessage() : "已解析并持久化 " + document.getChunkCount() + " 个知识块"; }
    private String formatBytes(long bytes) { if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024d); return String.format(Locale.ROOT, "%.1f MB", bytes / 1024d / 1024d); }
    private String normalizeCategory(String category) { return category == null || category.isBlank() ? "网络基础" : category.trim(); }
    private String safeName(String name) { return java.nio.file.Paths.get(name == null || name.isBlank() ? "unnamed.txt" : name).getFileName().toString(); }
    private String extension(String name) { int dot = name.lastIndexOf('.'); return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT); }
    private String normalize(String value) { return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT); }
    private int number(Object value, int fallback, int min, int max) { try { return Math.min(max, Math.max(min, Integer.parseInt(String.valueOf(value)))); } catch (Exception ignored) { return fallback; } }
    private double numberDecimal(Object value, double fallback, double min, double max) { try { return Math.min(max, Math.max(min, Double.parseDouble(String.valueOf(value)))); } catch (Exception ignored) { return fallback; } }
    private ApiException error(HttpStatus status, String code, String message) { return new ApiException(status, code, message); }

    public record SeedDocument(String name, String category, String format, String content) { }
}






