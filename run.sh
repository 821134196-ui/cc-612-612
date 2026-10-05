#!/usr/bin/env bash
# 一条命令启动：构建前端 -> 构建后端 -> 启动服务（SQLite 本地库）
set -euo pipefail
cd "$(dirname "$0")"

# ---- 1. Java / Maven 工具链（优先使用系统，其次 ~/tools 内自带的二进制）----
if ! command -v java >/dev/null 2>&1; then
  JDK_DIR="$(ls -d "$HOME"/tools/jdk-17* 2>/dev/null | head -1 || true)"
  [ -n "$JDK_DIR" ] && export JAVA_HOME="$JDK_DIR" && export PATH="$JAVA_HOME/bin:$PATH"
fi
if ! command -v mvn >/dev/null 2>&1; then
  MVN_DIR="$(ls -d "$HOME"/tools/apache-maven-* 2>/dev/null | head -1 || true)"
  [ -n "$MVN_DIR" ] && export PATH="$MVN_DIR/bin:$PATH"
fi

echo "==> java: $(java -version 2>&1 | head -1)"
echo "==> mvn : $(mvn -version 2>&1 | head -1)"

# ---- 2. 构建 Svelte 前端到 src/main/resources/META-INF/resources ----
if [ ! -d ui/node_modules ]; then
  echo "==> npm install"
  (cd ui && npm install --no-audit --no-fund)
fi
echo "==> 构建前端"
(cd ui && npm run build)

# ---- 3. 构建后端 ----
echo "==> 构建后端（Quarkus）"
mvn -B -q -DskipTests package

# ---- 4. 启动 ----
mkdir -p data
PORT="${PORT:-8080}"
echo "==> 启动服务： http://localhost:${PORT}  （Ctrl+C 停止）"
exec java -Dquarkus.http.port="$PORT" -jar target/quarkus-app/quarkus-run.jar
