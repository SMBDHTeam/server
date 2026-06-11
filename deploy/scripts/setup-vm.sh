#!/usr/bin/env bash

set -euo pipefail

APP_NAME="server"
APP_DIR="/opt/${APP_NAME}"
NGINX_CONF_SOURCE="./deploy/nginx/default.conf"
NGINX_CONF_TARGET="/etc/nginx/sites-available/${APP_NAME}"
NGINX_CONF_LINK="/etc/nginx/sites-enabled/${APP_NAME}"

echo "========== VM setup start =========="


if [ "$(id -u)" -ne 0 ]; then
  echo "ERROR: Please run this script with sudo."
  echo "Example: sudo bash deploy/scripts/setup-vm.sh"
  exit 1
fi

echo "[1/8] Update package index"

apt-get update -y

echo "[2/8] Install base packages"


apt-get install -y \
  ca-certificates \
  curl \
  gnupg \
  lsb-release

echo "[3/8] Set timezone to Asia/Seoul"


timedatectl set-timezone Asia/Seoul || true

echo "[4/8] Install Docker Engine"


install -m 0755 -d /etc/apt/keyrings

if [ ! -f /etc/apt/keyrings/docker.asc ]; then
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
fi


echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}") stable" \
  > /etc/apt/sources.list.d/docker.list

apt-get update -y

apt-get install -y \
  docker-ce \
  docker-ce-cli \
  containerd.io \
  docker-buildx-plugin \
  docker-compose-plugin

echo "[5/8] Enable and start Docker"

systemctl enable docker
systemctl start docker

echo "[6/8] Install and start Nginx"


apt-get install -y nginx

systemctl enable nginx
systemctl start nginx

echo "[7/8] Create application directory"


mkdir -p "${APP_DIR}"
chown -R "${SUDO_USER:-root}:${SUDO_USER:-root}" "${APP_DIR}"

echo "[8/8] Apply Nginx config if source exists"

if [ -f "${NGINX_CONF_SOURCE}" ]; then
  cp "${NGINX_CONF_SOURCE}" "${NGINX_CONF_TARGET}"


  rm -f /etc/nginx/sites-enabled/default

  # sites-available에 둔 설정을 sites-enabled로 연결
  ln -sfn "${NGINX_CONF_TARGET}" "${NGINX_CONF_LINK}"

  # Nginx 설정 문법 검사
  nginx -t

  # 설정 반영
  systemctl reload nginx

  echo "Nginx config applied: ${NGINX_CONF_TARGET}"
else
  echo "Nginx config source not found: ${NGINX_CONF_SOURCE}"
  echo "Skip Nginx config apply."
fi

echo "========== VM setup completed =========="

echo "Docker version:"
docker --version

echo "Docker Compose version:"
docker compose version || true

echo "Nginx version:"
nginx -v

echo ""
echo "Next checks:"
echo "1. Check Docker: sudo systemctl status docker"
echo "2. Check Nginx : sudo systemctl status nginx"
echo "3. Check Nginx config: sudo nginx -t"
echo "4. Cloud firewall/security group must allow 22, 80, later 443"