package com.agent.project;

import com.agent.common.api.ApiException;
import com.agent.topology.TopologyDocument;
import com.agent.topology.TopologyDocumentRepository;
import com.agent.topology.TopologyVersion;
import com.agent.topology.TopologyVersionRepository;
import com.agent.ai.AiConversation;
import com.agent.ai.AiConversationMessageRepository;
import com.agent.ai.AiConversationRepository;
import com.agent.chat.ChatMessageRepository;
import com.agent.chat.ChatSession;
import com.agent.chat.ChatSessionRepository;
import com.agent.file.ProjectFileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final TopologyDocumentRepository topologyDocumentRepository;
    private final TopologyVersionRepository topologyVersionRepository;
    private final ObjectMapper objectMapper;
    private final ProjectFileRepository projectFileRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final AiConversationRepository aiConversationRepository;
    private final AiConversationMessageRepository aiConversationMessageRepository;

    public ProjectService(ProjectRepository projectRepository, TopologyDocumentRepository topologyDocumentRepository,
                          TopologyVersionRepository topologyVersionRepository, ObjectMapper objectMapper,
                          ProjectFileRepository projectFileRepository, ChatSessionRepository chatSessionRepository,
                          ChatMessageRepository chatMessageRepository, AiConversationRepository aiConversationRepository,
                          AiConversationMessageRepository aiConversationMessageRepository) {
        this.projectRepository = projectRepository;
        this.topologyDocumentRepository = topologyDocumentRepository;
        this.topologyVersionRepository = topologyVersionRepository;
        this.objectMapper = objectMapper;
        this.projectFileRepository = projectFileRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.aiConversationRepository = aiConversationRepository;
        this.aiConversationMessageRepository = aiConversationMessageRepository;
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> list(Long ownerId) {
        return projectRepository.findByOwnerIdOrderByUpdatedAtDesc(ownerId).stream().map(ProjectResponse::from).toList();
    }

    @Transactional
    public ProjectResponse create(Long ownerId, CreateProjectRequest request) {
        String type = normalize(request.type(), "topo_design");
        String color = normalize(request.color(), "#1a73e8");
        String creationMode = "ai".equalsIgnoreCase(normalize(request.creationMode(), "manual")) ? "ai" : "manual";
        String clientRequestId = normalize(request.clientRequestId(), "");
        if (!clientRequestId.isBlank()) {
            var existing = projectRepository.findByOwnerIdAndCreationRequestId(ownerId, clientRequestId);
            if (existing.isPresent()) return ProjectResponse.from(existing.get());
        }
        Project project = new Project(ownerId, request.name().trim(), type, color, creationMode, normalize(request.description(), ""));
        if (!clientRequestId.isBlank()) project.setCreationRequestId(clientRequestId);
        return ProjectResponse.from(projectRepository.save(project));
    }

    @Transactional(readOnly = true)
    public Project getOwned(Long id, Long ownerId) {
        return projectRepository.findByIdAndOwnerId(id, ownerId)
                .orElseGet(() -> {
                    throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_AVAILABLE", "请求的资源不存在或无法访问");
                });
    }

    @Transactional(readOnly = true)
    public Project getOwned(String publicOrLegacyId, Long ownerId) {
        String key = publicOrLegacyId == null ? "" : publicOrLegacyId.trim();
        if (key.isBlank()) throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_AVAILABLE", "请求的资源不存在或无法访问");
        try {
            Project project = projectRepository.findById(Long.valueOf(key)).orElseThrow(() ->
                    new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_AVAILABLE", "请求的资源不存在或无法访问"));
            if (!ownerId.equals(project.getOwnerId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "PROJECT_FORBIDDEN", "无权访问该项目");
            }
            return project;
        } catch (NumberFormatException ignored) {
            Project project = projectRepository.findByPublicId(key).orElseThrow(() ->
                    new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_AVAILABLE", "请求的资源不存在或无法访问"));
            if (!ownerId.equals(project.getOwnerId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "PROJECT_FORBIDDEN", "无权访问该项目");
            }
            return project;
        }
    }

    @Transactional(readOnly = true)
    public Long resolveOwnedId(String publicOrLegacyId, Long ownerId) {
        return getOwned(publicOrLegacyId, ownerId).getId();
    }

    @Transactional
    public ProjectResponse update(Long id, Long ownerId, UpdateProjectRequest request) {
        Project project = getOwned(id, ownerId);
        if (request.name() != null && (request.name().trim().isEmpty() || request.name().trim().length() > 50)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROJECT_NAME_INVALID", "项目名称长度必须为1到50个字符");
        }
        String creationMode = request.creationMode() == null || request.creationMode().isBlank()
                ? null
                : ("ai".equalsIgnoreCase(request.creationMode().trim()) ? "ai" : "manual");
        project.update(request.name(), request.type(), request.color(), request.status(), request.terminals(), creationMode, request.description());
        return ProjectResponse.from(projectRepository.save(project));
    }

    @Transactional
    public ProjectResponse duplicate(Long id, Long ownerId) {
        Project source = getOwned(id, ownerId);
        String copyName = nextCopyName(ownerId, source.getName());
        Project copy = new Project(ownerId, copyName, source.getType(), source.getColor(), source.getCreationMode(), source.getDescription());
        copy.update(null, null, null, source.getStatus(), source.getTerminals(), source.getCreationMode(), source.getDescription());
        Project saved = projectRepository.save(copy);

        topologyDocumentRepository.findByProjectIdAndOwnerId(id, ownerId).ifPresent(document -> {
            JsonTopology topology = remapTopology(document.getNodesJson(), document.getEdgesJson());
            TopologyDocument copiedDocument = new TopologyDocument(saved.getId(), ownerId, topology.nodes(), topology.edges(), document.getZoom(), document.getVersion());
            topologyDocumentRepository.save(copiedDocument);
            topologyVersionRepository.save(new TopologyVersion(saved.getId(), ownerId, document.getVersion(), topology.nodes(), topology.edges(), document.getZoom()));
            saved.updateTopologyVersion("V" + document.getVersion());
        });
        return ProjectResponse.from(projectRepository.save(saved));
    }

    private String nextCopyName(Long ownerId, String originalName) {
        String base = originalName + " - 副本";
        List<String> names = projectRepository.findByOwnerIdOrderByUpdatedAtDesc(ownerId).stream().map(Project::getName).toList();
        if (!names.contains(base)) return base;
        int suffix = 2;
        while (names.contains(base + " " + suffix)) suffix += 1;
        return base + " " + suffix;
    }

    private JsonTopology remapTopology(String nodesJson, String edgesJson) {
        try {
            JsonNode nodes = objectMapper.readTree(nodesJson);
            JsonNode edges = objectMapper.readTree(edgesJson);
            Map<String, String> ids = new HashMap<>();
            ArrayNode copiedNodes = objectMapper.createArrayNode();
            if (nodes != null && nodes.isArray()) {
                for (JsonNode node : nodes) {
                    ObjectNode copy = node.deepCopy();
                    String oldId = copy.path("id").asText("");
                    String newId = UUID.randomUUID().toString();
                    if (!oldId.isBlank()) ids.put(oldId, newId);
                    copy.put("id", newId);
                    copiedNodes.add(copy);
                }
            }
            ArrayNode copiedEdges = objectMapper.createArrayNode();
            if (edges != null && edges.isArray()) {
                for (JsonNode edge : edges) {
                    ObjectNode copy = edge.deepCopy();
                    copy.put("id", UUID.randomUUID().toString());
                    String source = ids.get(copy.path("source").asText(""));
                    String target = ids.get(copy.path("target").asText(""));
                    if (source != null) copy.put("source", source);
                    if (target != null) copy.put("target", target);
                    copiedEdges.add(copy);
                }
            }
            return new JsonTopology(objectMapper.writeValueAsString(copiedNodes), objectMapper.writeValueAsString(copiedEdges));
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PROJECT_DUPLICATE_FAILED", "项目拓扑复制失败");
        }
    }

    private record JsonTopology(String nodes, String edges) {}

    @Transactional
    public void delete(Long id, Long ownerId) {
        getOwned(id, ownerId);
        for (ChatSession session : chatSessionRepository.findByProjectIdAndOwnerIdOrderByUpdatedAtDesc(id, ownerId)) {
            chatMessageRepository.deleteAll(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()));
        }
        chatSessionRepository.deleteAll(chatSessionRepository.findByProjectIdAndOwnerIdOrderByUpdatedAtDesc(id, ownerId));
        for (AiConversation conversation : aiConversationRepository.findByProjectIdAndUserIdOrderByUpdatedAtDesc(id, ownerId)) {
            aiConversationMessageRepository.deleteAll(aiConversationMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId()));
        }
        aiConversationRepository.deleteAll(aiConversationRepository.findByProjectIdAndUserIdOrderByUpdatedAtDesc(id, ownerId));
        projectFileRepository.deleteAll(projectFileRepository.findByProjectIdAndOwnerIdOrderByCreatedAtDesc(id, ownerId));
        topologyVersionRepository.deleteByProjectIdAndOwnerId(id, ownerId);
        topologyDocumentRepository.deleteByProjectIdAndOwnerId(id, ownerId);
        projectRepository.deleteById(id);
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
