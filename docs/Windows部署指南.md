# TopoPlan Windows 部署指南

本指南从本源码根目录启动。请先将整个提交目录复制到本机可写磁盘，首次安装依赖需要联网。

## 环境准备
安装 JDK 21（含 javac）、Maven 3.9.x、Node.js 22.12+、MySQL 8。用 java -version、javac -version、mvn -v、node -v、npm.cmd -v 验证；Maven使用的Java必须为21。

## 初始化数据库和配置
在隔离测试数据库执行 database/schema.sql，创建 topo 空库及表；不要在已有业务库盲目执行。外层 database/topo.sql 同样为空库结构，选择一份执行即可。
在提交目录打开 PowerShell：
```powershell
Copy-Item -LiteralPath '.env.example' -Destination '.env'
notepad .env
```
若 .env 已存在，直接编辑，不覆盖。填写 TOPO_DB_URL、TOPO_DB_USERNAME、TOPO_DB_PASSWORD、TOPO_CONFIG_ENCRYPTION_KEY、TOPO_JWT_SECRET、TOPO_ADMIN_PASSWORD。两个密钥分别随机生成，JWT至少32字符；加密密钥首次配置后妥善保存。等号后不加引号。真实 .env 只留运行电脑。

## 启动和访问
在 TopoPlan源码 内双击 启动TopoPlan.bat，或执行：
```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File './start-topoplan.ps1'
```
前台 http://localhost:5173/，后台 http://localhost:5173/admin。首次管理员用户名 admin，密码为自设 TOPO_ADMIN_PASSWORD。localhost只能本机访问。
后端默认127.0.0.1:8080，Vite将/api和/uploads转发到后端。不要用jwebserver直接托管网页后认定业务已接通，它不提供接口代理和SPA回退。

## 手动启动
在 TopoPlan源码 打开第一个PowerShell，执行：
```powershell
Get-Content -LiteralPath '.env' -Encoding UTF8 | ForEach-Object {
  if ($_ -match '^\s*([A-Z][A-Z0-9_]*)=(.*)$') {
    [Environment]::SetEnvironmentVariable($matches[1], $matches[2].Trim(), 'Process')
  }
}
Set-Location -LiteralPath './backend/demo1'
mvn spring-boot:run
```
从 TopoPlan源码 打开第二个窗口：
```powershell
Set-Location -LiteralPath './frontend'
npm.cmd ci
npm.cmd run dev -- --host 127.0.0.1 --port 5173 --strictPort
```
保持服务窗口运行，使用Ctrl+C停止。端口被占用先确认占用程序，避免误测旧服务。

## AI与验收
设置服务端 TOPO_MODEL_KEY_PRIMARY 为真实供应商密钥；管理端新增供应商HTTPS地址、真实模型ID及任务绑定，Key填写 env:TOPO_MODEL_KEY_PRIMARY，测试连接后启用。更改环境变量后重启后端；缺少渠道时AI不可用。
检查 http://127.0.0.1:8080/api/auth/csrf 与 http://localhost:5173/api/auth/csrf 均返回含data的JSON；再注册测试账号、新建拓扑、保存刷新，验证真实AI流式回复及拓扑应用确认。
前端执行 npm.cmd test、npm.cmd run verify:composer-stability、npm.cmd run build；后端执行 mvn test、mvn package。以实际日志为准，本次材料修改未替代团队业务验收。

## 运行快照和公网评审
www/wwwroot/topo 与 www/wwwroot/topoplan/app.jar 是保留的运行快照，与源码不保证一致，复现优先从源码构建。配置已脱敏，必须设置自有环境变量。生产部署需HTTPS、接口反向代理和SPA回退，见源码deploy及docs资料。
异地评审需团队提供已验收的公网入口和权限受限的评审账号。本次不声明旧公网服务可用。真实数据库、上传内容和凭据不随公开材料提交。
