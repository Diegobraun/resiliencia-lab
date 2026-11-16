#!/usr/bin/env bash
source "$(dirname "$0")/../lib.sh"
preparar

titulo "Shuffle sharding: cliente barulhento só satura o próprio shard"
curl -s "$BASE/api/shuffle-sharding/analise?clientes=10000&barulhento=cliente-barulhento" | jq .
passo "shard do barulhento:"; curl -s "$BASE/api/shuffle-sharding/clientes/cliente-barulhento" | jq -c .
chaos '{"latenciaMs":600}'
passo "barulhento manda 40 requisições em paralelo"
TMP=$(mktemp -d)
CLIENTE_FIXO=cliente-barulhento carga 40 40 > "$TMP/barulhento" &
sleep 0.3
passo "enquanto isso, outros clientes:"
for c in cliente-10 cliente-20 cliente-30 cliente-40; do
  printf '   %s workers=%s -> ' "$c" "$(curl -s "$BASE/api/shuffle-sharding/clientes/$c" | jq -c .workers)"
  transferir "$c" conta-3 conta-4 1 | grep -o 'HTTP [0-9]*'
done
wait
echo "   barulhento:"; cat "$TMP/barulhento"
chaos_reset
