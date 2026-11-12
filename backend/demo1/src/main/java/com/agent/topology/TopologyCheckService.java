package com.agent.topology;

import com.agent.common.api.ApiException;
import com.agent.project.ProjectService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class TopologyCheckService {
    private final TopologyDocumentRepository documents;
    private final ProjectService projects;
    private final ObjectMapper mapper;

    public TopologyCheckService(TopologyDocumentRepository documents, ProjectService projects, ObjectMapper mapper) {
        this.documents = documents;
        this.projects = projects;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public TopologyCheckResponse check(Long projectId, Long ownerId) {
        projects.getOwned(projectId, ownerId);
        TopologyDocument document = documents.findByProjectIdAndOwnerId(projectId, ownerId).orElse(null);
        if (document == null) return new TopologyCheckResponse(new TopologyCheckSummary(100, 0, 0, 0, 0), List.of());
        try {
            return evaluate(mapper.readTree(document.getNodesJson()), mapper.readTree(document.getEdgesJson()));
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TOPOLOGY_DATA_INVALID", "当前拓扑数据无法检查");
        }
    }

    public TopologyCheckResponse evaluate(JsonNode nodes, JsonNode edges) {
        Map<String, JsonNode> nodeById = new LinkedHashMap<>();
        if (nodes != null && nodes.isArray()) for (JsonNode node : nodes) {
            String id = text(node, "id");
            if (!id.isBlank()) nodeById.put(id, node);
        }
        List<TopologyCheckIssue> issues = new ArrayList<>();
        Map<String, Integer> degree = new HashMap<>();
        Map<String, PortUse> portOwners = new HashMap<>();
        Set<String> duplicateEdges = new HashSet<>();
        Map<String, String> aggregationPairs = new HashMap<>();
        Map<String, Integer> aggregationMembers = new HashMap<>();
        Map<String, Set<String>> aggregationSpeeds = new HashMap<>();
        Map<String, String> seenIps = new HashMap<>();
        Map<String, Set<String>> neighbors = new HashMap<>();
        List<ValidEdge> validEdges = new ArrayList<>();
        if (edges != null && edges.isArray()) {
            int index = 0;
            for (JsonNode edge : edges) {
                String id = text(edge, "id");
                String source = text(edge, "source");
                String target = text(edge, "target");
                String sourcePort = text(edge, "sourcePort");
                String targetPort = text(edge, "targetPort");
                String aggregation = aggregationKey(edge);
                String pair = canonicalPair(source, target);
                if (!nodeById.containsKey(source) || !nodeById.containsKey(target)) {
                    issues.add(issue("INVALID_EDGE_" + index, "INVALID_EDGE", "HIGH", "structure", "连接引用了不存在的设备", "当前连接的源设备或目标设备不存在。", List.of(source, target), List.of(id), "边的端点无法在当前拓扑中找到。", "该连接不会形成有效网络路径。", "删除无效连接或重新选择两端设备。", false, "MANUAL"));
                } else if (source.equals(target)) {
                    issues.add(issue("SELF_LOOP_" + index, "SELF_LOOP", "HIGH", "structure", "设备存在自环连接", "设备不能连接到自身。", List.of(source), List.of(id), "源设备和目标设备相同。", "可能造成错误的拓扑关系。", "删除该自环连接。", false, "MANUAL"));
                } else {
                    degree.merge(source, 1, Integer::sum);
                    degree.merge(target, 1, Integer::sum);
                    neighbors.computeIfAbsent(source, ignored -> new LinkedHashSet<>()).add(target);
                    neighbors.computeIfAbsent(target, ignored -> new LinkedHashSet<>()).add(source);
                    validEdges.add(new ValidEdge(id, source, target, aggregation));
                    checkPort(issues, portOwners, source, sourcePort, id, pair, aggregation);
                    checkPort(issues, portOwners, target, targetPort, id, pair, aggregation);
                    String physicalPair = source.compareTo(target) < 0 ? source + "|" + target + "|" + sourcePort + "|" + targetPort : target + "|" + source + "|" + targetPort + "|" + sourcePort;
                    if (!duplicateEdges.add(physicalPair)) issues.add(issue("DUPLICATE_EDGE_" + index, "DUPLICATE_EDGE", "MEDIUM", "structure", "存在完全重复的物理连接", "同一对设备的相同端口被重复连接。", List.of(source, target), List.of(id), "两端端口组合与已有连接完全一致。", "数据链路可能被重复计算。", "保留一条连接或调整端口。", false, "MANUAL"));
                    if (!aggregation.isBlank()) {
                        aggregationMembers.merge(aggregation, 1, Integer::sum);
                        String previousPair = aggregationPairs.putIfAbsent(aggregation, pair);
                        if (previousPair != null && !previousPair.equals(pair)) {
                            issues.add(issue("AGGREGATION_PAIR_" + aggregation, "AGGREGATION_PAIR_MISMATCH", "MEDIUM", "capacity", "链路聚合组连接了不同设备对", "同一聚合组中的成员链路必须连接相同的两台设备。", List.of(source, target), List.of(id), "聚合组“" + aggregation + "”包含多个设备对。", "聚合可能无法建立或产生环路。", "调整聚合组成员，确保所有成员连接同一对设备。", false, "MANUAL"));
                        }
                        String speed = edgeSpeed(edge);
                        if (!speed.isBlank()) aggregationSpeeds.computeIfAbsent(aggregation, ignored -> new LinkedHashSet<>()).add(speed);
                    }
                }
                index++;
            }
        }
        aggregationMembers.forEach((aggregation, members) -> {
            if (members == 1) issues.add(issue("AGGREGATION_SINGLE_" + aggregation, "AGGREGATION_SINGLE_MEMBER", "LOW", "capacity", "链路聚合组仅包含一条成员链路", "当前聚合组没有形成冗余链路。", List.of(), List.of(), "聚合组“" + aggregation + "”只有1条成员链路。", "链路故障时无法提供冗余。", "如果需要高可用，请补充兼容的成员链路。", false, "MANUAL"));
        });
        aggregationSpeeds.forEach((aggregation, speeds) -> {
            if (speeds.size() > 1) issues.add(issue("AGGREGATION_SPEED_" + aggregation, "AGGREGATION_SPEED_MISMATCH", "MEDIUM", "capacity", "链路聚合成员速率不一致", "同一聚合组中的成员链路带宽配置不一致。", List.of(), List.of(), "聚合组“" + aggregation + "”包含多种速率：" + String.join("、", speeds) + "。", "聚合带宽可能按最低速率工作，甚至无法成功协商。", "统一聚合成员的链路速率和双工配置。", false, "MANUAL"));
        });
        addArchitectureChecks(issues, nodeById, neighbors, validEdges);
        for (JsonNode node : nodeById.values()) {
            String id = text(node, "id");
            String name = text(node, "name");
            if (degree.getOrDefault(id, 0) == 0 && nodeById.size() > 1) issues.add(issue("ORPHAN_" + id, "ORPHAN_NODE", "HIGH", "structure", name + "当前没有任何连接", "该设备是孤立节点。", List.of(id), List.of(), "设备度数为0。", "设备无法参与网络通信。", "连接到合适的上联设备，或移除该节点。", false, "MANUAL"));
            String ip = text(node, "ip");
            if (!ip.isBlank()) {
                if (!ip.matches("(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)(?:\\.(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}")) issues.add(issue("INVALID_IP_" + id, "INVALID_IPV4", "MEDIUM", "ip", name + "的管理 IP 格式无效", "请填写合法的 IPv4 地址。", List.of(id), List.of(), "当前值为“" + ip + "”。", "设备管理地址可能无法访问。", "修正为合法 IPv4 地址。", false, "MANUAL"));
                else {
                    String previousNode = seenIps.putIfAbsent(ip, id);
                    if (previousNode != null) issues.add(issue("DUPLICATE_IP_" + id, "DUPLICATE_IP", "HIGH", "ip", "存在重复的管理 IP", "多个设备使用了相同的管理 IP。", List.of(previousNode, id), List.of(), "IP 地址“" + ip + "”重复。", "可能导致管理和路由冲突。", "为设备分配唯一管理 IP。", false, "MANUAL"));
                }
            }
        }
        int high = (int) issues.stream().filter(i -> "HIGH".equals(i.level())).count();
        int medium = (int) issues.stream().filter(i -> "MEDIUM".equals(i.level())).count();
        int low = (int) issues.stream().filter(i -> "LOW".equals(i.level())).count();
        int score = Math.max(0, 100 - high * 25 - medium * 10 - low * 4);
        int passed = Math.max(0, nodeById.size() + (edges != null && edges.isArray() ? edges.size() : 0) - issues.size());
        return new TopologyCheckResponse(new TopologyCheckSummary(score, high, medium, low, passed), issues);
    }

    private void checkPort(List<TopologyCheckIssue> issues, Map<String, PortUse> owners, String nodeId, String port, String edgeId, String pair, String aggregation) {
        if (port.isBlank()) return;
        String key = nodeId + "|" + port;
        PortUse previous = owners.putIfAbsent(key, new PortUse(edgeId, pair, aggregation));
        if (previous != null && !previous.edgeId().equals(edgeId)) issues.add(issue("PORT_" + nodeId + "_" + port + "_" + edgeId, "PORT_REUSED", "HIGH", "capacity", "同一物理端口被重复使用", "端口“" + port + "”同时被多条连接占用。", List.of(nodeId), List.of(previous.edgeId(), edgeId), "一个物理端口只能承载一条物理链路；链路聚合也必须使用不同的成员端口。", "连接状态与实际设备不一致。", "调整端口，确保每条物理链路使用独立端口。", false, "MANUAL"));
    }

    private String aggregationKey(JsonNode edge) {
        for (String field : List.of("aggregationId", "aggregationGroup", "lagId", "portChannel", "portChannelId")) {
            String value = text(edge, field);
            if (!value.isBlank()) return value;
        }
        JsonNode nested = edge == null ? null : edge.get("aggregation");
        if (nested != null && nested.isObject()) {
            for (String field : List.of("id", "name", "group")) {
                String value = text(nested, field);
                if (!value.isBlank()) return value;
            }
        }
        return "";
    }

    private String edgeSpeed(JsonNode edge) {
        for (String field : List.of("speed", "bandwidth", "linkSpeed", "rate")) {
            String value = text(edge, field);
            if (!value.isBlank()) return value;
        }
        return text(edge, "connectionType");
    }

    private String canonicalPair(String source, String target) {
        return source.compareTo(target) < 0 ? source + "|" + target : target + "|" + source;
    }

    private void addArchitectureChecks(List<TopologyCheckIssue> issues, Map<String, JsonNode> nodeById,
                                      Map<String, Set<String>> neighbors, List<ValidEdge> validEdges) {
        List<JsonNode> cores = nodeById.values().stream().filter(this::isCore).toList();
        List<JsonNode> controllers = nodeById.values().stream().filter(this::isController).toList();
        List<JsonNode> services = nodeById.values().stream().filter(this::isService).toList();

        if (cores.size() >= 2) {
            JsonNode primaryCore = cores.get(0);
            JsonNode secondaryCore = cores.get(1);
            List<String> coreIds = List.of(text(primaryCore, "id"), text(secondaryCore, "id"));
            if (controllers.size() < 2) {
                issues.add(issue("DUAL_CORE_SINGLE_AC", "DUAL_CORE_SINGLE_AC", "HIGH", "availability",
                        "双核心网络只有一个 AC", "检测到两个核心交换机，但无线控制器（AC）没有按核心冗余配置。",
                        coreIds, List.of(), "当前拓扑包含双核心，但 AC 数量为 " + controllers.size() + "。",
                        "任一核心故障都可能使无线控制器失去上联，造成整栋楼无线业务中断。",
                        "至少部署两台 AC，并让每台 AC 同时上联两个核心交换机。", false, "MANUAL"));
            }
            for (JsonNode controller : controllers) {
                String controllerId = text(controller, "id");
                Set<String> connectedCores = neighbors.getOrDefault(controllerId, Set.of()).stream()
                        .map(nodeById::get).filter(Objects::nonNull).filter(this::isCore)
                        .map(node -> text(node, "id")).collect(java.util.stream.Collectors.toSet());
                if (connectedCores.size() < 2) {
                    issues.add(issue("AC_SINGLE_HOMING_" + controllerId, "AC_SINGLE_HOMING", "HIGH", "availability",
                            text(controller, "name") + "仅连接一个核心", "每台 AC 都应同时连接两个核心交换机。",
                            List.of(controllerId), edgeIdsFor(controllerId, connectedCores, validEdges),
                            "当前 AC 只发现 " + connectedCores.size() + " 个核心上联。",
                            "单个核心或上联链路故障会使该 AC 失去服务。",
                            "为该 AC 增加到另一个核心交换机的独立上联。", false, "MANUAL"));
                }
            }

            String primaryId = text(primaryCore, "id");
            String secondaryId = text(secondaryCore, "id");
            List<ValidEdge> coreLinks = validEdges.stream()
                    .filter(edge -> samePair(edge.source(), edge.target(), primaryId, secondaryId)).toList();
            if (coreLinks.size() < 2) {
                issues.add(issue("CORE_PAIR_NO_REDUNDANCY", "CORE_PAIR_NO_REDUNDANCY", "HIGH", "availability",
                        "双核心之间缺少链路冗余", "两个核心交换机之间少于两条独立物理链路。",
                        coreIds, coreLinks.stream().map(ValidEdge::id).toList(),
                        "核心链路数量为 " + coreLinks.size() + "。", "任一核心互联链路故障都可能导致核心间通信中断。",
                        "使用不同物理端口增加至少一条互联链路，并配置链路聚合。", false, "MANUAL"));
            } else {
                boolean hasCoreLag = coreLinks.stream().filter(edge -> !edge.aggregation().isBlank())
                        .collect(java.util.stream.Collectors.groupingBy(ValidEdge::aggregation)).values().stream()
                        .anyMatch(group -> group.size() >= 2);
                if (!hasCoreLag) {
                    issues.add(issue("CORE_PAIR_NO_LAG", "CORE_PAIR_NO_LAG", "HIGH", "capacity",
                            "双核心互联未配置链路聚合", "双核心之间存在多条链路，但没有发现共同的链路聚合组。",
                            coreIds, coreLinks.stream().map(ValidEdge::id).toList(),
                            "核心互联链路未共享同一个 aggregationId/aggregationGroup。",
                            "多条链路可能无法作为统一高带宽、冗余的逻辑链路工作。",
                            "为核心互联链路配置同一个聚合组，并使用不同物理端口。", false, "MANUAL"));
                }
            }
        }

        if (!services.isEmpty()) {
            List<JsonNode> serverAccess = nodeById.values().stream().filter(this::isAccess)
                    .filter(node -> neighbors.getOrDefault(text(node, "id"), Set.of()).stream()
                            .map(nodeById::get).filter(Objects::nonNull).anyMatch(this::isService)).toList();
            if (serverAccess.isEmpty()) {
                issues.add(issue("SERVER_ACCESS_MISSING", "SERVER_ACCESS_MISSING", "MEDIUM", "segmentation",
                        "服务器区缺少接入或汇聚交换机", "服务器、数据库或 NAS 没有接入独立的服务器区交换机。",
                        services.stream().map(node -> text(node, "id")).toList(), List.of(),
                        "未发现与服务器类设备直接相连的接入/汇聚交换机。",
                        "服务器直接挂在核心或边界设备上，故障域和安全域难以隔离。",
                        "增加服务器区接入/汇聚交换机，让服务器先接入该交换机，再双上联核心。", false, "MANUAL"));
            }
            for (JsonNode service : services) {
                String serviceId = text(service, "id");
                Set<String> directCoreIds = neighbors.getOrDefault(serviceId, Set.of()).stream()
                        .map(nodeById::get).filter(Objects::nonNull).filter(this::isCore)
                        .map(node -> text(node, "id")).collect(java.util.stream.Collectors.toSet());
                if (!directCoreIds.isEmpty()) {
                    issues.add(issue("SERVER_DIRECT_CORE_" + serviceId, "SERVER_DIRECT_CORE", "MEDIUM", "segmentation",
                            text(service, "name") + "直接连接核心交换机", "服务器类设备不应直接占用核心交换机接入口。",
                            concatIds(List.of(serviceId), directCoreIds), edgeIdsFor(serviceId, directCoreIds, validEdges),
                            "检测到服务器、数据库或 NAS 直接连接核心交换机。",
                            "服务器区缺少清晰的接入层，安全策略、故障隔离和端口扩展能力不足。",
                            "将该设备接入服务器区交换机，再由服务器区交换机上联核心。", false, "MANUAL"));
                }
            }
            if (cores.size() >= 2) {
                for (JsonNode access : serverAccess) {
                    String accessId = text(access, "id");
                    Set<String> uplinkCores = neighbors.getOrDefault(accessId, Set.of()).stream()
                            .map(nodeById::get).filter(Objects::nonNull).filter(this::isCore)
                            .map(node -> text(node, "id")).collect(java.util.stream.Collectors.toSet());
                    if (uplinkCores.size() < 2) {
                        issues.add(issue("SERVER_ACCESS_SINGLE_UPLINK_" + accessId, "SERVER_ACCESS_SINGLE_UPLINK", "MEDIUM", "availability",
                                text(access, "name") + "仅单上联核心", "服务器区交换机没有同时连接两个核心交换机。",
                                List.of(accessId), edgeIdsFor(accessId, uplinkCores, validEdges),
                                "当前服务器区交换机只发现 " + uplinkCores.size() + " 个核心上联。",
                                "单个核心故障会同时影响服务器区内的多台设备。",
                                "为服务器区交换机增加到另一个核心的独立上联，并按需配置聚合。", false, "MANUAL"));
                    }
                }
            }
        }
    }

    private boolean samePair(String left, String right, String source, String target) {
        return (left.equals(source) && right.equals(target)) || (left.equals(target) && right.equals(source));
    }

    private List<String> edgeIdsFor(String nodeId, Set<String> relatedIds, List<ValidEdge> validEdges) {
        return validEdges.stream().filter(edge -> (edge.source().equals(nodeId) && relatedIds.contains(edge.target()))
                        || (edge.target().equals(nodeId) && relatedIds.contains(edge.source())))
                .map(ValidEdge::id).toList();
    }

    private List<String> concatIds(List<String> first, Set<String> rest) {
        List<String> ids = new ArrayList<>(first);
        ids.addAll(rest);
        return ids;
    }

    private boolean isCore(JsonNode node) {
        return matches(node, "core-switch", "layer3-switch", "核心交换机", "三层交换机");
    }

    private boolean isController(JsonNode node) {
        String type = firstText(node, "type", "deviceType", "resourceType").toLowerCase(Locale.ROOT).replace('_', '-');
        String name = firstText(node, "name", "deviceName", "label").toLowerCase(Locale.ROOT);
        return type.equals("ac") || type.contains("ac-controller") || type.contains("wireless-controller") || type.contains("wireless-ac")
                || name.contains("无线控制器") || name.contains("无线ac") || name.matches(".*\\bac\\b.*");
    }

    private boolean isAccess(JsonNode node) {
        return !isCore(node) && matches(node, "access-switch", "aggregation-switch", "distribution-switch", "接入交换机", "汇聚交换机", "汇聚");
    }

    private boolean isService(JsonNode node) {
        String type = firstText(node, "type", "deviceType", "resourceType").toLowerCase(Locale.ROOT).replace('_', '-');
        if (!type.isBlank() && !Set.of("device", "network-device", "unknown").contains(type)) {
            return type.contains("server") || type.contains("database") || type.equals("nas");
        }
        return matches(node, "server", "database", "nas", "服务器", "数据库", "存储");
    }

    private boolean matches(JsonNode node, String... values) {
        String type = firstText(node, "type", "deviceType", "resourceType").toLowerCase(Locale.ROOT).replace('_', '-');
        String name = firstText(node, "name", "deviceName", "label").toLowerCase(Locale.ROOT);
        return Arrays.stream(values).map(value -> value.toLowerCase(Locale.ROOT)).anyMatch(value -> type.contains(value) || name.contains(value));
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private record ValidEdge(String id, String source, String target, String aggregation) { }
    private record PortUse(String edgeId, String pair, String aggregation) { }

    private TopologyCheckIssue issue(String id, String rule, String level, String category, String title, String description, List<String> nodes, List<String> edges, String reason, String impact, String suggestion, boolean autoFix, String fixType) {
        return new TopologyCheckIssue(id, rule, level, category, title, description, nodes, edges, reason, impact, suggestion, autoFix, fixType);
    }
    private String text(JsonNode node, String field) { return node == null || node.get(field) == null ? "" : node.get(field).asText("").trim(); }
}
