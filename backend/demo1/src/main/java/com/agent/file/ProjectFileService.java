package com.agent.file;

import com.agent.common.api.ApiException;
import com.agent.knowledge.KnowledgeBaseService;
import com.agent.project.Project;
import com.agent.project.ProjectService;
import com.agent.subscription.SubscriptionService;
import com.agent.user.User;
import com.agent.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ProjectFileService {
    public static final long MAX_SIZE = 10L * 1024 * 1024;
    public static final int MAX_FILES_PER_PROJECT = 10;
    public static final long MAX_TOTAL_SIZE = 50L * 1024 * 1024;
    // These formats have a deterministic server-side text extractor. Legacy
    // binary .doc/.xls files are rejected instead of being stored unreadable.
    private static final Set<String> ALLOWED = Set.of("pdf", "docx", "xlsx", "txt", "md");

    private final ProjectFileRepository fileRepository;
    private final ProjectService projectService;
    private final KnowledgeBaseService knowledgeBase;
    private final UserRepository users;
    private final SubscriptionService subscriptions;
    private final Path uploadDirectory;

    public ProjectFileService(ProjectFileRepository fileRepository, ProjectService projectService,
                               KnowledgeBaseService knowledgeBase,
                               UserRepository users,
                               SubscriptionService subscriptions,
                               @Value("${topo.upload-dir:uploads}") String uploadDirectory) {
        this.fileRepository = fileRepository;
        this.projectService = projectService;
        this.knowledgeBase = knowledgeBase;
        this.users = users;
        this.subscriptions = subscriptions;
        this.uploadDirectory = Paths.get(uploadDirectory).toAbsolutePath().normalize();
    }

    @Transactional
    public ProjectFileResponse upload(Long projectId, Long ownerId, MultipartFile multipartFile) {
        Project project = projectService.getOwned(projectId, ownerId);
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "FILE_EMPTY", "上传文件不能为空");
        }
        List<ProjectFile> existing = fileRepository.findByProjectIdAndOwnerIdOrderByCreatedAtDesc(projectId, ownerId);
        UploadLimits limits = limitsFor(ownerId);
        if (existing.size() >= limits.maxFiles()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "FILE_LIMIT_EXCEEDED", "当前套餐每个项目最多上传 " + limits.maxFiles() + " 份资料");
        }
        if (multipartFile.getSize() > limits.maxSingleSize()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "FILE_TOO_LARGE", "单个文件不能超过 " + (limits.maxSingleSize() / (1024 * 1024)) + " MB");
        }
        long totalSize = existing.stream().mapToLong(ProjectFile::getSize).sum();
        if (totalSize + multipartFile.getSize() > limits.maxTotalSize()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PROJECT_FILE_STORAGE_LIMIT", "项目资料总容量不能超过 " + (limits.maxTotalSize() / (1024 * 1024)) + " MB");
        }
        String name = Paths.get(multipartFile.getOriginalFilename() == null ? "unnamed" : multipartFile.getOriginalFilename())
                .getFileName().toString();
        String format = extension(name);
        if (!ALLOWED.contains(format)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "FILE_TYPE_UNSUPPORTED", "仅支持 PDF、DOCX、XLSX、TXT、Markdown 文件");
        }
        String storageName = UUID.randomUUID() + "." + format;
        byte[] content;
        String extractedText;
        try {
            content = multipartFile.getBytes();
            extractedText = knowledgeBase.extractText(content, format);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "FILE_READ_FAILED", "无法读取上传资料");
        }
        String normalizedText = extractedText == null ? "" : extractedText.replace("\r\n", "\n").trim();
        String status = normalizedText.isBlank() ? "读取失败" : "已读取";
        String errorMessage = normalizedText.isBlank() ? "未提取到可检索的正文内容" : null;
        try {
            Files.createDirectories(uploadDirectory);
            Files.write(uploadDirectory.resolve(storageName), content);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_STORAGE_FAILED", "文件保存失败");
        }
        ProjectFile saved = fileRepository.save(new ProjectFile(projectId, ownerId, name, format, multipartFile.getSize(), status, storageName, normalizedText, errorMessage));
        project.updateFileCount(project.getFileCount() + 1);
        return ProjectFileResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ProjectFileResponse> list(Long projectId, Long ownerId) {
        projectService.getOwned(projectId, ownerId);
        return fileRepository.findByProjectIdAndOwnerIdOrderByCreatedAtDesc(projectId, ownerId).stream()
                .map(ProjectFileResponse::from).toList();
    }

    /** Build a bounded, query-aware context from project materials for the AI assistant. */
    @Transactional(readOnly = true)
    public String context(Long projectId, Long ownerId, List<Long> requestedIds, String query) {
        projectService.getOwned(projectId, ownerId);
        Set<Long> selected = requestedIds == null ? Set.of() : requestedIds.stream().filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        String normalizedQuery = normalize(query);
        List<ProjectFile> readable = fileRepository.findByProjectIdAndOwnerIdOrderByCreatedAtDesc(projectId, ownerId).stream()
                .filter(ProjectFile::hasReadableText).toList();
        List<ProjectFile> ranked = new ArrayList<>(readable);
        ranked.sort(Comparator.comparingInt((ProjectFile file) -> selected.contains(file.getId()) ? 10_000 : score(file.getExtractedText(), normalizedQuery)).reversed()
                .thenComparing(ProjectFile::getCreatedAt, Comparator.reverseOrder()));
        StringBuilder context = new StringBuilder();
        int used = 0;
        for (ProjectFile file : ranked) {
            if (!selected.isEmpty() && !selected.contains(file.getId()) && used >= 2) continue;
            String text = file.getExtractedText();
            if (text.isBlank()) continue;
            int remaining = 12_000 - used;
            if (remaining <= 120) break;
            String excerpt = text.length() > Math.min(4_000, remaining) ? text.substring(0, Math.min(4_000, remaining)) + "…" : text;
            String block = "【项目资料：" + file.getName() + "】\n" + excerpt.trim();
            if (context.length() > 0) context.append("\n\n");
            context.append(block);
            used += block.length();
        }
        return context.toString();
    }

    private int score(String content, String query) {
        if (query.isBlank()) return 0;
        String source = normalize(content);
        int hits = 0;
        for (String token : query.split("\\s+")) if (token.length() > 1 && source.contains(token)) hits++;
        return hits;
    }

    private String normalize(String value) {
        return java.text.Normalizer.normalize(value == null ? "" : value, java.text.Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private UploadLimits limitsFor(Long ownerId) {
        long maxFiles = MAX_FILES_PER_PROJECT;
        long maxSingle = MAX_SIZE;
        long maxTotal = MAX_TOTAL_SIZE;
        try {
            User user = users.findById(ownerId).orElse(null);
            if (user != null) {
                var subscription = subscriptions.subscriptionFor(user);
                var quota = (java.util.Map<?, ?>) subscription.get("quota");
                maxFiles = Math.min(maxFiles, quotaNumber(quota, "fileUploads", MAX_FILES_PER_PROJECT));
                maxSingle = Math.min(maxSingle, quotaNumber(quota, "singleFileSizeMb", MAX_SIZE / (1024 * 1024)) * 1024 * 1024);
                maxTotal = Math.min(maxTotal, quotaNumber(quota, "knowledgeCapacityMb", MAX_TOTAL_SIZE / (1024 * 1024)) * 1024 * 1024);
            }
        } catch (Exception ignored) {
            // Keep the system hard limits if subscription data is unavailable.
        }
        return new UploadLimits(Math.max(1, maxFiles), Math.max(1, maxSingle), Math.max(1, maxTotal));
    }

    private long quotaNumber(java.util.Map<?, ?> quota, String key, long fallback) {
        Object value = quota == null ? null : quota.get(key);
        if (value instanceof java.util.Map<?, ?> map) value = map.get("total");
        try { return value == null ? fallback : Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private record UploadLimits(long maxFiles, long maxSingleSize, long maxTotalSize) {}
}
