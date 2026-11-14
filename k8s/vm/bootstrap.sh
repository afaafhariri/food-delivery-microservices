#!/usr/bin/env bash
# Turns a fresh Ubuntu 22.04/24.04 VM into a single-node k3s cluster with
# cert-manager and a Let's Encrypt ClusterIssuer ("letsencrypt").
# Safe to re-run: every step is skipped when already done.
#
# Run as root on the VM. scripts/cloud-deploy.sh does this for you:
#   sudo bash k8s/vm/bootstrap.sh
set -euo pipefail

CERT_MANAGER_VERSION="${CERT_MANAGER_VERSION:-v1.18.2}"

log() { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
k() { k3s kubectl "$@"; }

[[ $EUID -eq 0 ]] || { echo "run as root (sudo bash $0)" >&2; exit 1; }

# k3s ships Traefik (ingress on :80/:443), ServiceLB, local-path storage and a
# NetworkPolicy controller, which is everything the prod overlay needs.
if ! command -v k3s >/dev/null 2>&1; then
  log "Installing k3s"
  curl -sfL https://get.k3s.io | INSTALL_K3S_CHANNEL=stable sh -s - --write-kubeconfig-mode 600
else
  log "k3s already installed ($(k3s --version | head -1))"
fi

log "Waiting for the node"
until k get nodes 2>/dev/null | grep -qw Ready; do sleep 3; done
k get nodes

log "Waiting for Traefik"
until k -n kube-system get deployment traefik >/dev/null 2>&1; do sleep 5; done
k -n kube-system rollout status deployment/traefik --timeout=5m

# Host firewall: Ubuntu cloud images ship ufw inactive. If it is on, open web.
if command -v ufw >/dev/null 2>&1 && ufw status | grep -q 'Status: active'; then
  log "Opening 80/443 in ufw"
  ufw allow 80/tcp
  ufw allow 443/tcp
fi

if ! k get namespace cert-manager >/dev/null 2>&1; then
  log "Installing cert-manager ${CERT_MANAGER_VERSION}"
  k apply -f "https://github.com/cert-manager/cert-manager/releases/download/${CERT_MANAGER_VERSION}/cert-manager.yaml"
fi
for d in cert-manager cert-manager-cainjector cert-manager-webhook; do
  k -n cert-manager rollout status "deployment/$d" --timeout=5m
done

# The webhook can take a few seconds after "ready" before it admits resources.
log "Creating ClusterIssuer letsencrypt"
for _ in $(seq 1 30); do
  if k apply -f - <<'EOF'
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    privateKeySecretRef:
      name: letsencrypt-account-key
    solvers:
      - http01:
          ingress:
            ingressClassName: traefik
EOF
  then
    break
  fi
  sleep 5
done
k get clusterissuer letsencrypt

log "Cluster ready"
