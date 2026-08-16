-include .env
export

.PHONY: help up down restart logs build test test-unit test-integration verify format format-check clean run run-observability observability-up observability-down

help: ## Show this help message
	@echo Available commands:
	@echo   up                 - Start local infrastructure in Docker
	@echo   up-full            - Start full stack in Docker, for pre-deploy sanity chec	ks
	@echo   down               - Stop local infrastructure
	@echo   restart            - Restart local infrastructure
	@echo   logs               - Tail logs from local infrastructure
	@echo   build              - Compile the project (no tests)
	@echo   test-unit          - Run unit tests only (same as CI unit-tests job)
	@echo   test-integration   - Run unit + integration tests (same as CI integration-tests job)
	@echo   test               - Alias for test-integration
	@echo   load-test          - Run Gatling load test against running app
	@echo   seed-load-test-data - Seed sample products for Gatling load test (idempotent)
	@echo   load-test-cache-stress - Run Gatling read-heavy stress test against product catalog (Redis cache)
	@echo   format             - Auto-format code with Spotless
	@echo   format-check       - Check code formatting without modifying files (same as CI lint job)
	@echo   clean              - Remove build artifacts
	@echo   run                - Run the application locally
	@echo   run-observability  - Run app locally with Prometheus metrics exposed
	@echo   observability-up   - Start Prometheus + Grafana + Jaeger (distributed tracing)
	@echo   observability-down - Stop Prometheus + Grafana + Jaeger
	@echo   observability-restart - Restart Prometheus + Grafana + Jaeger

up: ## Start local infrastructure in Docker
	docker compose up -d

up-full: ## Start full stack in Docker, for pre-deploy sanity checks
	docker compose --profile full up -d --build

down: ## Stop local infrastructure
	docker compose down

restart: down up ## Restart local infrastructure

logs: ## Tail logs from local infrastructure
	docker compose logs -f

build: ## Compile the project (no tests)
	mvn --batch-mode --no-transfer-progress compile

test-unit: ## Run unit tests only (same as CI unit-tests job)
	mvn --batch-mode --no-transfer-progress test

test-integration: ## Run unit + integration tests (same as CI integration-tests job)
	mvn --batch-mode --no-transfer-progress verify

test: test-integration ## Alias for test-integration

load-test: ## Run Gatling load test (app must be running separately)
	mvn --batch-mode --no-transfer-progress gatling:test -Dgatling.simulationClass=pl.dybcio.ordered.gatling.UserJourneySimulation

seed-load-test-data: ## Seed sample products for Gatling load test (idempotent)
	mvn spring-boot:run -Dspring-boot.run.profiles=load-test-seed

load-test-cache-stress: ## Run Gatling read-heavy stress test against product catalog (Redis cache)
	mvn --batch-mode --no-transfer-progress gatling:test -Dgatling.simulationClass=pl.dybcio.ordered.gatling.ProductCatalogStressSimulation

format: ## Auto-format code with Spotless
	mvn --batch-mode --no-transfer-progress spotless:apply

format-check: ## Check code formatting without modifying files (same as CI lint job)
	mvn --batch-mode --no-transfer-progress spotless:check

clean: ## Remove build artifacts
	mvn --batch-mode --no-transfer-progress clean

run: ## Run the application locally
	mvn spring-boot:run

run-observability: ## Run app locally with Prometheus metrics exposed
	mvn spring-boot:run -Dspring-boot.run.profiles=observability

observability-up: ## Start Prometheus + Grafana + Jaeger (distributed tracing)
	docker compose up -d prometheus grafana jaeger

observability-down: ## Stop and remove Prometheus + Grafana + Jaeger
	docker compose rm -sf prometheus grafana jaeger

observability-restart: ## Restart Prometheus + Grafana + Jaeger
	observability-down observability-up