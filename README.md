# TopoPlan 网络拓扑工程规划辅助系统

TopoPlan 面向办公网、园区网等网络工程规划场景，将拓扑编辑、项目资料、AI 问答、结构化规划和规则检查整合到同一个项目工作区。AI 生成结果需经过资源、端口与链路校验，并由用户确认后应用。

## 功能
- 用户注册登录、项目与会话隔离、项目文件管理。
- 拖拽拓扑编辑、设备与连线属性编辑、保存和版本管理。
- 项目 AI 助手、任务路由、流式回复、模型渠道管理和配额。
- 知识文档分块、关键词检索和上下文注入。
- 确定性拓扑检查，发现端口占用、失效连接及架构风险。
- 管理端提供平台统计、模型、知识库、规则与用户配额管理。

## 技术架构
浏览器 Vue 3 / Vite → Spring Boot 3.5 / Java 21 REST 与 SSE → MySQL。
AI 经 AiOrchestrator、PromptRuntimeService、AiModelRouter、AiGatewayService 调用已配置的外部模型。Spring Security 负责访问控制，JPA 管理持久化。当前知识检索为词项匹配打分，未实现真实向量数据库召回；不宣称自研或训练了大模型。

## 目录
```
frontend/                 Vue 前端与 103 项检查
backend/demo1/            Spring Boot 后端与自动化测试
database/schema.sql      21 张表的纯结构，不包含业务数据或密钥
docs/                    环境、AI、验收案例、技术文档和提交说明
.env.example             环境配置模板
start-topoplan.ps1        Windows 启动与就绪检查
PROVENANCE.md            源码及 Git 历史来源说明
```

## 环境
JDK 21、Node.js 22.12+（或 20.19+）、npm、Maven 3.9+、MySQL 8.0+。首次安装依赖需要联网。前端依赖以 package-lock.json 为准；后端由 Spring Boot 父 POM 锁定依赖版本。

## Windows 安装启动
1. 将整个项目解压到有写权限的本地目录。
2. 在 MySQL 执行 database/schema.sql，仅创建空 topo 数据库和表；已有业务库请勿重复导入。可另建数据库并通过 TOPO_DB_URL 指定。
3. 复制 .env.example 为 .env，填写数据库密码、随机加密密钥、至少 32 字符的 JWT 密钥和管理员初始密码。不要在值两侧添加引号。加密密钥首次设置后需妥善保管，变更后旧模型密钥无法直接解密。
4. 双击 启动TopoPlan.bat。启动器读取 .env，检查依赖、安装前端依赖、启动服务并检查就绪状态。
5. 浏览器打开 http://localhost:5173/；管理员入口 http://localhost:5173/admin。首次管理员用户名 admin，密码为自行填写的 TOPO_ADMIN_PASSWORD。普通用户通过注册页面创建账号。
6. 管理端新增真实模型渠道，填写供应商提供的 Base URL、模型 ID、API Key，完成连接测试并启用任务绑定。无模型密钥时仍可验证手动拓扑和账号功能，AI 生成功能不能离线模拟通过。

## 手动启动与跨平台
先将 .env 中变量导出到当前进程环境；后端不自动加载该文件。终端一：进入 backend/demo1 后执行 mvn spring-boot:run。终端二：进入 frontend，执行 npm ci 和 npm run dev。Linux/macOS 使用相同命令并自行设置环境变量。Vite 的 /api 与 /uploads 默认转发到 127.0.0.1:8080；代理目标可用 VITE_API_PROXY_TARGET 覆盖。

## 验证
```
cd frontend
npm ci
npm test
npm run verify:composer-stability
npm run build
```
后端在 backend/demo1 执行 mvn test。测试使用 H2 内存库及仅用于测试的账号密钥，不连接真实业务数据库。测试详情见 docs/验证报告.md。真实供应商调用与前端整页交互需另外验收，不能由单元测试替代。

## AI 故障排查
查看 backend/demo1/logs/topoplan-backend.log。密钥无效先检查模型渠道；超时核对后端网络；没有任务模型检查渠道启用状态与绑定。提交版修正了 Markdown 分隔线与零填充地址的重复内容误判，并补齐意外流式异常的失败反馈。修改源码后必须重启后端才会加载新类。

## 团队信息
团队名称：工作室在逃大学生。所属院校：四川工商学院。参赛组别：B组。团队成员：杨淑琼、游国立、龚胜岚。

| 成员 | 分工安排 |
| --- | --- |
| 游国立 | 技术负责人，统筹网页代码、前后端接口、数据库与数据管理，负责代码整合和部署 |
| 龚胜岚 | 拓扑交互、AI 助手接入与联调、回归测试及作品打包 |
| 杨淑琼 | 需求分析、技术文档与使用说明、功能验收和材料核对 |

仓库地址：https://github.com/gongshenglan6-crypto/TopoPlan 。详见 docs/团队信息.md。项目整理者标识 Submission Maintainer 仅用于本次本地材料提交记录，不代表团队成员身份。

## 提交与演示
PDF 技术文档位于 docs/TopoPlan技术文档.pdf，视频按用户要求未制作。在线演示为可选加分项；localhost 不是评审可访问地址。仓库发布步骤与赛前核对清单见 docs/提交清单.md。源码包含 Git 元数据；另有 Git bundle 可恢复记录。未附带原始含个人数据的 SQL、任何外部模型密钥或用户会话。

## 云端演示部署

已提供 Dockerfile、Railway 部署配置和页面路由适配，新增两项测试后后端共 57 项通过。部署步骤与限制见 docs/云端部署.md。当前 Railway 工作区限制阻止创建网站服务，尚未提供公网演示地址。
