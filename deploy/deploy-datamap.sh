#!/bin/bash
# ============================================================
# data-map 一键部署脚本（方案：后端托管前端，无需 Nginx，无需 root）
# 用法: bash deploy-datamap.sh
# 部署后访问 http://12.0.216.77:8966/
# ============================================================
# 不用 set -e，改用显式错误检查，避免某步非关键失败导致整体退出

GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[0;33m'; NC='\033[0m'
ok(){ echo -e "${GREEN}[✓]${NC} $1"; }
fail(){ echo -e "${RED}[✗]${NC} $1"; exit 1; }
info(){ echo -e "${YELLOW}[!]${NC} $1"; }

APP="$HOME/datamap"
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
cd "$SCRIPT_DIR"

echo "========== 0. 环境检查 =========="

if command -v java >/dev/null 2>&1; then
  JV=$(java -version 2>&1 | head -1)
  ok "Java 已安装: $JV"
else
  fail "Java 未安装！请先装 JDK 8:
    CentOS:  yum install -y java-1.8.0-openjdk
    Ubuntu:  apt install -y openjdk-8-jdk"
fi

[ -f "$SCRIPT_DIR/data-map-backend-1.0.0.jar" ] || fail "缺少 data-map-backend-1.0.0.jar"
[ -d "$SCRIPT_DIR/dist" ] || fail "缺少 dist 前端目录"
[ -f "$SCRIPT_DIR/config/application-prod.yml" ] || fail "缺少 config/application-prod.yml"
ok "产物文件齐全"

# 端口检查（非关键，失败也不停）
OCC=""
if command -v ss >/dev/null 2>&1; then
  OCC=$(ss -tlnp 2>/dev/null | grep ":8966 " || true)
elif command -v netstat >/dev/null 2>&1; then
  OCC=$(netstat -tlnp 2>/dev/null | grep ":8966 " || true)
fi
if [ -n "$OCC" ]; then info "端口 8966 被占用（脚本会停掉旧 data-map 进程；若被其它程序占用需先手动释放）"; fi

echo ""
echo "========== 1. 建目录、放文件 =========="
mkdir -p "$APP/backend" "$APP/frontend" "$APP/config" || fail "建目录失败: $APP"
cp -f data-map-backend-1.0.0.jar "$APP/backend/" || fail "复制 jar 失败"
cp -f config/application-prod.yml "$APP/config/" || fail "复制配置失败"
rm -rf "$APP/frontend/dist"
cp -rf dist "$APP/frontend/dist" || fail "复制前端文件失败"
ok "文件部署完成 → $APP"

echo ""
echo "========== 2. 停旧进程、启新进程 =========="
pkill -f 'data-map-backend-1.0.0.jar' 2>/dev/null
info "已尝试停掉旧进程"
sleep 2

nohup java -jar "$APP/backend/data-map-backend-1.0.0.jar" \
  --spring.config.additional-location="file:$APP/config/" \
  --spring.profiles.active=prod \
  > "$APP/backend/backend.log" 2>&1 &
info "进程已启动，等待 15 秒让 Spring Boot 初始化..."
sleep 15

echo ""
echo "========== 3. 验证 =========="
# 进程检查
if pgrep -f 'data-map-backend-1.0.0.jar' >/dev/null 2>&1; then
  ok "后端进程在运行 (PID: $(pgrep -f data-map-backend-1.0.0.jar | head -1))"
else
  echo -e "${RED}[✗] 后端进程已退出！启动失败，最后 30 行日志:${NC}"
  tail -30 "$APP/backend/backend.log"
  exit 1
fi

# 启动成功标志检查
if grep -q "Started DataMapApplication" "$APP/backend/backend.log" 2>/dev/null; then
  ok "Spring Boot 启动成功"
else
  info "未检测到 'Started DataMapApplication'，可能还在初始化，看日志确认: tail -30 $APP/backend/backend.log"
fi

echo "--- 首页 ---"
code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 http://127.0.0.1:8966/ 2>/dev/null || echo "000")
[ "$code" = "200" ] && ok "首页 :8966 正常 (HTTP 200)" || info "首页异常 (HTTP $code)，看日志: tail -50 $APP/backend/backend.log"

echo "--- 静态资源（白屏必查）---"
# 取 index.html 引用的第一个 js，验证能取到（防止 SPA 兜底误拦静态资源导致白屏）
JS=$(curl -s http://127.0.0.1:8966/ 2>/dev/null | grep -oE 'assets/[^"]+\.js' | head -1)
if [ -n "$JS" ]; then
  code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "http://127.0.0.1:8966/$JS" 2>/dev/null || echo "000")
  [ "$code" = "200" ] && ok "静态资源 /$JS 正常 (HTTP 200)" || info "静态资源 /$JS 异常 (HTTP $code) → 页面会白屏！检查 SpaForwardController 是否误拦截 assets"
else
  info "未能从首页解析出 JS 引用，手动检查: curl -s http://127.0.0.1:8966/ | grep assets"
fi

echo "--- API ---"
code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 http://127.0.0.1:8966/api/projects 2>/dev/null || echo "000")
[ "$code" = "200" ] && ok "API 正常 (HTTP 200)" || info "API 异常 (HTTP $code)，看日志: tail -50 $APP/backend/backend.log"

echo ""
echo "========== 部署完成 =========="
echo ""
echo "  访问地址:  http://12.0.216.77:8966/"
echo "  部署目录:  $APP"
echo "  后端日志:  tail -f $APP/backend/backend.log"
echo "  重启命令:  pkill -f data-map-backend-1.0.0.jar; nohup java -jar $APP/backend/data-map-backend-1.0.0.jar --spring.config.additional-location=file:$APP/config/ --spring.profiles.active=prod > $APP/backend/backend.log 2>&1 &"
echo ""
echo -e "${YELLOW}  ⚠️  浏览器首次访问若白屏，按 Ctrl+Shift+R 强制刷新（清缓存）${NC}"
echo ""
echo "  如果访问不通:"
echo "    1. 防火墙: sudo firewall-cmd --add-port=8966/tcp --permanent && sudo firewall-cmd --reload"
echo "    2. 连不上 MySQL: tail -50 $APP/backend/backend.log 看 Connection refused"
echo "    3. 首页 404: 检查 $APP/config/application-prod.yml 的 datamap.web-root 路径是否正确"
