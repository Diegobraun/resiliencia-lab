#!/usr/bin/env bash
source "$(dirname "$0")/../lib.sh"
preparar

titulo "Idempotency key: mesma chave, um efeito só"
CHAVE=$(uuidgen)
passo "saldo antes"; saldo conta-1
passo "envia a mesma transferência 3 vezes com a chave $CHAVE"
for i in 1 2 3; do transferir cliente-1 conta-1 conta-2 100 "$CHAVE" -D - | grep -iE 'idempotent-replayed|"id"' | head -2; done
passo "saldo depois (debitou 100 uma vez só)"; saldo conta-1
passo "mesma chave, corpo diferente -> 422"
transferir cliente-1 conta-1 conta-2 999 "$CHAVE"
