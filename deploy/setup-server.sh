#!/usr/bin/env bash
# Preparación única del servidor (Ubuntu 24.04 en Oracle Cloud). Uso: bash setup-server.sh
set -euo pipefail

echo "== Docker =="
if ! command -v docker >/dev/null; then
  sudo apt-get update -qq
  sudo apt-get install -y -qq ca-certificates curl gettext-base
  sudo install -m 0755 -d /etc/apt/keyrings
  sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
    | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
  sudo apt-get update -qq
  sudo apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-compose-plugin
  sudo usermod -aG docker "$USER"
fi
sudo apt-get install -y -qq gettext-base netfilter-persistent > /dev/null

echo "== Firewall del servidor: puertos 80 y 443 =="
# Las imágenes de Ubuntu de Oracle traen iptables que bloquea todo salvo SSH.
for rule in "tcp 80" "tcp 443" "udp 443"; do
  set -- $rule
  sudo iptables -C INPUT -p "$1" --dport "$2" -m state --state NEW -j ACCEPT 2>/dev/null \
    || sudo iptables -I INPUT 6 -p "$1" --dport "$2" -m state --state NEW -j ACCEPT
done
sudo netfilter-persistent save > /dev/null

echo "== Carpeta y secretos =="
sudo mkdir -p /opt/opspilot && sudo chown "$USER:$USER" /opt/opspilot
if [ ! -f /opt/opspilot/.env ]; then
  read -rp "Correo para avisos de Let's Encrypt: " acme_email
  cat > /opt/opspilot/.env <<ENV
APP_URL=https://opspilot.fuken404.dev
API_HOST=api.opspilot.fuken404.dev
AUTH_HOST=auth.opspilot.fuken404.dev
ACME_EMAIL=$acme_email
POSTGRES_PASSWORD=$(openssl rand -hex 24)
KEYCLOAK_ADMIN_PASSWORD=$(openssl rand -hex 16)
# Copias en Oracle Object Storage (opcional)
S3_ENDPOINT=
S3_BUCKET=
S3_ACCESS_KEY=
S3_SECRET_KEY=
ENV
  chmod 600 /opt/opspilot/.env
  echo "Secretos generados en /opt/opspilot/.env (no se muestran)."
fi
echo "Listo. Cierra la sesión SSH y vuelve a entrar para usar docker sin sudo."
