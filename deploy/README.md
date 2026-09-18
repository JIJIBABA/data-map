# data-map 部署指南

## 原理

一个 `java -jar` 进程同时提供前端页面 + 后端 API，**只需 JDK，不用 Nginx，不用 Node**。

```
浏览器 → http://12.0.216.77:8966/
            ├─ /              → 前端页面（dist 静态文件）
            └─ /api/*         → 后端接口
         （同一个 Spring Boot 进程）
```

---

## 首次部署（4 步）

### 第 1 步：装 JDK（只需一次）

SSH 登录 `12.0.216.77`（scfadmin 账号），执行：
```bash
java -version
```
- 显示 `1.8.x` → 已有，跳过
- `command not found` → 装一下：
```bash
# CentOS
sudo yum install -y java-1.8.0-openjdk
# Ubuntu
sudo apt install -y openjdk-8-jdk
```

### 第 2 步：开防火墙端口（只需一次，需 sudo）
```bash
sudo firewall-cmd --add-port=8966/tcp --permanent
sudo firewall-cmd --reload
```
> 提示 `command not found` 就跳过（没开防火墙）

### 第 3 步：上传并解压部署包

开发机上的部署包：`D:\self\data-map\datamap-deploy.zip`

用 WinSCP / Xftp 传到服务器 `/home/scfadmin/`，然后：
```bash
cd ~
unzip datamap-deploy.zip -d datamap-deploy
cd datamap-deploy
```
> 没有 unzip：`sudo yum install -y unzip`

### 第 4 步：一键部署
```bash
bash deploy-datamap.sh
```
脚本自动：检查 JDK → 放文件 → 启动 → 验证（含静态资源白屏检测）。最后打印"部署完成"。

## 验证

浏览器开 **http://12.0.216.77:8966/**

⚠️ **如果白屏，按 `Ctrl + Shift + R` 强制刷新**（浏览器缓存了旧页面，最常见）。还不行就 F12 → Network 看有没有 404，Console 看有没有报错。

看到数据地图页面 + 表列表 = 成功 🎉

---

## 更新部署（代码改了重新发）

详见 `D:\self\data-map\docs\更新部署流程.md`，核心四步：

```bash
# 1. 停旧
pkill -f data-map-backend-1.0.0.jar; sleep 2

# 2. 换文件（按需）
#   后端: cp /home/scfadmin/data-map-backend-1.0.0.jar ~/datamap/backend/
#   前端: rm -rf ~/datamap/frontend/dist && cp -r /home/scfadmin/dist ~/datamap/frontend/dist

# 3. 启新
nohup java -jar ~/datamap/backend/data-map-backend-1.0.0.jar \
  --spring.config.additional-location=file:$HOME/datamap/config/ \
  --spring.profiles.active=prod \
  > ~/datamap/backend/backend.log 2>&1 &

# 4. 验证 + 浏览器 Ctrl+Shift+R
sleep 15; tail -30 ~/datamap/backend/backend.log
```

> 只更新前端时，**不用停进程重启**，直接换 dist，浏览器强刷即可。

---

## 常见问题

### 1. 浏览器白屏
**99% 是缓存**。`Ctrl + Shift + R` 强刷。还不行：F12 → Network 看静态资源是否 404（若 404 是 SPA 兜底拦截问题，联系开发）。

### 2. 浏览器打不开（转圈/拒绝）
防火墙没开 8966：
```bash
sudo firewall-cmd --add-port=8966/tcp --permanent && sudo firewall-cmd --reload
```

### 3. 页面能开但没数据/接口报错
后端连不上数据库。看日志：
```bash
tail -50 ~/datamap/backend/backend.log
```
看到 `Connection refused` / `Communications link failure` → 连不上 MySQL(12.0.221.123:3311)。

### 4. 首页 404
`~/datamap/config/application-prod.yml` 里的 `datamap.web-root` 路径不对。检查：
```bash
grep web-root ~/datamap/config/application-prod.yml
ls ~/datamap/frontend/dist/index.html
```
web-root 必须指向 dist 的绝对路径（scfadmin 部署时是 `/home/scfadmin/datamap/frontend/dist`）。

### 5. 重启服务器后服务没了
手动起：
```bash
nohup java -jar ~/datamap/backend/data-map-backend-1.0.0.jar \
  --spring.config.additional-location=file:$HOME/datamap/config/ \
  --spring.profiles.active=prod \
  > ~/datamap/backend/backend.log 2>&1 &
```

---

## 文件位置

| 内容 | 路径 |
|------|------|
| 后端 jar | `~/datamap/backend/data-map-backend-1.0.0.jar` |
| 后端日志 | `~/datamap/backend/backend.log` |
| 后端配置 | `~/datamap/config/application-prod.yml` |
| 前端文件 | `~/datamap/frontend/dist/` |
| 访问地址 | `http://12.0.216.77:8966/` |
