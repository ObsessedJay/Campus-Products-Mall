# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack

Existing Spring Boot 3 / Java 17 backend with MyBatis, MySQL, Redis, RabbitMQ, Spring Security, JWT, and Actuator. The planned client surface is Vue3; the repository currently contains backend APIs and integration tests rather than a frontend application.

## Users

The primary users are students who want to discover, reserve, purchase, pay for, and collect limited campus creative products. Secondary users are campus creative-operations staff who maintain products, inventory, and activities, plus platform administrators who review content and manage governance.

## Product Purpose

The product is a campus creative flash-sale system for badges, school-spirit goods, mascots, graduation keepsakes, and club merchandise. It lets students complete the path from browsing to reservation or lottery, flash purchase, simulated payment, and pickup-point verification. Success means students can reliably obtain limited products while inventory, order state, and pickup records remain consistent.

## Positioning

Its differentiating mechanism is a campus-identity-aware limited-sale loop: reservation and lottery eligibility, flash-sale inventory controls, asynchronous order handling, simulated payment, and on-campus pickup verification are designed as one connected workflow rather than a generic storefront checkout.

## Operating Context

Students use the web product around campus sale events and collect paid products at enabled campus pickup points. Operations staff configure products, inventory, sale activities, reservations, orders, and verification. Administrators review products and activities and handle governance. Development and integration environments use simulated payment and local or environment-provided infrastructure credentials.

## Capabilities and Constraints

- Account registration, login, JWT authentication, role checks, and disabled-user checks.
- Product catalog browsing with category, keyword, sale-type, price, pagination, and sorting filters.
- Product favorites, activity creation and publication, reservation windows, lottery reservation status, and reservation idempotency.
- Order creation with request-number idempotency, stock decrement and restoration, order ownership checks, order detail/list APIs, simulated payment idempotency, and cancellation of unpaid orders.
- MySQL is the durable source of business state; Redis is used for cache and future atomic flash-sale controls; RabbitMQ carries asynchronous order work and dead-letter topology.
- Real payment settlement, cross-campus logistics, multi-merchant settlement, invoices, and paid SMS/email providers are outside the current scope. Development environments use adapters or simulation for those integrations.
- Public product and activity reads must remain safe for anonymous users; protected operations must enforce student, operator, and administrator roles.

## Evidence on Hand

- Authoritative product requirements: `tasks&requirements/requirements.md`.
- Delivery plan and progress record: `tasks&requirements/tasks.md` and `Progress.md`.
- Current database schema: `src/main/resources/schema.sql`.
- Current backend implementation and integration tests under `src/main/java` and `src/test/java`.
- No binding logo, school visual system, brand assets, or approved marketing content were provided.

## Product Principles

- Make the student purchase path understandable from discovery through pickup.
- Treat eligibility, stock, order state, and payment idempotency as correctness boundaries.
- Prefer explicit status and error feedback over silent retries or ambiguous outcomes.
- Keep public discovery fast and open while enforcing authorization at every state-changing operation.
- Preserve campus-specific terminology and operational traceability.
