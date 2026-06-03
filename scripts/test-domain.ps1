# Tests api-domain: unitarios (siempre) + integración -Pit (requiere Docker para Testcontainers).
param(
    [switch]$SoloUnitarios
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

function Invoke-MavenTest([string]$Goals) {
    docker run --rm `
        -v "${root}\services\api-domain:/app" `
        -v reforma_m2_cache:/root/.m2 `
        -w /app `
        maven:3.9-eclipse-temurin-21-alpine `
        mvn -B $Goals
}

Write-Host "1/2 Tests unitarios (MP, Proveedores, Animales)..." -ForegroundColor Cyan
Invoke-MavenTest "test"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

if ($SoloUnitarios) {
    Write-Host "OK — tests unitarios en verde." -ForegroundColor Green
    exit 0
}

Write-Host "2/2 Tests integración (-Pit, Postgres Testcontainers)..." -ForegroundColor Cyan
$env:TESTCONTAINERS_RYUK_DISABLED = "true"
docker run --rm `
    -v "${root}\services\api-domain:/app" `
    -v reforma_m2_cache:/root/.m2 `
    -v //var/run/docker.sock:/var/run/docker.sock `
    -e DOCKER_HOST=unix:///var/run/docker.sock `
    -w /app `
    maven:3.9-eclipse-temurin-21-alpine `
    mvn -B test -Pit

if ($LASTEXITCODE -eq 0) {
    Write-Host "OK — catálogos estabilizados (unit + integración)." -ForegroundColor Green
} else {
    Write-Host "FALLO integración — Docker debe estar activo. Probá: .\scripts\test-domain.ps1 -SoloUnitarios" -ForegroundColor Red
    exit $LASTEXITCODE
}
