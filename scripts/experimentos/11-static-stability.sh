#!/usr/bin/env bash
source "$(dirname "$0")/../lib.sh"
preparar

titulo "Static stability: data plane segue sem o control plane"
status '.configOperacional'
passo "control plane cai"
chaos '{"controlPlaneForaDoAr":true}'
sleep 4
status '.configOperacional | {origem, idadeMs, falhasConsecutivas, versao: .config.versao}'
passo "transferências continuam com a última config boa"
transferir cliente-1 conta-1 conta-2 10 | grep -o 'HTTP [0-9]*'
passo "control plane volta com novo limite (100)"
chaos_reset
curl -s -X PUT "$BASE/control-plane/config" -H 'Content-Type: application/json' -d '{"valorMaximoTransferencia":100}' | jq -c .
sleep 4
transferir cliente-1 conta-1 conta-2 500
passo "reinicie a aplicação com o control plane fora: ela sobe com o cache em disco (target/config-cache.json)"
curl -s -X PUT "$BASE/control-plane/config" -H 'Content-Type: application/json' -d '{"valorMaximoTransferencia":5000}' > /dev/null
