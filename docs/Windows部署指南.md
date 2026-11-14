# Windows 部署：启动 Spring Boot 和 Vue

## 1. 安装工具
安装完整 JDK 21（含 javac）、Apache Maven 3.9 或更高的 3.x 版本、Node.js 22.12+ 和 MySQL 8。Vue 与 Vite 由项目的 npm 锁文件安装，不需要全局安装 Vue CLI。MySQL 服务可在本机服务面板中启动。

在 Windows 开始菜单搜索“编辑帐户的环境变量”。新增 JAVA_HOME，值选实际 JDK 根目录（例如 C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot）；新增 MAVEN_HOME，值选 Maven 解压目录。两者都指向包含 bin 的文件夹。编辑用户 Path，分别添加 %JAVA_HOME%\bin、%MAVEN_HOME%\bin 和 Node.js 安装目录。重新打开 PowerShell，逐条验证：

~~~powershell
java -version
javac -version
mvn -v
node -v
npm.cmd -v
~~~

前三项中的 Java 均应为 21。若 Maven 报“不支持发行版本 21”，检查 mvn -v 的 Java home；它可能与 java -version 不同。

## 2. 准备数据库与私有配置
在新 MySQL 实例中使用管理客户端执行 database/schema.sql，创建 topo 数据库和空表。已有同名业务库时请使用独立实例。为应用配置对该库具有必要建表、读写权限的数据库账号。在仓库根目录运行：

~~~powershell
Copy-Item .env.example .env
notepad .env
~~~

按示例填写实际数据库地址、用户和密码（TOPO_DB_URL、TOPO_DB_USERNAME、TOPO_DB_PASSWORD）。分别生成独立随机值作为 TOPO_CONFIG_ENCRYPTION_KEY 和 TOPO_JWT_SECRET，后者至少 32 字符。TOPO_ADMIN_PASSWORD 为首次初始化的管理员密码。各项一行，等号后不加引号。真实 .env 仅留在运行设备。

要启用 AI，在 .env 内设置 TOPO_MODEL_KEY_PRIMARY 为真实供应商密钥。启动后进入 /admin，用 admin 和自设密码登录，新增对应供应商的 HTTPS 地址、模型 ID 和任务绑定，Key 栏填写 **env:TOPO_MODEL_KEY_PRIMARY**。选择测试连接并启用。密钥仅在后端解析，不能放入以 VITE_ 开头的前端变量。

## 3. 启动两端
在仓库根目录双击“启动TopoPlan.bat”。它读取 .env，检查工具，并启动后端与前端。首次运行需联网下载依赖。打开 http://localhost:5173/ 。后端默认监听本机 8080，Vue 默认监听 5173，Vite 把 /api 和 /uploads 转发给后端。

需要分开启动时，在仓库根目录打开两个 PowerShell。第一个窗口执行：
~~~powershell
Get-Content -LiteralPath .env -Encoding UTF8 | ForEach-Object {
    if ($_ -match '^\s*([A-Z][A-Z0-9_]*)=(.*)$') {
        [Environment]::SetEnvironmentVariable($matches[1], $matches[2].Trim(), 'Process')
    }
}
Set-Location backend\demo1
mvn spring-boot:run
~~~
第二个窗口执行：
~~~powershell
Set-Location frontend
npm.cmd ci
npm.cmd run dev -- --host 127.0.0.1 --port 5173 --strictPort
~~~

## 4. 怎样判断启动成功
浏览器打开 http://127.0.0.1:8080/api/auth/csrf 应返回含 data 的 JSON。在 http://localhost:5173/api/auth/csrf 也应看到该结构，说明 Vue 能连到 Spring Boot。打开首页、注册测试账号并新建项目，保存后刷新确认项目仍在。AI 功能另用自有渠道发送一次真实请求，核对完成状态。
若 8080 已占用，先确认占用者，避免误测旧 JAR。手动启动时也可在后端窗口设置 $env:PORT='8081'，在前端窗口设置 $env:VITE_API_PROXY_TARGET='http://127.0.0.1:8081' 后再启动。数据库连接失败先核对 MySQL 服务、库名和账号权限。单独启动的两个终端均可用 Ctrl+C 停止。

## 5. 构建和复测
前端目录执行 npm.cmd test、npm.cmd run verify:composer-stability、npm.cmd run build，静态文件位于 dist。后端目录执行 mvn test、mvn package，JAR 位于 target；直接运行 JAR 也需先设置同样的后端环境变量。生产部署可用 Nginx 托管 dist，将 /api 和 /uploads 转发至 Spring Boot；配置 HTTPS，并为前端路由设置 index.html 回退。参见 docs/云端部署.md。
