#!/usr/bin/env bash
source "$(dirname "$0")/../lib.sh"
preparar

titulo "Retry com backoff + jitter, limitado por retry budget"
status '.retry'
passo "core com 30% de erro (abaixo do limite do breaker): retries ajudam, mas o budget (10% das requisições) seca"
chaos '{"taxaErro":0.3}'
carga 400 20
status '.retry'
chaos_reset
