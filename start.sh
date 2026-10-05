#!/usr/bin/env bash
# 一条命令启动：构建前端 -> 打包后端 -> 启动服务
# 用法: ./start.sh
set -euo pipefail
cd "$(dirname "$0")"

# ---- 定位 JDK / Maven（优先系统命令，其次 ./tools 下的便携版） ----
if ! command -v java >/dev/null 2>&1; then
  JDK_DIR=$(ls -d "$HOME"/tools/jdk-21* 2>/dev/null | head -1 || true)
  if [ -n "$JDK_DIR" ]; then
    export JAVA_HOME="$JDK_DIR"
    export PATH="$JAVA_HOME/bin:$PATH"
  else
    echo "未找到 Java，请先安装 JDK 17+（或解压便携 JDK 到 ~/tools/）"; exit 1
  fi
fi
if ! command -v mvn >/dev/null 2>&1; then
  MVN_DIR=$(ls -d "$HOME"/tools/apache-maven-* 2>/dev/null | head -1 || true)
  if [ -n "$MVN_DIR" ]; then
    export PATH="$MVN_DIR/bin:$PATH"
  else
    echo "未找到 Maven，请先安装 Maven 3.9+（或解压便携 Maven 到 ~/tools/）"; exit 1
  fi
fi

# ---- 构建前端（产物直接进入后端静态资源目录） ----
echo "==> 构建前端 (Svelte + Vite)"
(cd frontend && npm install --no-audit --no-fund && npm run build)

# ---- 打包后端 ----
echo "==> 打包后端 (Quarkus)"
(cd backend && mvn -B -q package -DskipTests)

# ---- 启动 ----
echo "==> 启动 http://localhost:8080 （Ctrl+C 停止）"
cd backend
exec java -jar target/quarkus-app/quarkus-run.jar
