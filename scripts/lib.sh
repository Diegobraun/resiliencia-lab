#!/usr/bin/env bash
set -euo pipefail

BASE="${BASE:-http://localhost:8080}"

titulo() {
  printf '\n\033[1;36m== %s ==\033[0m\n' "$*"
}

passo() {
  printf '\033[1;33m-> %s\033[0m\n' "$*"
}

chaos() {
  curl -s -X PUT "$BASE/chaos" -H 'Content-Type: application/json' -d "$1" > /dev/null
}

chaos_reset() {
  curl -s -X DELETE "$BASE/chaos" > /dev/null
}

transferir() {
  local cliente="$1" origem="$2" destino="$3" valor="$4" chave="${5:-$(uuidgen)}"
  shift 5 || shift $#
  curl -s -o /dev/stdout -w ' [HTTP %{http_code} em %{time_total}s]\n' \
    -X POST "$BASE/api/transferencias" \
    -H 'Content-Type: application/json' \
    -H "X-Cliente: $cliente" \
    -H "Idempotency-Key: $chave" \
    "$@" \
    -d "{\"contaOrigem\":\"$origem\",\"contaDestino\":\"$destino\",\"valor\":$valor}"
}

carga() {
  local total="$1" paralelo="$2"
  BASE="$BASE" seq "$total" | BASE="$BASE" xargs -P "$paralelo" -n 1 bash "$(dirname "${BASH_SOURCE[0]}")/req.sh" | sort | uniq -c | sed 's/^/   /'
}

preparar() {
  chaos_reset
  for _ in $(seq 20); do
    if ! curl -s "$BASE/api/status" | jq -e '[.circuitBreakers[].estado] | index("ABERTO")' > /dev/null; then
      return
    fi
    sleep 0.5
  done
}

status() {
  curl -s "$BASE/api/status" | jq "${1:-.}"
}

saldo() {
  curl -s "$BASE/core/contas/$1" | jq -c .
}
