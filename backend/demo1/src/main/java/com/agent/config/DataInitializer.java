package com.agent.config;

import com.agent.knowledge.KnowledgeBaseService;
import com.agent.ai.AiModelService;
import com.agent.ai.AiConversation;
import com.agent.ai.AiConversationRepository;
import com.agent.project.Project;
import com.agent.project.ProjectRepository;
import com.agent.subscription.SubscriptionService;
import com.agent.user.User;
import com.agent.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class DataInitializer {
    @Bean
    CommandLineRunner seedAdmin(UserRepository userRepository, ProjectRepository projectRepository, AiConversationRepository conversationRepository, PasswordEncoder passwordEncoder,
                                KnowledgeBaseService knowledgeBaseService, AiModelService aiModelService,
                                SubscriptionService subscriptionService,
                                @Value("${topo.bootstrap.enabled:true}") boolean enabled,
                                @Value("${TOPO_ADMIN_PASSWORD:}") String adminPassword,
                                @Value("${topo.knowledge.seed-files:../../1.txt,../../网络拓扑工程规划辅助系统详细设计.md}") String seedFiles) {
        return args -> {
            subscriptionService.migrateExistingUsersAndPlans();
            migrateProjectPublicIds(projectRepository);
            migrateConversationPublicIds(conversationRepository);
            aiModelService.repairKnownModelMetadata();
            if (!enabled) {
                return;
            }
            User admin = userRepository.findByUsernameIgnoreCase("admin")
                    .orElseGet(() -> {
                        if (adminPassword == null || adminPassword.isBlank()) {
                            throw new IllegalStateException("首次启动请设置 TOPO_ADMIN_PASSWORD");
                        }
                        return new User("admin", passwordEncoder.encode(adminPassword), "系统管理员", "admin@topoplan.local", "ADMIN");
                    });
            if (!"ADMIN".equals(admin.getRole())) {
                admin.setRole("ADMIN");
            }
            userRepository.save(admin);

            subscriptionService.initializePlansAndMigrateUsers();
            List<KnowledgeBaseService.SeedDocument> seeds = new ArrayList<>();
            for (String configuredFile : seedFiles.split(",")) {
                Path file = Path.of(System.getProperty("user.dir")).resolve(configuredFile.trim()).normalize();
                if (!Files.isRegularFile(file)) continue;
                String name = file.getFileName().toString();
                String format = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
                String category = name.endsWith("1.txt") ? "网络基础" : "网络设计案例";
                seeds.add(new KnowledgeBaseService.SeedDocument(name, category, format, Files.readString(file, StandardCharsets.UTF_8)));
            }
            knowledgeBaseService.seedIfEmpty(seeds);
            knowledgeBaseService.ensureTextDocument(
                    "TopoPlan-无线双AC与服务器区架构规则.md",
                    "网络架构规范",
                    "# 无线双 AC 与服务器区架构规则\n\n"
                            + "## 无线控制器高可用\n"
                            + "- 一个 AC 必须同时连接两台核心或交换机，不能只连接单台核心。\n"
                            + "- 采用两个 AC 时，AC-01 放在核心对左上方，AC-02 放在核心对右上方。两台 AC 都必须分别连接核心交换机 A 和核心交换机 B，形成双 AC 冗余。\n"
                            + "- 双核心之间使用至少两条不同物理端口的链路，并配置同一个链路聚合组。\n\n"
                            + "## 服务器区分层\n"
                            + "- 服务器、数据库、NAS 不应直接连接核心交换机或防火墙。\n"
                            + "- 服务器先连接服务器区接入或汇聚交换机，再由服务器区交换机双上联核心。\n"
                            + "- 服务器区交换机名称应明确包含服务器区、机房或 Server 语义，便于拓扑识别和自动布局。\n"
                            + "- 所有规划必须只使用管理端启用且允许 AI 使用的真实拓扑资源和真实端口。\n"
            );
        };
    }
    private void migrateProjectPublicIds(ProjectRepository repository) {
        var projects = repository.findAll();
        var changed = new ArrayList<Project>();
        for (Project project : projects) {
            String before = project.getPublicId();
            project.ensurePublicId();
            if (!java.util.Objects.equals(before, project.getPublicId())) changed.add(project);
        }
        if (!changed.isEmpty()) repository.saveAll(changed);
    }

    private void migrateConversationPublicIds(AiConversationRepository repository) {
        var conversations = repository.findAll();
        var changed = new ArrayList<AiConversation>();
        for (AiConversation conversation : conversations) {
            String before = conversation.getPublicId();
            String beforeToken = conversation.getPublicToken();
            conversation.ensurePublicId();
            conversation.ensurePublicToken();
            if (!java.util.Objects.equals(before, conversation.getPublicId()) || !java.util.Objects.equals(beforeToken, conversation.getPublicToken())) changed.add(conversation);
        }
        if (!changed.isEmpty()) repository.saveAll(changed);
    }

}
