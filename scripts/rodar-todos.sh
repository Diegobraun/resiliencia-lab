#!/usr/bin/env bash
set -euo pipefail
for experimento in "$(dirname "$0")"/experimentos/*.sh; do
  bash "$experimento"
done
