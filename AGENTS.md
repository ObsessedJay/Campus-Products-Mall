# Repository Guidelines

## Project Structure & Module Organization

This is a single-module Maven project for the campus cultural-creative flash-sale system.

- `src/main/java/com/sk/onlinemall/` contains Spring Boot production code. Keep controllers, services, mappers, entities, configuration, and shared utilities in separate packages as the implementation grows.
- `src/test/java/com/sk/onlinemall/` contains JUnit/Spring Boot tests. Mirror the production package structure.
- `tasks&requirements/` contains the authoritative product requirements and phased delivery plan. Read these files before changing business behavior.
- `pom.xml` defines Java 17, Spring Boot 3.0.2, MyBatis, MySQL, Lombok, and test dependencies.

## Build, Test, and Development Commands

Run commands from the repository root:

```powershell
mvn test                         # compile and run all tests
mvn clean verify                 # clean build plus verification
mvn spring-boot:run              # start the local Spring Boot application
mvn package                      # produce the deployable artifact
```

Database and middleware configuration is not checked in. Use profiles or environment variables for local values; never commit credentials.

## Coding Style & Naming Conventions

Use Java 17, four-space indentation, UTF-8, and existing Spring conventions. Packages are lowercase (`com.sk.onlinemall...`); classes and records use `PascalCase`; methods, variables, and JSON fields use `camelCase`; constants use `UPPER_SNAKE_CASE`. Prefer constructor injection, explicit service boundaries, thin controllers, and service-level transactions. Use Lombok when it improves readability. Every handwritten Java method and constructor must have concise Chinese JavaDoc; include applicable `@param`, `@param <T>`, and `@return` entries with meaningful descriptions. Keep inline comments for non-obvious concurrency or consistency decisions.

## Testing Guidelines

Use JUnit 5 through `spring-boot-starter-test`. Name tests after the unit under test, for example `FlashSaleServiceTests` and `OrderControllerTests`; use descriptive method names such as `shouldRejectDuplicatePurchase`. Cover stock, idempotency, and state transitions, plus integration tests for MySQL/MyBatis and RabbitMQ flows as those modules arrive. Run `mvn test` before every pull request.

## Commit & Pull Request Guidelines

No Git history is available in this checkout, so use Conventional Commits going forward: `feat: add flash-sale reservation`, `fix: release expired order stock`, or `docs: update requirements`. Keep commits focused. Pull requests should explain the user-visible change, link the relevant task/story ID, list validation commands, and include screenshots or API examples for UI or contract changes. Call out schema, cache-key, queue, or configuration changes explicitly.

## Security & Configuration Tips

Never commit passwords, JWT secrets, payment keys, or production endpoints. Validate authorization on protected APIs, make Redis stock deduction atomic, and make RabbitMQ consumers idempotent. Audit administrative actions and keep test credentials in a local profile.
