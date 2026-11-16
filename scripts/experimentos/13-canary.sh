#!/usr/bin/env bash
source "$(dirname "$0")/../lib.sh"
preparar

titulo "Canary + staged rollout com rollback automático"
passo "v2 do cliente do core está quebrada; ninguém sabe ainda"
chaos '{"v2Quebrada":true}'
curl -s -X POST "$BASE/api/rollout/iniciar" | jq -c '{percentualV2, eventos}'
CLIENTES=1000 carga 400 10 > /dev/null
sleep 2
curl -s "$BASE/api/rollout" | jq '{ativo, percentualV2, v1, v2, eventos}'
passo "agora com v2 saudável: avança 5 -> 25 -> 50 -> 100 (10s por estágio)"
chaos_reset
curl -s -X POST "$BASE/api/rollout/iniciar" > /dev/null
for i in 1 2 3 4 5; do CLIENTES=1000 carga 200 10 > /dev/null; sleep 9; curl -s "$BASE/api/rollout" | jq -c '{percentualV2, ultimo: .eventos[0]}'; done
