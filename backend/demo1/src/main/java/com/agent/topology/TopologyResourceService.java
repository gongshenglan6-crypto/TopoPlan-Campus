package com.agent.topology;

import com.agent.admin.config.AdminConfigService;
import com.agent.common.api.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class TopologyResourceService {
    private static final String MODULE = "knowledge.devices";
    private static final long MAX_ICON_SIZE = 2L * 1024 * 1024;
    private static final Set<String> ICON_FORMATS = Set.of("svg", "png", "webp");
    private static final List<String> CATEGORIES = List.of("网络设备", "安全设备", "无线设备", "服务器与存储", "终端设备", "其他");

    private final AdminConfigService configs;
    private final Path iconDirectory;

    public TopologyResourceService(AdminConfigService configs,
                                   @Value("${topo.upload-dir:uploads}") String uploadDirectory) {
        this.configs = configs;
        this.iconDirectory = Paths.get(uploadDirectory).toAbsolutePath().normalize().resolve("topology-icons");
    }

    @Transactional
    public List<Map<String, Object>> listPublicResources() {
        return listResources().stream()
                .filter(resource -> !Boolean.FALSE.equals(resource.get("enabled")))
                .filter(resource -> !"连接素材".equals(resource.get("category")))
                .map(this::publicResource)
                .toList();
    }

    @Transactional
    public List<Map<String, Object>> listAiAvailableResources() {
        return listPublicResources().stream()
                .filter(resource -> Boolean.TRUE.equals(resource.get("allowAi")))
                .toList();
    }

    @Transactional
    public Map<String, Object> getPublicResource(String resourceId) {
        String requested = resourceId == null ? "" : resourceId.trim();
        String canonicalKey = resolveResourceKey(requested);
        Map<String, Object> resource = listResources().stream()
                .filter(item -> canonicalKey.equals(text(item, "key", "")))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TOPOLOGY_RESOURCE_NOT_FOUND", "拓扑资源不存在"));
        return publicResource(resource);
    }

    @Transactional
    public List<Map<String, Object>> listForAdmin() {
        return listResources().stream().map(this::adminResource).toList();
    }

    @Transactional
    public Map<String, Object> getForAdmin(String resourceId) {
        return adminResource(getRawResource(resourceId));
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> values) {
        seedDefaults();
        Map<String, Object> payload = resourcePayload(values, null, true);
        ensureUniqueDeviceType(String.valueOf(payload.get("type")), null);
        return adminResource(configs.save(MODULE, UUID.randomUUID().toString(), payload));
    }

    @Transactional
    public Map<String, Object> update(String resourceId, Map<String, Object> values) {
        String resourceKey = resolveResourceKey(resourceId);
        Map<String, Object> existing = getRawResource(resourceKey);
        Map<String, Object> payload = resourcePayload(values, existing, false);
        ensureUniqueDeviceType(String.valueOf(payload.get("type")), resourceKey);
        return adminResource(configs.save(MODULE, resourceKey, payload));
    }

    public Map<String, Object> uploadIcon(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() == 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ICON_FILE_EMPTY", "图标文件不能为空");
        }
        if (file.getSize() > MAX_ICON_SIZE) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ICON_FILE_TOO_LARGE", "图标文件不能超过2MB");
        }
        String originalFileName = safeFileName(file.getOriginalFilename());
        String format = extension(originalFileName);
        if (!ICON_FORMATS.contains(format)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ICON_FILE_TYPE_UNSUPPORTED", "仅支持SVG、PNG、WEBP格式");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ICON_FILE_READ_FAILED", "图标读取失败，请重试");
        }
        String iconHash = sha256(content);
        String storageName = "topology-icon-" + iconHash + "." + format;
        try {
            Files.createDirectories(iconDirectory);
            Path target = iconDirectory.resolve(storageName);
            if (!Files.isRegularFile(target)) Files.write(target, content);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ICON_FILE_STORAGE_FAILED", "图标上传失败，请重试");
        }

        String iconPath = "/uploads/topology-icons/" + storageName;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("fileName", storageName);
        result.put("originalFileName", originalFileName);
        result.put("iconPath", iconPath);
        result.put("iconUrl", iconPath);
        result.put("iconMimeType", mimeType(format));
        result.put("iconSize", file.getSize());
        result.put("iconHash", iconHash);
        return result;
    }

    public Resource loadIcon(String fileName) {
        String safeName = safeFileName(fileName);
        Path icon = iconDirectory.resolve(safeName).normalize();
        if (!icon.startsWith(iconDirectory) || !Files.isRegularFile(icon)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TOPOLOGY_ICON_NOT_FOUND", "图标不存在");
        }
        return new FileSystemResource(icon);
    }

    public String iconContentType(String fileName) {
        return mimeType(extension(fileName));
    }

    public List<String> categories() {
        return CATEGORIES;
    }

    private List<Map<String, Object>> listResources() {
        seedDefaults();
        return deduplicateResources(configs.list(MODULE).stream()
                .sorted(Comparator.comparingInt(resource -> number(resource.get("order"), 100)))
                .toList());
    }

    private void seedDefaults() {
        List<Map<String, Object>> resources = configs.list(MODULE);
        for (Map<String, Object> resource : defaultResources()) {
            String type = String.valueOf(resource.get("type"));
            Map<String, Object> existing = resources.stream()
                    .filter(item -> type.equals(text(item, "key", "")) || type.equals(canonicalType(item)))
                    .sorted(Comparator.comparing((Map<String, Object> item) -> !type.equals(text(item, "key", ""))))
                    .findFirst().orElse(null);
            if (existing == null) {
                configs.save(MODULE, type, resource);
            } else if (!type.equals(text(existing, "key", ""))) {
                // Migrate a legacy UUID-keyed record to the stable device type key.
                configs.save(MODULE, type, mergedResource(existing, resource));
            } else {
                Map<String, Object> merged = mergedResource(existing, resource);
                if (!sameResourcePayload(existing, merged)) configs.save(MODULE, type, merged);
            }
        }
        // Keep old identifiers resolvable for historical projects, but prevent
        // duplicate cards from appearing in either resource list.
        for (Map<String, Object> resource : configs.list(MODULE)) {
            String type = canonicalType(resource);
            String key = text(resource, "key", "");
            if (type.isBlank() || key.isBlank() || key.equals(type)) continue;
            if (!Boolean.FALSE.equals(resource.get("enabled"))) {
                Map<String, Object> disabled = new LinkedHashMap<>(resource);
                disabled.put("enabled", false);
                disabled.put("legacyResourceType", type);
                configs.save(MODULE, key, disabled);
            }
        }
    }

    private Map<String, Object> getRawResource(String resourceId) {
        Map<String, Object> resource = configs.get(MODULE, resolveResourceKey(resourceId));
        if (!Boolean.TRUE.equals(resource.get("configured"))) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TOPOLOGY_RESOURCE_NOT_FOUND", "拓扑资源不存在");
        }
        return resource;
    }

    /**
     * Configuration records have two identifiers: the database record id and
     * the module key used by the resource APIs. Accept both so older clients
     * and already persisted records can still be opened and edited.
     */
    private String resolveResourceKey(String resourceId) {
        seedDefaults();
        String requested = resourceId == null ? "" : resourceId.trim();
        if (requested.isBlank()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TOPOLOGY_RESOURCE_NOT_FOUND", "拓扑资源不存在");
        }

        Map<String, Object> byKey = configs.get(MODULE, requested);
        if (Boolean.TRUE.equals(byKey.get("configured"))) {
            String type = canonicalType(byKey);
            return type.isBlank() ? requested : type;
        }

        return configs.list(MODULE).stream()
                .filter(resource -> requested.equals(text(resource, "key", ""))
                        || requested.equals(text(resource, "id", ""))
                        || requested.equals(text(resource, "type", "")))
                .map(this::canonicalType)
                .filter(key -> !key.isBlank())
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TOPOLOGY_RESOURCE_NOT_FOUND", "拓扑资源不存在"));
    }

    private void ensureUniqueDeviceType(String deviceType, String currentResourceId) {
        boolean exists = configs.list(MODULE).stream().anyMatch(resource ->
                !String.valueOf(resource.get("key")).equals(currentResourceId)
                        && deviceType.equalsIgnoreCase(text(resource, "type", "")));
        if (exists) throw new ApiException(HttpStatus.CONFLICT, "TOPOLOGY_DEVICE_TYPE_EXISTS", "该设备类型标识已存在");
    }

    private Map<String, Object> resourcePayload(Map<String, Object> values, Map<String, Object> existing, boolean creating) {
        Map<String, Object> payload = new LinkedHashMap<>();
        String name = required(values, existing, "name", "设备名称", 80);
        String deviceType = required(values, existing, "deviceType", "设备类型标识", 80);
        if (!deviceType.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TOPOLOGY_DEVICE_TYPE_INVALID", "设备类型标识仅支持小写字母、数字和连字符");
        }
        String category = required(values, existing, "category", "设备分类", 40);
        if (!CATEGORIES.contains(category)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TOPOLOGY_CATEGORY_INVALID", "请选择有效的设备分类");
        }
        String iconPath = value(values, existing, "iconPath", "asset");
        if (creating && iconPath.isBlank()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TOPOLOGY_ICON_REQUIRED", "请上传设备图标");
        }
        if (!iconPath.isBlank()) validateIconPath(iconPath);

        payload.put("type", deviceType);
        payload.put("name", name);
        payload.put("englishName", trimmed(value(values, existing, "englishName"), 120));
        payload.put("vendor", "TOPOPLAN");
        payload.put("category", category);
        payload.put("format", formatForPath(iconPath, value(values, existing, "format")));
        payload.put("tone", value(values, existing, "tone").isBlank() ? "slate" : value(values, existing, "tone"));
        payload.put("size", value(values, existing, "size").isBlank() ? "48 × 48" : value(values, existing, "size"));
        payload.put("enabled", booleanValue(values, existing, "isEnabled", "enabled", true));
        payload.put("allowAi", booleanValue(values, existing, "allowAi", null, true));
        payload.put("draggable", booleanValue(values, existing, "allowUserDrag", "draggable", true));
        payload.put("order", integerValue(values, existing, "sortOrder", "order", 100));
        payload.put("description", trimmed(value(values, existing, "description"), 1000));
        Map<String, Object> profile = portProfile(deviceType, values, existing);
        payload.put("ports", profile.get("ports"));
        payload.put("portProfileId", profile.get("portProfileId"));
        payload.put("portProfileName", profile.get("portProfileName"));
        payload.put("defaultPortCount", profile.get("defaultPortCount"));
        if (!iconPath.isBlank()) {
            String storedFileName = fileName(iconPath);
            payload.put("asset", iconPath);
            payload.put("iconPath", iconPath);
            payload.put("iconFileName", storedFileName);
            payload.put("iconMimeType", mimeType(extension(storedFileName)));
            payload.put("iconSize", iconSize(storedFileName));
            payload.put("iconHash", iconHash(storedFileName));
        }
        return payload;
    }

    private void validateIconPath(String iconPath) {
        String prefix = "/uploads/topology-icons/";
        if (!iconPath.startsWith(prefix)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TOPOLOGY_ICON_PATH_INVALID", "图标路径无效");
        }
        String name = iconPath.substring(prefix.length());
        if (!ICON_FORMATS.contains(extension(name))) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TOPOLOGY_ICON_PATH_INVALID", "图标路径格式无效");
        }
        Path icon = iconDirectory.resolve(safeFileName(name)).normalize();
        if (!icon.startsWith(iconDirectory) || !Files.isRegularFile(icon)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TOPOLOGY_ICON_PATH_INVALID", "上传的图标不存在");
        }
    }

    private Map<String, Object> adminResource(Map<String, Object> source) {
        Map<String, Object> resource = new LinkedHashMap<>(source);
        String key = text(source, "key", "");
        resource.put("recordId", source.get("id"));
        resource.put("id", key);
        resource.put("deviceType", text(source, "type", ""));
        resource.put("sortOrder", number(source.get("order"), 100));
        resource.put("allowUserDrag", !Boolean.FALSE.equals(source.get("draggable")));
        resource.put("isEnabled", !Boolean.FALSE.equals(source.get("enabled")));
        resource.put("iconPath", text(source, "iconPath", text(source, "asset", "")));
        resource.put("portProfileId", text(source, "portProfileId", ""));
        resource.put("portProfileName", text(source, "portProfileName", ""));
        resource.put("defaultPortCount", number(source.get("defaultPortCount"), portsList(source).size()));
        return resource;
    }

    private Map<String, Object> publicResource(Map<String, Object> source) {
        Map<String, Object> resource = new LinkedHashMap<>();
        copy(source, resource, "id", "key", "type", "name", "englishName", "category", "format", "tone", "size", "description", "allowAi", "enabled", "draggable", "order", "asset", "icon", "iconUrl", "iconPath", "iconHash", "portProfileId", "portProfileName", "defaultPortCount");
        if (resource.containsKey("id")) resource.put("resourceId", resource.get("id"));
        else if (resource.containsKey("key")) resource.put("resourceId", resource.get("key"));
        resource.put("ports", portsList(source));
        return resource;
    }

    private void copy(Map<String, Object> source, Map<String, Object> target, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null) target.put(key, value);
        }
    }

    private String required(Map<String, Object> values, Map<String, Object> existing, String key, String label, int limit) {
        String value = trimmed(value(values, existing, key), limit);
        if (value.isBlank()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TOPOLOGY_RESOURCE_VALIDATION_ERROR", "请输入" + label);
        return value;
    }

    private String value(Map<String, Object> values, Map<String, Object> existing, String key) {
        return value(values, existing, key, null);
    }

    private String value(Map<String, Object> values, Map<String, Object> existing, String primary, String fallback) {
        if (values != null && values.containsKey(primary)) return values.get(primary) == null ? "" : String.valueOf(values.get(primary)).trim();
        if (fallback != null && values != null && values.containsKey(fallback)) return values.get(fallback) == null ? "" : String.valueOf(values.get(fallback)).trim();
        if (existing == null) return "";
        if (existing.containsKey(primary)) return existing.get(primary) == null ? "" : String.valueOf(existing.get(primary)).trim();
        if (fallback != null && existing.containsKey(fallback)) return existing.get(fallback) == null ? "" : String.valueOf(existing.get(fallback)).trim();
        return "";
    }

    private boolean booleanValue(Map<String, Object> values, Map<String, Object> existing, String primary, String fallback, boolean defaultValue) {
        Object raw = null;
        if (values != null && values.containsKey(primary)) raw = values.get(primary);
        else if (fallback != null && values != null && values.containsKey(fallback)) raw = values.get(fallback);
        else if (existing != null && existing.containsKey(primary)) raw = existing.get(primary);
        else if (fallback != null && existing != null && existing.containsKey(fallback)) raw = existing.get(fallback);
        if (raw == null) return defaultValue;
        if (raw instanceof Boolean bool) return bool;
        return Boolean.parseBoolean(String.valueOf(raw));
    }

    private int integerValue(Map<String, Object> values, Map<String, Object> existing, String primary, String fallback, int defaultValue) {
        String raw = value(values, existing, primary, fallback);
        if (raw.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TOPOLOGY_SORT_ORDER_INVALID", "排序权重必须为整数");
        }
    }

    private Map<String, Object> portProfile(String type, Map<String, Object> values, Map<String, Object> existing) {
        Object source = values != null && values.containsKey("ports") ? values.get("ports") : existing == null ? null : existing.get("ports");
        List<String> portNames = singleInterfaceType(type) ? defaultPorts(type) : normalizePortNames(source);
        if (portNames.isEmpty()) portNames = defaultPorts(type);
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("ports", portNames);
        profile.put("portProfileId", singleInterfaceType(type) || value(values, existing, "portProfileId").isBlank() ? defaultProfileId(type) : value(values, existing, "portProfileId"));
        profile.put("portProfileName", singleInterfaceType(type) || value(values, existing, "portProfileName").isBlank() ? defaultProfileName(type, portNames.size()) : value(values, existing, "portProfileName"));
        profile.put("defaultPortCount", portNames.size());
        return profile;
    }

    private List<String> portsList(Map<String, Object> source) {
        return normalizePortNames(source == null ? null : source.get("ports"));
    }

    private List<String> normalizePortNames(Object source) {
        if (!(source instanceof List<?> list)) return List.of();
        return list.stream().map(value -> {
            if (value instanceof Map<?, ?> map) {
                Object name = map.get("name");
                Object portName = map.get("portName");
                Object selected = name != null ? name : portName;
                return selected == null ? "" : String.valueOf(selected);
            }
            return String.valueOf(value);
        }).map(String::trim).filter(value -> !value.isBlank()).distinct().limit(256).toList();
    }

    private List<String> defaultPorts(String type) {
        return switch (type) {
            case "core-switch", "layer3-switch" -> numberedPorts("GE0/0/", 48);
            case "access-switch" -> numberedPorts("GE0/0/", 24);
            case "router" -> List.of("GE0/0/0", "GE0/0/1", "GE0/0/2", "GE0/0/3");
            case "firewall" -> List.of("GE0/0/0", "GE0/0/1", "GE0/0/2", "GE0/0/3", "Management");
            case "ac-controller" -> List.of("Management", "GE0/0/0", "GE0/0/1", "GE0/0/2");
            case "wireless-ap" -> List.of("ETH0");
            // Server-side resources use one logical interface so their port
            // model stays consistent in the admin console and project canvas.
            case "server", "database", "nas" -> List.of("ETH0");
            case "pc", "laptop", "printer", "camera" -> List.of("Ethernet0");
            case "ip-phone" -> List.of("LAN", "PC Passthrough");
            case "internet", "cloud" -> List.of("Logical-1", "Logical-2", "Logical-3", "Logical-4");
            default -> List.of("GE0/0/1", "GE0/0/2");
        };
    }

    private boolean singleInterfaceType(String type) {
        return Set.of("server", "database", "nas").contains(type);
    }

    private List<String> numberedPorts(String prefix, int count) {
        List<String> ports = new ArrayList<>(count);
        for (int index = 1; index <= count; index++) ports.add(prefix + index);
        return ports;
    }

    private String defaultProfileId(String type) { return type + "-default"; }

    private String defaultProfileName(String type, int count) {
        if (type.contains("switch")) return count + "口交换机";
        return type + " 默认端口";
    }

    private String defaultIconPath(String type) {
        String icon = switch (type) {
            case "router" -> "router.svg";
            case "firewall" -> "firewall.svg";
            case "server", "database", "nas" -> "server.svg";
            default -> "switch.svg";
        };
        return "/uploads/topology-icons/" + icon;
    }

    private List<String> ports(Map<String, Object> values, Map<String, Object> existing) {
        Object source = values != null && values.containsKey("ports") ? values.get("ports") : existing == null ? null : existing.get("ports");
        return normalizePortNames(source);
    }

    private String formatForPath(String iconPath, String fallback) {
        String format = extension(iconPath);
        if (format.isBlank()) format = extension(fallback);
        return format.isBlank() ? "SVG" : format.toUpperCase(Locale.ROOT);
    }

    private String safeFileName(String fileName) {
        String name = Paths.get(fileName == null ? "" : fileName).getFileName().toString();
        if (name.isBlank() || name.contains("..")) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ICON_FILE_NAME_INVALID", "图标文件名无效");
        return name;
    }

    private String fileName(String path) {
        return safeFileName(path.substring(path.lastIndexOf('/') + 1));
    }

    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String mimeType(String format) {
        return switch (format) {
            case "svg" -> "image/svg+xml";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }

    private long iconSize(String fileName) {
        try {
            return Files.size(iconDirectory.resolve(fileName));
        } catch (IOException ignored) {
            return 0;
        }
    }

    private String iconHash(String fileName) {
        try {
            Path file = iconDirectory.resolve(fileName).normalize();
            return Files.isRegularFile(file) ? sha256(Files.readAllBytes(file)) : "";
        } catch (IOException ignored) {
            return "";
        }
    }

    private String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) result.append(String.format(Locale.ROOT, "%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ICON_HASH_FAILED", "图标校验失败");
        }
    }

    private String text(Map<String, Object> source, String key, String fallback) {
        Object value = source.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private String trimmed(String value, int limit) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > limit) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TOPOLOGY_RESOURCE_VALIDATION_ERROR", "输入内容过长");
        return normalized;
    }

    private int number(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try { return Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private String canonicalType(Map<String, Object> resource) {
        String type = text(resource, "type", "").trim().toLowerCase(Locale.ROOT);
        if (!type.isBlank()) return type;
        String value = (text(resource, "name", "") + " " + text(resource, "englishName", "")).toLowerCase(Locale.ROOT);
        if (value.contains("core") && value.contains("switch") || value.contains("核心交换")) return "core-switch";
        if (value.contains("layer 3") || value.contains("layer3") || value.contains("三层交换")) return "layer3-switch";
        if (value.contains("access") && value.contains("switch") || value.contains("接入交换")) return "access-switch";
        if (value.contains("router") || value.contains("路由")) return "router";
        if (value.contains("firewall") || value.contains("防火墙")) return "firewall";
        if (value.contains("ids") || value.contains("入侵")) return "ids-ips";
        if (value.contains("vpn")) return "vpn-gateway";
        if (value.contains("wireless") || value.contains("无线") || value.matches(".*\\bap\\b.*")) return "wireless-ap";
        if (value.contains("controller") || value.contains("控制器")) return "ac-controller";
        if (value.contains("server") || value.contains("服务器")) return "server";
        if (value.contains("database") || value.contains("数据库")) return "database";
        if (value.contains("nas")) return "nas";
        if (value.contains("laptop") || value.contains("笔记本")) return "laptop";
        if (value.contains("printer") || value.contains("打印机")) return "printer";
        if (value.contains("phone") || value.contains("电话")) return "ip-phone";
        if (value.contains("camera") || value.contains("摄像头")) return "camera";
        if (value.contains("internet")) return "internet";
        if (value.contains("cloud") || value.contains("云")) return "cloud";
        if (value.matches(".*\\bpc\\b.*")) return "pc";
        return "";
    }

    private Map<String, Object> mergedResource(Map<String, Object> existing, Map<String, Object> defaults) {
        Map<String, Object> merged = new LinkedHashMap<>(defaults);
        merged.putAll(existing);
        String type = canonicalType(defaults);
        merged.put("type", type);
        merged.putIfAbsent("name", defaults.get("name"));
        merged.putIfAbsent("englishName", defaults.get("englishName"));
        String existingIcon = text(merged, "iconPath", text(merged, "asset", ""));
        if (!existingIcon.startsWith("/uploads/topology-icons/")) {
            merged.put("iconPath", defaultIconPath(type));
            merged.put("asset", defaultIconPath(type));
        }
        if (text(merged, "iconHash", "").isBlank()) merged.put("iconHash", iconHash(fileName(defaultIconPath(type))));
        List<String> expectedPorts = defaultPorts(type);
        String profileId = text(merged, "portProfileId", "");
        boolean canonicalDefaultProfile = profileId.isBlank() || profileId.equals(defaultProfileId(type));
        if (singleInterfaceType(type) || portsList(merged).isEmpty() || (canonicalDefaultProfile
                && portsList(merged).size() != expectedPorts.size())) {
            merged.put("ports", expectedPorts);
        }
        if (singleInterfaceType(type)) {
            merged.put("portProfileId", defaultProfileId(type));
            merged.put("portProfileName", defaultProfileName(type, expectedPorts.size()));
        } else {
            merged.putIfAbsent("portProfileId", defaultProfileId(type));
            merged.putIfAbsent("portProfileName", defaultProfileName(type, portsList(merged).size()));
        }
        merged.put("defaultPortCount", portsList(merged).size());
        return merged;
    }

    private boolean sameResourcePayload(Map<String, Object> left, Map<String, Object> right) {
        return text(left, "type", "").equals(text(right, "type", ""))
                && text(left, "asset", "").equals(text(right, "asset", ""))
                && portsList(left).equals(portsList(right))
                && number(left.get("defaultPortCount"), 0) == number(right.get("defaultPortCount"), 0)
                && text(left, "portProfileId", "").equals(text(right, "portProfileId", ""));
    }

    private List<Map<String, Object>> deduplicateResources(List<Map<String, Object>> resources) {
        Map<String, Map<String, Object>> unique = new LinkedHashMap<>();
        for (Map<String, Object> resource : resources) {
            String type = canonicalType(resource);
            if (type.isBlank()) continue;
            Map<String, Object> current = unique.get(type);
            if (current == null || (type.equals(text(resource, "key", "")) && !type.equals(text(current, "key", "")))) {
                Map<String, Object> normalized = new LinkedHashMap<>(resource);
                normalized.put("type", type);
                if (text(normalized, "asset", "").isBlank()) {
                    normalized.put("asset", defaultIconPath(type));
                    normalized.put("iconPath", defaultIconPath(type));
                }
                if (singleInterfaceType(type) || portsList(normalized).isEmpty()) {
                    normalized.put("ports", defaultPorts(type));
                    normalized.put("portProfileId", defaultProfileId(type));
                    normalized.put("portProfileName", defaultProfileName(type, 1));
                    normalized.put("defaultPortCount", 1);
                }
                unique.put(type, normalized);
            }
        }
        return unique.values().stream()
                .sorted(Comparator.comparingInt(resource -> number(resource.get("order"), 100)))
                .toList();
    }

    private List<Map<String, Object>> defaultResources() {
        return List.of(
                resource("router", "路由器", "Router", "网络设备", 10, defaultPorts("router")),
                resource("core-switch", "核心交换机", "Core Switch", "网络设备", 20, defaultPorts("core-switch")),
                resource("layer3-switch", "三层交换机", "Layer 3 Switch", "网络设备", 30, defaultPorts("layer3-switch")),
                resource("access-switch", "接入交换机", "Access Switch", "网络设备", 40, defaultPorts("access-switch")),
                resource("firewall", "防火墙", "Firewall", "安全设备", 50, defaultPorts("firewall")),
                resource("ids-ips", "IDS / IPS", "IDS / IPS", "安全设备", 60, defaultPorts("ids-ips")),
                resource("vpn-gateway", "VPN 网关", "VPN Gateway", "安全设备", 70, defaultPorts("vpn-gateway")),
                resource("wireless-ap", "无线 AP", "Wireless AP", "无线设备", 80, defaultPorts("wireless-ap")),
                resource("ac-controller", "AC 控制器", "AC Controller", "无线设备", 90, defaultPorts("ac-controller")),
                resource("server", "服务器", "Server", "服务器与存储", 100, defaultPorts("server")),
                resource("database", "数据库", "Database", "服务器与存储", 110, defaultPorts("database")),
                resource("nas", "NAS", "NAS", "服务器与存储", 120, defaultPorts("nas")),
                resource("pc", "PC", "PC", "终端设备", 130, defaultPorts("pc")),
                resource("laptop", "笔记本", "Laptop", "终端设备", 140, defaultPorts("laptop")),
                resource("printer", "打印机", "Printer", "终端设备", 150, defaultPorts("printer")),
                resource("ip-phone", "IP 电话", "IP Phone", "终端设备", 160, defaultPorts("ip-phone")),
                resource("camera", "摄像头", "Camera", "终端设备", 170, defaultPorts("camera")),
                resource("internet", "Internet", "Internet", "其他", 180, defaultPorts("internet")),
                resource("cloud", "Cloud", "Cloud", "其他", 190, defaultPorts("cloud"))
        );
    }

    private Map<String, Object> resource(String type, String name, String englishName, String category, int order, List<String> ports) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("type", type);
        resource.put("name", name);
        resource.put("englishName", englishName);
        resource.put("vendor", "TOPOPLAN");
        resource.put("category", category);
        resource.put("format", "SVG");
        resource.put("tone", "slate");
        resource.put("size", "48 × 48");
        resource.put("enabled", true);
        resource.put("allowAi", true);
        resource.put("draggable", true);
        resource.put("order", order);
        resource.put("ports", new ArrayList<>(ports));
        resource.put("portProfileId", type + "-default");
        resource.put("portProfileName", defaultProfileName(type, ports.size()));
        resource.put("defaultPortCount", ports.size());
        resource.put("asset", defaultIconPath(type));
        resource.put("iconPath", defaultIconPath(type));
        resource.put("iconHash", iconHash(fileName(defaultIconPath(type))));
        return resource;
    }
}
