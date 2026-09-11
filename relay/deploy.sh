#!/usr/bin/env bash
# Deploy the Pi Remote Terminal relay on Debian 12.
# Upload this directory to /opt/pi-relay first, then run this script as root.
set -euo pipefail

PUBLIC_IP="${PUBLIC_IP:-8.138.112.73}"
APP_DIR="/opt/pi-relay"
ETC_DIR="/etc/pi-relay"
SVC_USER="pi-relay"

log() { printf '\n\033[1;32m==> %s\033[0m\n' "$*"; }

log "1/7 Node.js"
if ! command -v node >/dev/null 2>&1; then
  curl -fsSL https://deb.nodesource.com/setup_22.x | bash -
  apt-get install -y nodejs
fi
node -v

log "2/7 service account"
if ! id -u "$SVC_USER" >/dev/null 2>&1; then
  useradd --system --no-create-home --shell /usr/sbin/nologin "$SVC_USER"
fi

log "3/7 directories"
mkdir -p "$ETC_DIR"
chgrp "$SVC_USER" "$ETC_DIR"
chmod 750 "$ETC_DIR"

log "4/7 TLS certificate (self-signed, pinned by fingerprint)"
if [[ ! -f "$ETC_DIR/cert.pem" ]]; then
  openssl req -x509 -newkey rsa:2048 -nodes -days 3650 \
    -keyout "$ETC_DIR/key.pem" -out "$ETC_DIR/cert.pem" \
    -subj "/CN=pi-relay" \
    -addext "subjectAltName=IP:${PUBLIC_IP},DNS:pi-relay" >/dev/null 2>&1
fi
chgrp "$SVC_USER" "$ETC_DIR/cert.pem" "$ETC_DIR/key.pem"
chmod 644 "$ETC_DIR/cert.pem"
chmod 640 "$ETC_DIR/key.pem"
FINGERPRINT=$(openssl x509 -in "$ETC_DIR/cert.pem" -noout -fingerprint -sha256 | cut -d= -f2)

log "5/7 tokens"
if [[ ! -f "$ETC_DIR/config.json" ]]; then
  AGENT_TOKEN=$(openssl rand -hex 32)
  DEVICE_TOKEN=$(openssl rand -hex 32)
  cat > "$ETC_DIR/config.json" <<JSON
{
  "ports": [443, 8443],
  "path": "/relay",
  "certFile": "$ETC_DIR/cert.pem",
  "keyFile": "$ETC_DIR/key.pem",
  "agentToken": "$AGENT_TOKEN",
  "deviceToken": "$DEVICE_TOKEN",
  "heartbeatMs": 30000,
  "handshakeTimeoutMs": 10000,
  "maxPayloadBytes": 4194304
}
JSON
fi
chgrp "$SVC_USER" "$ETC_DIR/config.json"
chmod 640 "$ETC_DIR/config.json"

log "6/7 dependencies"
cd "$APP_DIR"
npm install --omit=dev --no-audit --no-fund

log "7/7 systemd"
install -m 644 "$APP_DIR/pi-relay.service" /etc/systemd/system/pi-relay.service
systemctl daemon-reload
systemctl enable --now pi-relay
sleep 2
systemctl --no-pager --lines=15 status pi-relay || true

AGENT_TOKEN=$(node -e "console.log(require('$ETC_DIR/config.json').agentToken)")
DEVICE_TOKEN=$(node -e "console.log(require('$ETC_DIR/config.json').deviceToken)")

cat <<EOF

============================================================
部署完成
------------------------------------------------------------
证书 SHA-256 指纹
$FINGERPRINT

Agent  Token
$AGENT_TOKEN

Device Token
$DEVICE_TOKEN

监听地址
wss://${PUBLIC_IP}:443/relay     (备用 8443)

健康检查
curl -k https://${PUBLIC_IP}/healthz
============================================================
EOF
