package com.agent.topology;

import com.agent.project.Project;
import com.agent.project.ProjectService;
import com.agent.common.api.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class TopologyService {
    private final TopologyDocumentRepository documentRepository;
    private final TopologyVersionRepository versionRepository;
    private final ProjectService projectService;
    private final ObjectMapper objectMapper;
    private final TopologyResourceService resourceService;

    public TopologyService(TopologyDocumentRepository documentRepository, TopologyVersionRepository versionRepository,
                           ProjectService projectService, ObjectMapper objectMapper, TopologyResourceService resourceService) {
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.projectService = projectService;
        this.objectMapper = objectMapper;
        this.resourceService = resourceService;
    }

    @Transactional(readOnly = true)
    public TopologyResponse get(Long projectId, Long ownerId) {
        projectService.getOwned(projectId, ownerId);
        return documentRepository.findByProjectIdAndOwnerId(projectId, ownerId)
                .map(document -> TopologyResponse.from(document, objectMapper))
                .orElseGet(TopologyResponse::empty);
    }

    @Transactional
    public TopologyResponse save(Long projectId, Long ownerId, TopologyRequest request) {
        Project project = projectService.getOwned(projectId, ownerId);
        int nextVersion = documentRepository.findByProjectIdAndOwnerId(projectId, ownerId).map(TopologyDocument::getVersion).orElse(0) + 1;
        String nodesJson = request.nodes().toString();
        String edgesJson = request.edges().toString();
        TopologyDocument document = documentRepository.findByProjectIdAndOwnerId(projectId, ownerId)
                .orElseGet(() -> new TopologyDocument(projectId, ownerId, nodesJson, edgesJson, request.zoom(), nextVersion));
        document.update(nodesJson, edgesJson, request.zoom(), nextVersion);
        documentRepository.save(document);
        versionRepository.save(new TopologyVersion(projectId, ownerId, nextVersion, nodesJson, edgesJson, request.zoom()));
        project.updateTopologyVersion("V" + nextVersion);
        return TopologyResponse.from(document, objectMapper);
    }

    @Transactional(readOnly = true)
    public List<TopologyVersionResponse> versions(Long projectId, Long ownerId) {
        projectService.getOwned(projectId, ownerId);
        return versionRepository.findByProjectIdAndOwnerIdOrderByVersionDesc(projectId, ownerId).stream()
                .map(TopologyVersionResponse::from).toList();
    }

    @Transactional
    public TopologyResponse applyPlan(Long projectId, Long ownerId, JsonNode plan) {
        Project project = projectService.getOwned(projectId, ownerId);
        TopologyDocument current = documentRepository.findByProjectIdAndOwnerId(projectId, ownerId).orElse(null);
        ArrayNode nodes = objectMapper.createArrayNode();
        ArrayNode edges = objectMapper.createArrayNode();
        try {
            if (plan == null || !plan.isObject()) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TOPOLOGY_PLAN_INVALID", "拓扑规划内容无效");
            }
            if (current != null) {
                JsonNode existingNodes = objectMapper.readTree(current.getNodesJson());
                JsonNode existingEdges = objectMapper.readTree(current.getEdgesJson());
                if (existingNodes != null && existingNodes.isArray()) existingNodes.forEach(nodes::add);
                if (existingEdges != null && existingEdges.isArray()) existingEdges.forEach(edges::add);
            }
            Map<String, String> idMap = new java.util.HashMap<>();
            Set<String> addedNodeIds = new LinkedHashSet<>();
            JsonNode addNodes = firstArray(plan, "addNodes", "nodesToAdd", "nodes");
            if (addNodes != null && addNodes.isArray()) for (JsonNode raw : addNodes) {
                if (!raw.isObject()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TOPOLOGY_PLAN_NODE_INVALID", "规划中的设备节点格式无效");
                ObjectNode item = raw.deepCopy();
                String requestedId = firstText(item, "id", "tempId", "nodeId");
                String id = requestedId.isBlank() || containsNode(nodes, requestedId) ? java.util.UUID.randomUUID().toString() : requestedId;
                if (!requestedId.isBlank()) idMap.put(requestedId, id);
                item.put("id", id);
                item.remove("tempId");
                String type = firstText(item, "type", "deviceType", "resourceKey").replace('_', '-').toLowerCase(java.util.Locale.ROOT);
                String requestedResourceId = firstText(item, "resourceId", "resourceKey", "type", "deviceType");
                if (type.isBlank() || requestedResourceId.isBlank()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TOPOLOGY_PLAN_RESOURCE_INVALID", "规划中缺少设备类型");
                Map<String, Object> resource = resourceService.getPublicResource(requestedResourceId);
                if (!Boolean.TRUE.equals(resource.get("enabled")) || !Boolean.TRUE.equals(resource.get("allowAi"))) throw new ApiException(HttpStatus.CONFLICT, "TOPOLOGY_RESOURCE_AI_DISABLED", "该设备资源当前未启用或不允许用于 AI 规划");
                String resourceType = String.valueOf(resource.getOrDefault("type", "")).trim().replace('_', '-').toLowerCase(java.util.Locale.ROOT);
                if (resourceType.isBlank()) resourceType = type;
                // The resource identifier is the authoritative whitelist key.
                // Display labels from a model may differ from the internal
                // type, so canonicalize to the configured library type rather
                // than rejecting an otherwise valid selected resource.
                item.put("type", resourceType);
                item.put("resourceId", resource.getOrDefault("resourceId", resource.getOrDefault("key", requestedResourceId)).toString());
                item.put("resourceKey", resource.getOrDefault("key", resourceType).toString());
                item.put("name", uniqueNodeName(nodes, firstText(item, "name"), resource.getOrDefault("name", resourceType).toString()));
                item.set("ports", objectMapper.valueToTree(resource.get("ports")));
                int nodeIndex = nodes.size();
                item.put("x", coordinate(item, "x", 180 + (nodeIndex % 4) * 220));
                item.put("y", coordinate(item, "y", 120 + layerRank(firstText(item, "layer", "role")) * 150 + (nodeIndex / 4) * 22));
                nodes.add(item);
                addedNodeIds.add(id);
            }
            applyNodeUpdates(nodes, firstArray(plan, "updateNodes", "nodesToUpdate"), idMap);
            if (plan != null && plan.path("allowDestructive").asBoolean(false)) {
                JsonNode removeNodes = firstArray(plan, "removeNodes", "nodesToRemove");
                if (removeNodes != null && removeNodes.isArray()) {
                    Set<String> remove = new HashSet<>();
                    removeNodes.forEach(item -> {
                        String requested = item.isTextual() ? item.asText() : firstText(item, "id", "tempId", "nodeId");
                        if (!requested.isBlank()) remove.add(idMap.getOrDefault(requested, requested));
                    });
                    ArrayNode kept = objectMapper.createArrayNode();
                    for (JsonNode item : nodes) if (!remove.contains(text(item, "id"))) kept.add(item);
                    nodes = kept;
                    ArrayNode keptEdges = objectMapper.createArrayNode();
                    for (JsonNode item : edges) if (!remove.contains(text(item, "source")) && !remove.contains(text(item, "target"))) keptEdges.add(item);
                    edges = keptEdges;
                }
            }
            JsonNode addEdges = firstArray(plan, "addEdges", "edgesToAdd", "connections", "edges");
            if (addEdges != null && addEdges.isArray()) for (JsonNode raw : addEdges) {
                if (!raw.isObject()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TOPOLOGY_PLAN_EDGE_INVALID", "规划中的连接格式无效");
                ObjectNode item = raw.deepCopy();
                String rawSource = endpointRef(item, "source", "from", "sourceDevice");
                String rawTarget = endpointRef(item, "target", "to", "targetDevice");
                String source = resolveNodeReference(nodes, idMap.getOrDefault(rawSource, rawSource));
                String target = resolveNodeReference(nodes, idMap.getOrDefault(rawTarget, rawTarget));
                if (source.isBlank() || target.isBlank() || source.equals(target) || !containsNode(nodes, source) || !containsNode(nodes, target)) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TOPOLOGY_PLAN_EDGE_INVALID", "规划中存在无效连接");
                item.put("source", source); item.put("target", target);
                String edgeId = firstText(item, "id", "edgeId");
                item.put("id", edgeId.isBlank() ? java.util.UUID.randomUUID().toString() : edgeId);
                item.put("sourcePort", allocatePort(nodes, edges, source, firstText(item, "sourcePort", "fromPort")));
                item.put("targetPort", allocatePort(nodes, edges, target, firstText(item, "targetPort", "toPort")));
                item.put("connectionType", firstText(item, "connectionType", "linkType", "type").isBlank() ? "千兆以太网" : firstText(item, "connectionType", "linkType", "type"));
                if (!item.has("aggregationGroup") && plan.path("aggregation").path("enabled").asBoolean(false)) {
                    String group = firstText(plan.path("aggregation"), "groupName", "name", "id");
                    if (!group.isBlank()) item.put("aggregationGroup", group);
                }
                if (text(item, "sourcePort").isBlank() || text(item, "targetPort").isBlank()
                        || !hasPort(nodes, source, text(item, "sourcePort")) || !hasPort(nodes, target, text(item, "targetPort"))
                        || !isPortFree(edges, source, text(item, "sourcePort")) || !isPortFree(edges, target, text(item, "targetPort"))) throw new ApiException(HttpStatus.CONFLICT, "TOPOLOGY_PORT_CAPACITY_EXCEEDED", "规划需要的端口已用尽");
                if (!portExists(nodes, source, text(item, "sourcePort")) || !portExists(nodes, target, text(item, "targetPort"))) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TOPOLOGY_PLAN_PORT_INVALID", "规划使用了不存在的物理端口");
                if (!portAvailable(edges, source, text(item, "sourcePort")) || !portAvailable(edges, target, text(item, "targetPort"))) throw new ApiException(HttpStatus.CONFLICT, "TOPOLOGY_PORT_CAPACITY_EXCEEDED", "规划使用的物理端口已被占用");
                if (hasPhysicalEdge(edges, source, target, text(item, "sourcePort"), text(item, "targetPort"))) throw new ApiException(HttpStatus.CONFLICT, "TOPOLOGY_PLAN_DUPLICATE_EDGE", "规划包含重复物理连接");
                edges.add(item);
            }
            applyEdgeUpdates(edges, firstArray(plan, "updateEdges", "edgesToUpdate"), nodes, idMap);
            if (plan.path("allowDestructive").asBoolean(false)) removeEdges(edges, firstArray(plan, "removeEdges", "edgesToRemove"), nodes, idMap);
            validateEdges(nodes, edges);
            validateNewNodeConnectivity(nodes, edges, addedNodeIds, plan.path("allowIsolatedNodes").asBoolean(false));
            int nextVersion = current == null ? 1 : current.getVersion() + 1;
            String nodesJson = nodes.toString();
            String edgesJson = edges.toString();
            TopologyDocument document = current == null ? new TopologyDocument(projectId, ownerId, nodesJson, edgesJson, 1, nextVersion) : current;
            document.update(nodesJson, edgesJson, current == null ? 1 : current.getZoom(), nextVersion);
            documentRepository.save(document);
            versionRepository.save(new TopologyVersion(projectId, ownerId, nextVersion, nodesJson, edgesJson, document.getZoom()));
            project.updateTopologyVersion("V" + nextVersion);
            return TopologyResponse.from(document, objectMapper);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TOPOLOGY_PLAN_INVALID", "拓扑规划无法应用");
        }
    }

    private JsonNode firstArray(JsonNode object, String... names) {
        if (object == null || !object.isObject()) return null;
        JsonNode first = null;
        for (String name : names) {
            JsonNode value = object.get(name);
            if (value == null || !value.isArray()) continue;
            if (first == null) first = value;
            if (value.size() > 0) return value;
        }
        return first;
    }
    private String firstText(JsonNode object, String... names) {
        if (object == null || !object.isObject()) return "";
        for (String name : names) {
            JsonNode value = object.get(name);
            if (value == null || value.isNull()) continue;
            if (value.isValueNode()) {
                String candidate = value.asText("").trim();
                if (!candidate.isBlank()) return candidate;
            } else if (value.isObject()) {
                String nested = firstText(value, "id", "tempId", "nodeId", "deviceId", "name", "key");
                if (!nested.isBlank()) return nested;
            }
        }
        return "";
    }
    private String endpointRef(JsonNode edge, String primary, String... aliases) {
        String value = firstText(edge, primary);
        return value.isBlank() ? firstText(edge, aliases) : value;
    }
    private String resolveNodeReference(ArrayNode nodes, String requested) {
        if (requested == null || requested.isBlank()) return "";
        if (containsNode(nodes, requested)) return requested;
        for (JsonNode node : nodes) {
            String id = text(node, "id");
            if (requested.equalsIgnoreCase(text(node, "name"))
                    || requested.equalsIgnoreCase(text(node, "resourceId"))
                    || requested.equalsIgnoreCase(text(node, "resourceKey"))) return id;
        }
        return requested;
    }
    private String allocatePort(ArrayNode nodes, ArrayNode edges, String nodeId, String requested) {
        if (!requested.isBlank() && portExists(nodes, nodeId, requested) && portAvailable(edges, nodeId, requested)) return requested;
        String fallback = firstFreePort(nodes, edges, nodeId);
        if (fallback.isBlank()) throw new ApiException(HttpStatus.CONFLICT, "TOPOLOGY_PORT_CAPACITY_EXCEEDED", "规划需要的端口已用尽");
        return fallback;
    }
    private double coordinate(JsonNode item, String key, double fallback) {
        JsonNode value = item.get(key);
        if (value == null || value.isNull()) {
            JsonNode properties = item.get("properties");
            value = properties == null ? null : properties.get(key);
        }
        if (value == null || value.isNull()) return fallback;
        if (value.isNumber()) return value.asDouble();
        try { return Double.parseDouble(value.asText()); } catch (NumberFormatException ignored) { return fallback; }
    }
    private int layerRank(String layer) {
        String value = layer == null ? "" : layer.toUpperCase(Locale.ROOT);
        if (value.contains("EDGE") || value.contains("INTERNET") || value.contains("WAN")) return 0;
        if (value.contains("FIREWALL") || value.contains("ROUTER")) return 1;
        if (value.contains("CORE")) return 2;
        if (value.contains("AGGREG") || value.contains("DISTRIB")) return 3;
        if (value.contains("ACCESS")) return 4;
        if (value.contains("TERMINAL") || value.contains("SERVER") || value.contains("WIRELESS")) return 5;
        return 2;
    }
    private String uniqueNodeName(ArrayNode nodes, String requested, String fallback) {
        String base = requested == null || requested.isBlank() ? fallback : requested.trim();
        if (!containsNodeName(nodes, base)) return base;
        int suffix = 2;
        while (containsNodeName(nodes, base + "-" + suffix)) suffix++;
        return base + "-" + suffix;
    }
    private boolean containsNodeName(ArrayNode nodes, String name) {
        for (JsonNode node : nodes) if (name.equalsIgnoreCase(text(node, "name"))) return true;
        return false;
    }
    private void validateNewNodeConnectivity(ArrayNode nodes, ArrayNode edges, Set<String> addedNodeIds, boolean allowIsolated) {
        if (allowIsolated || addedNodeIds.isEmpty()) return;
        Set<String> connected = new HashSet<>();
        for (JsonNode edge : edges) { connected.add(text(edge, "source")); connected.add(text(edge, "target")); }
        for (String id : addedNodeIds) if (!connected.contains(id)) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TOPOLOGY_PLAN_NODE_ISOLATED", "规划中存在未连接的新增设备");
    }
    private boolean containsNode(ArrayNode nodes, String id) { for (JsonNode node : nodes) if (id.equals(text(node, "id"))) return true; return false; }
    private JsonNode findNode(ArrayNode nodes, String id) { for (JsonNode node : nodes) if (id.equals(text(node, "id"))) return node; return null; }
    private void applyNodeUpdates(ArrayNode nodes, JsonNode updates, Map<String, String> idMap) {
        if (updates == null || !updates.isArray()) return;
        Set<String> mutable = Set.of("name", "x", "y", "ip", "area", "vlan", "model", "notes", "ports");
        for (JsonNode raw : updates) {
            String requested = firstText(raw, "id", "tempId", "nodeId");
            String id = idMap.getOrDefault(requested, requested);
            ObjectNode target = findNode(nodes, id) instanceof ObjectNode object ? object : null;
            if (target == null) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TOPOLOGY_PLAN_NODE_INVALID", "规划更新了不存在的设备");
            java.util.Iterator<String> fields = raw.fieldNames();
            while (fields.hasNext()) {
                String field = fields.next();
                if (mutable.contains(field)) target.set(field, raw.get(field).deepCopy());
            }
        }
    }
    private void applyEdgeUpdates(ArrayNode edges, JsonNode updates, ArrayNode nodes, Map<String, String> idMap) {
        if (updates == null || !updates.isArray()) return;
        Set<String> mutable = Set.of("source", "target", "sourcePort", "targetPort", "connectionType", "aggregationGroup", "aggregationId", "notes");
        for (JsonNode raw : updates) {
            String requestedId = firstText(raw, "id", "edgeId", "connectionId");
            ObjectNode target = findMatchingEdge(edges, raw, nodes, idMap);
            if (target == null) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TOPOLOGY_PLAN_EDGE_INVALID", "规划更新了不存在的连接");
            // A source/target tuple without a valid edge id is a locator, not
            // a request to rewire that edge. This prevents an AI-generated
            // descriptive tuple from accidentally changing the connection.
            boolean matchedById = !requestedId.isBlank() && requestedId.equals(text(target, "id"));
            if (raw.has("source") || raw.has("sourceDevice")) {
                String endpoint = endpointRef(raw, "source", "sourceDevice");
                if (matchedById) target.put("source", resolveNodeReference(nodes, idMap.getOrDefault(endpoint, endpoint)));
            }
            if (raw.has("target") || raw.has("targetDevice")) {
                String endpoint = endpointRef(raw, "target", "targetDevice");
                if (matchedById) target.put("target", resolveNodeReference(nodes, idMap.getOrDefault(endpoint, endpoint)));
            }
            java.util.Iterator<String> fields = raw.fieldNames();
            while (fields.hasNext()) {
                String field = fields.next();
                if (!mutable.contains(field)) continue;
                JsonNode value = raw.get(field);
                if ("source".equals(field) || "target".equals(field)) {
                    if (!matchedById) continue;
                    String endpoint = endpointRef(raw, field, "source".equals(field) ? "sourceDevice" : "targetDevice");
                    target.put(field, resolveNodeReference(nodes, idMap.getOrDefault(endpoint, endpoint)));
                } else target.set(field, value.deepCopy());
            }
            String source = text(target, "source"), destination = text(target, "target");
            if (source.isBlank() || destination.isBlank() || source.equals(destination) || !containsNode(nodes, source) || !containsNode(nodes, destination)) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TOPOLOGY_PLAN_EDGE_INVALID", "规划更新后的连接无效");
            if (!portExists(nodes, source, text(target, "sourcePort")) || !portExists(nodes, destination, text(target, "targetPort"))) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TOPOLOGY_PLAN_PORT_INVALID", "规划更新使用了不存在的物理端口");
        }
    }
    private ObjectNode findMatchingEdge(ArrayNode edges, JsonNode raw, ArrayNode nodes, Map<String, String> idMap) {
        String requestedId = firstText(raw, "id", "edgeId", "connectionId");
        if (!requestedId.isBlank()) {
            for (JsonNode edge : edges) if (requestedId.equals(text(edge, "id")) && edge instanceof ObjectNode object) return object;
        }
        String rawSource = endpointRef(raw, "source", "from", "sourceDevice");
        String rawTarget = endpointRef(raw, "target", "to", "targetDevice");
        if (rawSource.isBlank() || rawTarget.isBlank()) return null;
        String source = resolveNodeReference(nodes, idMap.getOrDefault(rawSource, rawSource));
        String target = resolveNodeReference(nodes, idMap.getOrDefault(rawTarget, rawTarget));
        String sourcePort = firstText(raw, "sourcePort", "fromPort");
        String targetPort = firstText(raw, "targetPort", "toPort");
        for (JsonNode edge : edges) {
            String edgeSource = text(edge, "source"), edgeTarget = text(edge, "target");
            boolean direct = source.equalsIgnoreCase(edgeSource) && target.equalsIgnoreCase(edgeTarget)
                    && portMatches(sourcePort, text(edge, "sourcePort")) && portMatches(targetPort, text(edge, "targetPort"));
            boolean reverse = source.equalsIgnoreCase(edgeTarget) && target.equalsIgnoreCase(edgeSource)
                    && portMatches(sourcePort, text(edge, "targetPort")) && portMatches(targetPort, text(edge, "sourcePort"));
            if ((direct || reverse) && edge instanceof ObjectNode object) return object;
        }
        return null;
    }
    private boolean portMatches(String requested, String actual) {
        return requested == null || requested.isBlank() || requested.equalsIgnoreCase(actual);
    }
    private void removeEdges(ArrayNode edges, JsonNode removals, ArrayNode nodes, Map<String, String> idMap) {
        if (removals == null || !removals.isArray()) return;
        Set<String> ids = new HashSet<>();
        removals.forEach(item -> {
            String id = item.isTextual() ? item.asText() : firstText(item, "id", "edgeId", "connectionId");
            if (!id.isBlank() && containsEdgeId(edges, id)) ids.add(id);
            else if (!item.isTextual()) {
                ObjectNode match = findMatchingEdge(edges, item, nodes, idMap);
                if (match != null) ids.add(text(match, "id"));
            }
        });
        ArrayNode kept = objectMapper.createArrayNode(); for (JsonNode item : edges) if (!ids.contains(text(item, "id"))) kept.add(item); edges.removeAll(); kept.forEach(edges::add);
    }
    private boolean containsEdgeId(ArrayNode edges, String id) {
        for (JsonNode edge : edges) if (id.equals(text(edge, "id"))) return true;
        return false;
    }

    private void validateEdges(ArrayNode nodes, ArrayNode edges) {
        Set<String> portOwners = new HashSet<>();
        Set<String> physicalPairs = new HashSet<>();
        for (JsonNode edge : edges) {
            String source = text(edge, "source"), target = text(edge, "target");
            if (source.isBlank() || target.isBlank() || source.equals(target) || !containsNode(nodes, source) || !containsNode(nodes, target)) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TOPOLOGY_PLAN_EDGE_INVALID", "拓扑中存在无效连接");
            }
            String sourcePort = text(edge, "sourcePort"), targetPort = text(edge, "targetPort");
            if (!sourcePort.isBlank()) {
                if (!portExists(nodes, source, sourcePort) || !portOwners.add(source + "|" + sourcePort)) throw new ApiException(HttpStatus.CONFLICT, "TOPOLOGY_PORT_CAPACITY_EXCEEDED", "规划后的源设备端口已被占用或不存在");
            }
            if (!targetPort.isBlank()) {
                if (!portExists(nodes, target, targetPort) || !portOwners.add(target + "|" + targetPort)) throw new ApiException(HttpStatus.CONFLICT, "TOPOLOGY_PORT_CAPACITY_EXCEEDED", "规划后的目标设备端口已被占用或不存在");
            }
            String physical = source.compareTo(target) < 0 ? source + "|" + target + "|" + sourcePort + "|" + targetPort : target + "|" + source + "|" + targetPort + "|" + sourcePort;
            if (!physicalPairs.add(physical)) throw new ApiException(HttpStatus.CONFLICT, "TOPOLOGY_PLAN_DUPLICATE_EDGE", "规划包含重复物理连接");
        }
    }
    private boolean portExists(ArrayNode nodes, String nodeId, String port) {
        if (port == null || port.isBlank()) return false;
        JsonNode node = findNode(nodes, nodeId); JsonNode ports = node == null ? null : node.get("ports");
        if (ports == null || !ports.isArray()) return false;
        for (JsonNode value : ports) if (port.equals(value.isTextual() ? value.asText() : text(value, "name"))) return true;
        return false;
    }
    private boolean portAvailable(ArrayNode edges, String nodeId, String port) { for (JsonNode edge : edges) { if (nodeId.equals(text(edge, "source")) && port.equals(text(edge, "sourcePort"))) return false; if (nodeId.equals(text(edge, "target")) && port.equals(text(edge, "targetPort"))) return false; } return true; }
    private boolean hasPhysicalEdge(ArrayNode edges, String source, String target, String sourcePort, String targetPort) { for (JsonNode edge : edges) { boolean direct = source.equals(text(edge, "source")) && target.equals(text(edge, "target")) && sourcePort.equals(text(edge, "sourcePort")) && targetPort.equals(text(edge, "targetPort")); boolean reverse = source.equals(text(edge, "target")) && target.equals(text(edge, "source")) && sourcePort.equals(text(edge, "targetPort")) && targetPort.equals(text(edge, "sourcePort")); if (direct || reverse) return true; } return false; }
    private String firstFreePort(ArrayNode nodes, ArrayNode edges, String nodeId) {
        JsonNode target = null; for (JsonNode node : nodes) if (nodeId.equals(text(node, "id"))) { target = node; break; }
        if (target == null) return "";
        List<String> ports = new java.util.ArrayList<>(); JsonNode raw = target.get("ports"); if (raw != null && raw.isArray()) raw.forEach(item -> ports.add(item.isTextual() ? item.asText() : text(item, "name")));
        Set<String> used = new HashSet<>(); edges.forEach(edge -> { if (nodeId.equals(text(edge, "source"))) used.add(text(edge, "sourcePort")); if (nodeId.equals(text(edge, "target"))) used.add(text(edge, "targetPort")); });
        return ports.stream().filter(port -> !used.contains(port)).findFirst().orElse("");
    }
    private boolean isPortFree(ArrayNode edges, String nodeId, String port) {
        for (JsonNode edge : edges) {
            if (nodeId.equals(text(edge, "source")) && port.equals(text(edge, "sourcePort"))) return false;
            if (nodeId.equals(text(edge, "target")) && port.equals(text(edge, "targetPort"))) return false;
        }
        return true;
    }
    private boolean hasPort(ArrayNode nodes, String nodeId, String port) {
        for (JsonNode node : nodes) if (nodeId.equals(text(node, "id"))) {
            JsonNode raw = node.get("ports");
            if (raw != null && raw.isArray()) for (JsonNode item : raw) if (port.equals(item.isTextual() ? item.asText() : text(item, "name"))) return true;
            return false;
        }
        return false;
    }
    private String text(JsonNode node, String field) { return node == null || node.get(field) == null ? "" : node.get(field).asText("").trim(); }
}
