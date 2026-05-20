# ADR 0004 — JWT s2s api-domain ↔ api-ml

## Estado

Aceptado

## Decisión

`api-domain` firma JWT con `JWT_ML_SECRET`, claims `iss=api-domain`, `aud=api-ml`. El navegador nunca llama a `api-ml` directamente.
