#!/bin/bash
# data-map 部署脚本（backend + frontend，同源 Nginx 反代）
# 在物理机 12.0.216.77 上以 root 执行
# 用法: sudo bash deploy-datamap.sh
#
# 前置: 把这两个产物放到脚本同目录:
#   data-map-backend-1.0.0.jar   (后端 fat jar)
#   dist/                         (前端构建产物目录)
# 配置文件放同目录的 config/:
#   config/application-prod.yml
#   nginx-datamap.conf

set -e
APP=/opt/data-map

echo "===== 1. 建目录 ====="
mkdir -p $APP/backend $APP/frontend $APP/config

echo "===== 2. 部署后端 ====="
cp -f data-map-backend-1.0.0.jar $APP/backend/
cp -f config/application-prod.yml $APP/config/

echo "===== 3. 部署前端 ====="
rm -rf $APP/frontend/dist
cp -rf dist $APP/frontend/dist

echo "===== 4. 配置 Nginx ====="
cp -f nginx-datamap.conf /etc/nginx/conf.d/datamap.conf
nginx -t
systemctl reload nginx || systemctl restart nginx
echo "Nginx 已 reload，监听 :8966"

echo "===== 5. 启动/重启后端 ====="
# 停掉旧进程
pkill -f 'data-map-backend-1.0.0.jar' 2>/dev/null || true
sleep 2
nohup java -jar $APP/backend/data-map-backend-1.0.0.jar \
  --spring.config.additional-location=file:$APP/config/ \
  --spring.profiles.active=prod \
  > $APP/backend/backend.log 2>&1 &
echo "后端启动中，PID=$!"
sleep 8

echo "===== 6. 验证 ====="
echo "--- 后端 ---"
curl -s -o /dev/null -w "backend :8967 -> HTTP %{http_code}\n" http://127.0.0.1:8967/api/projects
echo "--- 前端 ---"
curl -s -o /dev/null -w "frontend :8966 -> HTTP %{http_code}\n" http://127.0.0.1:8966/
echo "--- 前端调 API（经 Nginx 反代）---"
curl -s -o /dev/null -w "proxy /api -> HTTP %{http_code}\n" http://127.0.0.1:8966/api/projects

echo ""
echo "===== 部署完成 ====="
echo "访问地址: http://12.0.216.77:8966/"
echo "后端日志: tail -f $APP/backend/backend.log"
