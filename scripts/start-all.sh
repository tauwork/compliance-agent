#!/bin/bash
# 一键启动（本地开发模式：构建后端 + 启动前端dev）
# 前置：JDK21+、Maven、Node18+、本地MySQL 8已运行且可建库
set -e
cd "$(dirname "$0")/.."

echo "[1/4] 构建后端..."
mvn -q clean package -DskipTests

echo "[2/4] 准备数据库（需要root权限，仅本地演示库pcm）..."
mysql -h${MYSQL_HOST:-127.0.0.1} -P${MYSQL_PORT:-3306} -uroot -e \
  "CREATE DATABASE IF NOT EXISTS pcm DEFAULT CHARACTER SET utf8mb4;
   CREATE USER IF NOT EXISTS 'pcm'@'%' IDENTIFIED BY 'pcm123456';
   CREATE USER IF NOT EXISTS 'pcm'@'localhost' IDENTIFIED BY 'pcm123456';
   GRANT ALL ON pcm.* TO 'pcm'@'%'; GRANT ALL ON pcm.* TO 'pcm'@'localhost'; FLUSH PRIVILEGES;" \
  || echo "跳过建库（可能已存在或无root权限，Flyway将自动建表）"

echo "[3/4] 启动后端 :8080 ..."
MYSQL_HOST=${MYSQL_HOST:-127.0.0.1} MYSQL_PORT=${MYSQL_PORT:-3306} \
  java -jar app/target/app-1.0.0-M1.jar &
APP_PID=$!
sleep 8

echo "[4/4] 启动前端 :5173 ..."
cd web && npm install --no-fund --no-audit && npm run dev &
WEB_PID=$!

sleep 6
echo
echo "==== 启动完成 ===="
echo "前端:   http://localhost:5173"
echo "API:    http://localhost:8080/api/stats/overview"
echo "演示数据: 新终端执行 sh scripts/demo-seed.sh"
echo "停止: kill $APP_PID $WEB_PID"
wait
