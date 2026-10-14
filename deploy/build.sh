#!/bin/sh
# Builds the three images and pushes them: ./build.sh 0.1.0   (PMP_REGISTRY from pmp.env)
# With PMP_REGISTRY=pmp the images stay local (single-node Swarm) and nothing is pushed.
set -eu
cd "$(dirname "$0")"
VERSION="${1:?usage: ./build.sh <version>}"
set -a; . ./pmp.env; set +a
for app in backend analytics frontend; do
  # The frontend is a server build: no development tenant, sign-in required.
  docker build -t "${PMP_REGISTRY}/pmp-${app}:${VERSION}" "../${app}"
  [ "$PMP_REGISTRY" = pmp ] || docker push "${PMP_REGISTRY}/pmp-${app}:${VERSION}"
done
echo "Built ${VERSION}. Set PMP_VERSION=${VERSION} in pmp.env and run ./deploy.sh"
