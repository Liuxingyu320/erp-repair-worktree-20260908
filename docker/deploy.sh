#!/bin/sh
set -eu

BASE_DIR=$(CDPATH= cd "$(dirname "$0")" && pwd)

usage() {
	echo "Usage: sh deploy.sh [port|base|modules|stop|rm]"
	echo "Note: port is for local/debug firewall setup, not production hardening."
	exit 1
}

compose_cmd() {
	if docker compose version >/dev/null 2>&1; then
		echo "docker compose"
		return
	fi
	if command -v docker-compose >/dev/null 2>&1; then
		echo "docker-compose"
		return
	fi
	echo "docker compose or docker-compose is required" >&2
	exit 1
}

# 开启所需端口（本地/调试部署使用，不作为生产防火墙策略）
port() {
	echo "WARNING: port opens module ports for local/debug deployment. Production should expose only erp-nginx/erp-gateway."
	firewall-cmd --add-port=80/tcp --permanent
	firewall-cmd --add-port=8080/tcp --permanent
	firewall-cmd --add-port=8848/tcp --permanent
	firewall-cmd --add-port=9848/tcp --permanent
	firewall-cmd --add-port=9849/tcp --permanent
	firewall-cmd --add-port=6379/tcp --permanent
	firewall-cmd --add-port=3306/tcp --permanent
	firewall-cmd --add-port=9100/tcp --permanent
	firewall-cmd --add-port=9200/tcp --permanent
	firewall-cmd --add-port=9201/tcp --permanent
	firewall-cmd --add-port=9203/tcp --permanent
	firewall-cmd --add-port=9204/tcp --permanent
	firewall-cmd --add-port=9205/tcp --permanent
	firewall-cmd --add-port=9206/tcp --permanent
	firewall-cmd --add-port=9300/tcp --permanent
	service firewalld restart
}

# 启动基础环境（必须）
base() {
	cd "$BASE_DIR"
	$(compose_cmd) up -d erp-mysql erp-redis erp-nacos
}

# 启动程序模块（必须）
modules() {
	cd "$BASE_DIR"
	$(compose_cmd) up -d erp-gateway erp-auth erp-visual-monitor erp-modules-system erp-modules-job erp-modules-oa erp-modules-inventory erp-modules-file erp-modules-approval erp-nginx
}

# 关闭所有环境/模块
stop() {
	cd "$BASE_DIR"
	$(compose_cmd) stop
}

# 删除所有环境/模块
remove() {
	cd "$BASE_DIR"
	$(compose_cmd) rm -f
}

case "${1:-}" in
"port")
	port
;;
"base")
	base
;;
"modules")
	modules
;;
"stop")
	stop
;;
"rm")
	remove
;;
*)
	usage
;;
esac
