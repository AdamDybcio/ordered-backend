.PHONY: help up down restart logs build test test-unit test-integration verify format format-check clean run

help: ## Show this help message
	@echo Available commands:
	@echo   up                 - Start local infrastructure in Docker
	@echo   up-full            - Start full stack in Docker, for pre-deploy sanity checks
	@echo   down               - Stop local infrastructure
	@echo   restart            - Restart local infrastructure
	@echo   logs               - Tail logs from local infrastructure
	@echo   build              - Compile the project (no tests)
	@echo   test-unit          - Run unit tests only (same as CI unit-tests job)
	@echo   test-integration   - Run unit + integration tests (same as CI integration-tests job)
	@echo   test               - Alias for test-integration
	@echo   format             - Auto-format code with Spotless
	@echo   format-check       - Check code formatting without modifying files (same as CI lint job)
	@echo   clean              - Remove build artifacts
	@echo   run                - Run the application locally

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

format: ## Auto-format code with Spotless
	mvn --batch-mode --no-transfer-progress spotless:apply

format-check: ## Check code formatting without modifying files (same as CI lint job)
	mvn --batch-mode --no-transfer-progress spotless:check

clean: ## Remove build artifacts
	mvn --batch-mode --no-transfer-progress clean

run: ## Run the application locally
	mvn spring-boot:run