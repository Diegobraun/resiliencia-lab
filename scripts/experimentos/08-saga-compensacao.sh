#!/usr/bin/env bash
source "$(dirname "$0")/../lib.sh"
preparar

titulo "Saga: débito ok, crédito recusado -> estorno"
saldo conta-1
transferir cliente-1 conta-1 conta-inexistente 250 | jq -R 'split(" [")[0] | fromjson | {status, motivo}'
passo "saldo voltou ao original"
saldo conta-1
