#!/usr/bin/env bash
# Deploys QuickBite (k8s/overlays/prod) to a fresh Ubuntu VM over SSH:
# installs k3s + cert-manager on first run, copies the manifests, and applies
# them with images from GHCR. Nothing is exposed except ports 80/443.
#
# Usage:
#   scripts/cloud-deploy.sh <ssh-user>@<vm-ip>
#
# Optional environment:
#   TAG=sha-1a2b3c4            image tag to deploy (default: latest)
#   HOST=api.example.com       public hostname (default: <vm-ip>.sslip.io,
#                              a free wildcard DNS name that resolves to the IP)
#   GHCR_USER=... GHCR_TOKEN=...   only for private GHCR packages: a GitHub
#                              token with read:packages, stored as a pull secret
set -euo pipefail

TARGET="${1:?usage: $0 <ssh-user>@<vm-ip>}"
TAG="${TAG:-latest}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REMOTE_DIR=quickbite # relative to the SSH user's home

addr="${TARGET#*@}"
if [[ -z "${HOST:-}" ]]; then
  if [[ "$addr" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    HOST="$addr.sslip.io"
  else
    echo "Set HOST=<public hostname> when connecting by name instead of IP." >&2
    exit 1
  fi
fi

step() { printf '\n\033[1;34m### %s\033[0m\n' "$*"; }
remote() { ssh -o StrictHostKeyChecking=accept-new -o ConnectTimeout=10 "$TARGET" "$@"; }

step "Checking SSH and sudo on $TARGET"
remote 'sudo -n true' || {
  echo "Need SSH access with passwordless sudo (default for root, ubuntu and SSH-key azureuser)." >&2
  exit 1
}

step "Copying k8s/ and scripts/ to ~/$REMOTE_DIR"
# COPYFILE_DISABLE / --no-xattrs: no macOS ._* files or xattr headers in the archive.
COPYFILE_DISABLE=1 tar -C "$ROOT" --no-xattrs \
    --exclude 'k8s/overlays/prod/secrets.env' --exclude 'k8s/overlays/cloud' --exclude '.DS_Store' \
    -czf - k8s scripts |
  remote "rm -rf ~/$REMOTE_DIR/k8s ~/$REMOTE_DIR/scripts && mkdir -p ~/$REMOTE_DIR && tar -xzf - -C ~/$REMOTE_DIR"

step "Bootstrapping k3s + cert-manager (skipped if already done)"
remote "sudo -n bash ~/$REMOTE_DIR/k8s/vm/bootstrap.sh"

if [[ -n "${GHCR_TOKEN:-}" ]]; then
  step "Storing GHCR pull secret"
  : "${GHCR_USER:?set GHCR_USER (your GitHub username) together with GHCR_TOKEN}"
  # Built locally and sent over stdin, so the token never appears in a command line.
  auth=$(printf '%s:%s' "$GHCR_USER" "$GHCR_TOKEN" | base64 | tr -d '\n')
  config=$(printf '{"auths":{"ghcr.io":{"auth":"%s"}}}' "$auth" | base64 | tr -d '\n')
  {
    cat "$ROOT/k8s/base/namespace.yaml"
    printf -- '---\napiVersion: v1\nkind: Secret\nmetadata:\n  name: ghcr\n  namespace: quickbite\ntype: kubernetes.io/dockerconfigjson\ndata:\n  .dockerconfigjson: %s\n' "$config"
  } | remote 'sudo -n k3s kubectl apply -f -'
fi

step "Deploying tag '$TAG' for https://$HOST"
remote "sudo -n bash ~/$REMOTE_DIR/k8s/vm/deploy.sh '$HOST' '$TAG'"

cat <<EOF

QuickBite is up.
  Gateway (HTTP):   http://$HOST
  Gateway (HTTPS):  https://$HOST   (once the certificate shows READY=True above;
                                     Let's Encrypt usually takes under a minute)
  Smoke test:       scripts/smoke-test.sh http://$HOST
  Cluster shell:    ssh $TARGET sudo k3s kubectl -n quickbite get pods
EOF
