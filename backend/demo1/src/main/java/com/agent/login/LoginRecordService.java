package com.agent.login;

import com.agent.user.User;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LoginRecordService {
    private static final ZoneId APPLICATION_ZONE = ZoneId.of("Asia/Shanghai");
    private final LoginRecordRepository repository;

    public LoginRecordService(LoginRecordRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccessfulLogin(User user, String loginIdentifier, LoginRequestMetadata metadata) {
        ClientDetails client = ClientDetails.from(metadata.userAgent());
        repository.save(new LoginRecord(user.getId(), safe(loginIdentifier, 180), safe(user.getDisplayName(), 120),
                accountType(user), loginMethod(user), safe(metadata.ipAddress(), 64), "未知地区", client.device(),
                client.operatingSystem(), client.browser(), safe(metadata.userAgent(), 4000), "成功", "正常", null));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedLogin(String loginIdentifier, User user, LoginRequestMetadata metadata, String failureReason) {
        recordFailedLogin(loginIdentifier, user, metadata, failureReason, "正常");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedLogin(String loginIdentifier, User user, LoginRequestMetadata metadata, String failureReason, String riskStatus) {
        ClientDetails client = ClientDetails.from(metadata.userAgent());
        repository.save(new LoginRecord(user == null ? null : user.getId(), safe(loginIdentifier, 180),
                user == null ? safe(loginIdentifier, 120) : safe(user.getDisplayName(), 120),
                user == null ? "未知账号" : accountType(user), user == null ? "账号密码" : loginMethod(user),
                safe(metadata.ipAddress(), 64), "未知地区", client.device(), client.operatingSystem(), client.browser(),
                safe(metadata.userAgent(), 4000), "失败", safe(riskStatus, 16), safe(failureReason, 255)));
    }

    @Transactional(readOnly = true)
    public Page<LoginRecordResponse> list(int page, int size, String keyword, Long userId, String accountType,
                                          String result, String riskStatus, String loginMethod,
                                          Instant startAt, Instant endAt) {
        int normalizedSize = Math.min(100, Math.max(1, size));
        return repository.findAll(specification(keyword, userId, accountType, result, riskStatus, loginMethod, startAt, endAt),
                        PageRequest.of(Math.max(0, page), normalizedSize, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(LoginRecordResponse::from);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> statistics() {
        Instant startOfToday = ZonedDateTime.now(APPLICATION_ZONE).toLocalDate().atStartOfDay(APPLICATION_ZONE).toInstant();
        Map<String, Long> statistics = new LinkedHashMap<>();
        statistics.put("today", repository.countByCreatedAtGreaterThanEqual(startOfToday));
        statistics.put("success", repository.countByResult("成功"));
        statistics.put("failure", repository.countByResult("失败"));
        statistics.put("risk", repository.countByRiskStatusNot("正常"));
        return statistics;
    }

    private Specification<LoginRecord> specification(String keyword, Long userId, String accountType, String result,
                                                     String riskStatus, String loginMethod, Instant startAt, Instant endAt) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("loginIdentifier")), pattern),
                        builder.like(builder.lower(root.get("displayName")), pattern),
                        builder.like(builder.lower(root.get("ipAddress")), pattern),
                        builder.like(root.get("userId").as(String.class), pattern)
                ));
            }
            if (userId != null) predicates.add(builder.equal(root.get("userId"), userId));
            addEquals(predicates, builder, root, "accountType", accountType);
            addEquals(predicates, builder, root, "result", result);
            addEquals(predicates, builder, root, "riskStatus", riskStatus);
            addEquals(predicates, builder, root, "loginMethod", loginMethod);
            if (startAt != null) predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), startAt));
            if (endAt != null) predicates.add(builder.lessThan(root.get("createdAt"), endAt));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void addEquals(List<Predicate> predicates, jakarta.persistence.criteria.CriteriaBuilder builder,
                           jakarta.persistence.criteria.Root<LoginRecord> root, String field, String value) {
        if (value != null && !value.isBlank() && !"全部".equals(value)) {
            predicates.add(builder.equal(root.get(field), value.trim()));
        }
    }

    private static String accountType(User user) {
        return "ADMIN".equalsIgnoreCase(user.getRole()) ? "后台管理员" : "普通用户";
    }

    private static String loginMethod(User user) {
        return "ADMIN".equalsIgnoreCase(user.getRole()) ? "管理员登录" : "账号密码";
    }

    private static String safe(String value, int maxLength) {
        if (value == null || value.isBlank()) return "未知";
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private record ClientDetails(String device, String operatingSystem, String browser) {
        private static final Pattern ANDROID = Pattern.compile("Android\\s+([^;)]+)", Pattern.CASE_INSENSITIVE);
        private static final Pattern IOS = Pattern.compile("(?:iPhone|CPU) OS\\s+([0-9_]+)", Pattern.CASE_INSENSITIVE);
        private static final Pattern MAC = Pattern.compile("Mac OS X\\s+([0-9_.]+)", Pattern.CASE_INSENSITIVE);
        private static final Pattern VERSION = Pattern.compile("%s/([0-9.]+)", Pattern.CASE_INSENSITIVE);

        static ClientDetails from(String userAgent) {
            String agent = userAgent == null ? "" : userAgent;
            String operatingSystem = operatingSystem(agent);
            String browser = browser(agent);
            return new ClientDetails(osFamily(operatingSystem) + " / " + browserFamily(browser), operatingSystem, browser);
        }

        private static String operatingSystem(String agent) {
            Matcher android = ANDROID.matcher(agent);
            if (android.find()) return "Android " + android.group(1);
            Matcher ios = IOS.matcher(agent);
            if (ios.find()) return "iOS " + ios.group(1).replace('_', '.');
            Matcher mac = MAC.matcher(agent);
            if (mac.find()) return "macOS " + mac.group(1).replace('_', '.');
            if (agent.contains("Windows NT 10.0")) return "Windows 10/11";
            if (agent.contains("Windows NT 6.3")) return "Windows 8.1";
            if (agent.contains("Windows NT 6.1")) return "Windows 7";
            if (agent.contains("Linux")) return "Linux";
            return "未知操作系统";
        }

        private static String browser(String agent) {
            String edge = version(agent, "Edg");
            if (edge != null) return "Microsoft Edge " + edge;
            String opera = version(agent, "OPR");
            if (opera != null) return "Opera " + opera;
            String firefox = version(agent, "Firefox");
            if (firefox != null) return "Firefox " + firefox;
            String chrome = version(agent, "Chrome");
            if (chrome != null) return "Google Chrome " + chrome;
            String safari = version(agent, "Version");
            if (safari != null && agent.contains("Safari")) return "Safari " + safari;
            return "未知浏览器";
        }

        private static String version(String agent, String token) {
            Matcher matcher = Pattern.compile(String.format(VERSION.pattern(), Pattern.quote(token)), Pattern.CASE_INSENSITIVE).matcher(agent);
            return matcher.find() ? matcher.group(1) : null;
        }

        private static String osFamily(String operatingSystem) {
            int index = operatingSystem.indexOf(' ');
            return index < 0 ? operatingSystem : operatingSystem.substring(0, index);
        }

        private static String browserFamily(String browser) {
            if (browser.startsWith("Microsoft Edge")) return "Edge";
            if (browser.startsWith("Google Chrome")) return "Chrome";
            if (browser.startsWith("未知")) return "未知浏览器";
            int index = browser.indexOf(' ');
            return index < 0 ? browser : browser.substring(0, index);
        }
    }
}
