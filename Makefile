.PHONY: help dev down logs ps restart test test-domain test-ml test-web lint build migrate migrate-domain migrate-ml seed reset-db

help:
	@echo "REFORMA — comandos disponibles"
	@echo ""
	@echo "  make dev          Levanta postgres, redis, api-domain, api-ml y web"
	@echo "  make down         Detiene la stack"
	@echo "  make logs         Tail de logs de servicios"
	@echo "  make ps           Estado de contenedores"
	@echo "  make restart      Reinicia api-domain, api-ml y web"
	@echo ""
	@echo "  make test-domain  Tests Maven (api-domain)"
	@echo "  make test-ml      Tests pytest (api-ml)"
	@echo "  make migrate      Flyway + Alembic"
	@echo "  make seed         Carga datos demo en BD local"
	@echo "  make reset-db     DROP + migrate + seed (destructivo)"

dev:
	docker compose up -d --build
	@echo "Postgres:    localhost:5432"
	@echo "Redis:       localhost:6379"
	@echo "api-domain:  http://localhost:8080  (swagger: /swagger-ui.html)"
	@echo "api-ml:      http://localhost:8081  (docs: /docs)"
	@echo "web:         http://localhost:4200"

down:
	docker compose down

logs:
	docker compose logs -f api-domain api-ml web

ps:
	docker compose ps

restart:
	docker compose restart api-domain api-ml web

test-domain:
	cd services/api-domain && mvn test

test-ml:
	cd services/api-ml && python -m pytest -q

migrate: migrate-domain migrate-ml

migrate-domain:
	cd services/api-domain && mvn flyway:migrate

migrate-ml:
	cd services/api-ml && alembic upgrade head

seed:
	bash scripts/seed.sh

reset-db:
	bash scripts/reset-db.sh

build:
	docker compose build
