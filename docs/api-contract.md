# P0 API Contract

All endpoints use the `/api/v1` prefix and return the following envelope:

```json
{"success":true,"code":"OK","message":"success","data":{}}
```

For business failures, `success` is `false`, `data` is `null`, and `code` is a stable machine-readable value. Protected endpoints require `Authorization: Bearer <jwt>`.

## Authentication

| Method | Path | Auth | Body | Data |
| --- | --- | --- | --- | --- |
| GET | `/auth/captcha` | public | query `previousCaptchaId?` | `{ captchaId, imageData, expiresInSeconds }` |
| POST | `/auth/email-codes` | public | `{ email, captchaId, captchaCode }` | `{ expiresInSeconds, retryAfterSeconds }` (`202`) |
| POST | `/auth/register` | public | `{ email, password, confirmPassword, nickname, emailCode }` | `{ token, userId, email, nickname, role }` |
| POST | `/auth/merchants/email-codes` | public | `{ email, captchaId, captchaCode }` | `{ expiresInSeconds, retryAfterSeconds }` (`202`) |
| POST | `/auth/merchants/register` | public | JSON body, or multipart `data` JSON + optional `logo` image | `{ token, userId, email, nickname, role }` |
| POST | `/auth/login` | public | `{ email, password, captchaId, captchaCode, portalRole }` | `{ token, userId, email, nickname, role }` |
| POST | `/auth/refresh` | refresh cookie | none | rotated `{ token, userId, email, nickname, role }` |
| POST | `/auth/logout` | public | none | `null`, and clears the refresh cookie |
| POST | `/auth/password-reset/email-code` | public | `{ email, captchaId, captchaCode, portalRole }` | `{ expiresInSeconds, retryAfterSeconds }` (`202`) |
| POST | `/auth/password-reset` | public | `{ email, emailCode, newPassword, portalRole }` | `null` |
| GET | `/auth/me` | user | none | `{ status: "authenticated" }` |

Captcha challenges are stored in Redis for five minutes and may be validated repeatedly until expiry. Passing `previousCaptchaId` while requesting a replacement deletes the old Redis key before the new challenge is created. Registration and password-reset email codes use separate Redis keys, are consumed atomically on validation, and have independent resend cooldowns. Password-reset keys also include `portalRole`, so accounts sharing an email cannot consume each other's code. Password-reset email requests return the same accepted receipt for unknown addresses so account registration state is not exposed.

Email is the only user-facing login identifier. Accounts are unique by `(email, role)`, so the same email may independently own student, merchant, and database-provisioned administrator accounts; duplicate registration within the same portal remains rejected. `portalRole` selects the account for login and password reset. Registration requires matching `password` and `confirmPassword`; confirmation is validated before the email code is consumed and is never persisted. Student registration does not accept a username, school, student number, or role. The backend assigns the fixed school `成都信息工程大学` and maintains a non-public role-scoped hashed compatibility identifier for existing principal lookups.

Login and registration responses set an opaque refresh token in a `HttpOnly`, `SameSite=Strict` cookie scoped to `/api/v1/auth`; the raw value is never returned in JSON or stored by frontend JavaScript. Redis stores only its SHA-256 digest. `POST /auth/refresh` consumes the token once, rotates it, and returns a new short-lived access JWT. Password reset revokes every refresh session for that user. Set `REFRESH_COOKIE_SECURE=true` behind HTTPS. SMTP credentials are environment-only (`MAIL_USERNAME`, `MAIL_PASSWORD`).

### Merchant account provisioning

| Method | Path | Role | Body | Data |
| --- | --- | --- | --- | --- |
| POST | `/admin/accounts/merchants` | ADMIN | `{ email, password, confirmPassword, displayName, merchantName, contactName?, contactPhone? }` | merchant account and profile (`201`) |

Administrators can provision merchant accounts, and merchants can also use the public merchant registration endpoint. Public registration accepts an optional merchant logo/storefront image as PNG, JPEG, GIF, or WebP through multipart form data; the persisted URL is stored in `merchant_profile.logo_url`. Merchant email uniqueness is enforced within the merchant portal, independently of student and administrator accounts. The role is assigned by the server as `MERCHANT`, and password confirmation is checked before any account row is written. Neither password nor confirmation is returned. Administrator account creation, merchant profile creation, and the `CREATE_MERCHANT_ACCOUNT` audit record commit together.

The current compatibility runtime also accepts the legacy `OPERATOR` role on merchant workbench APIs. New V2 accounts use `MERCHANT`; this role mapping will be removed as part of the one-time `sys_user` to `auth_account` migration.

## Request protection

Redis-backed request protection runs before controller business logic:

- `/api/v1/auth/**` is limited by direct client IP in fixed windows. Captcha, login, student and merchant registration, email-code, password-reset, refresh, logout, and session-check routes use independent buckets so traffic on one entry does not consume another entry's quota.
- Authenticated write APIs are limited by the authenticated internal identity; unauthenticated writes fall back to direct client IP.
- `POST /orders/flash-sale` uses a stricter independent per-user window.
- Repeated invalid email/password attempts are counted by both IP and normalized email. Reaching the configured threshold places both dimensions on a temporary blacklist.
- Client IP uses the direct socket address. A reverse proxy must be configured as a trusted forwarding boundary before forwarded IP headers are used.

Rate limits return HTTP `429` with code `RATE_LIMITED`. Temporary blacklist matches return HTTP `403` with code `REQUEST_BLACKLISTED`. Request-guard Redis failures are logged and fail open so ordinary traffic is not blocked by a degraded protection dependency; flash-sale inventory operations keep their existing fail-closed Redis behavior.

Duplicate business submissions remain protected by the endpoint-level identifiers and unique constraints: order and flash-sale `requestNo`, payment `idempotencyKey`, one reservation per `(activityId, userId)`, and one favorite per `(userId, productId)`.

## Public discovery

`GET /products` accepts `keyword`, `categoryId`, `saleType`, `minPrice`, `maxPrice`, `sort`, `page`, and `size`. `sort` is `latest`, `sales`, `priceAsc`, or `priceDesc`. The data value is `{ items, total, page, size, totalPages }`.

`GET /products/{id}` returns an on-sale product. `GET /categories` returns active categories. `GET /activities` and `GET /activities/{id}` return published activities that are not terminated.

## Image files

| Method | Path | Role | Body | Data |
| --- | --- | --- | --- | --- |
| POST | `/files/images` | OPERATOR/ADMIN | `multipart/form-data` field `file` | `{ objectName, url, contentType, size }` |
| GET | `/files/images/{objectName}` | public | none | raw image bytes |
| DELETE | `/files/images/{objectName}` | OPERATOR/ADMIN | none | `null` |

Uploads accept PNG, JPEG, GIF, and WebP images up to `MAX_IMAGE_BYTES` (5 MiB by default). Validation uses the file signature and rejects a conflicting declared media type; the client-provided filename is never used as an object key. The service generates an opaque object name and returns a same-origin URL. Set `MINIO_ENABLED=true`, `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, and optionally `MINIO_BUCKET` to enable persistence. The bucket is created on first use. When storage is disabled, write and read operations fail with `STORAGE_UNAVAILABLE` while the rest of the application remains available.

## Product management, specifications and image galleries

| Method | Path | Role | Body | Data |
| --- | --- | --- | --- | --- |
| GET | `/admin/products?keyword=&status=&page=&size=` | OPERATOR/ADMIN | none | product page including every status |
| POST | `/products` | OPERATOR/ADMIN | `{ categoryId?, pickupPointId, name, subtitle?, description?, coverUrl?, saleType, price, stock, limitPerUser }` | created product (`201`) |
| PUT | `/admin/products/{productId}` | OPERATOR/ADMIN | `{ categoryId?, pickupPointId, name, subtitle?, description?, saleType, price, stock, limitPerUser }` | updated product |
| POST | `/admin/products/{productId}/off-sale` | OPERATOR/ADMIN | none | off-sale product |
| POST | `/admin/products/{productId}/on-sale` | OPERATOR/ADMIN | none | relisted product |
| DELETE | `/admin/products/{productId}` | OPERATOR/ADMIN | none | `null` |
| GET | `/admin/categories` | OPERATOR/ADMIN | none | all categories including inactive ones |
| POST | `/admin/categories` | OPERATOR/ADMIN | `{ name, sortOrder? }` | created category (`201`) |
| PUT | `/admin/categories/{categoryId}` | OPERATOR/ADMIN | `{ name, sortOrder?, status }` | updated category |
| DELETE | `/admin/categories/{categoryId}` | OPERATOR/ADMIN | none | `null` |
| GET | `/products/{productId}/skus` | public | none | enabled SKU array |
| GET | `/admin/products/{productId}/skus` | OPERATOR/ADMIN | none | all SKU array |
| POST | `/admin/products/{productId}/skus` | OPERATOR/ADMIN | `{ skuCode, name, price, stock, enabled, sortOrder }` | created SKU (`201`) |
| PUT | `/admin/products/{productId}/skus/{skuId}` | OPERATOR/ADMIN | `{ skuCode, name, price, stock, enabled, sortOrder }` | updated SKU |
| DELETE | `/admin/products/{productId}/skus/{skuId}` | OPERATOR/ADMIN | none | `null` |
| GET | `/products/{productId}/images` | public | none | product image array |
| GET | `/admin/products/{productId}/images` | OPERATOR/ADMIN | none | product image array |
| POST | `/admin/products/{productId}/images` | OPERATOR/ADMIN | `multipart/form-data` fields `file`, optional `displayName` (up to 100 characters), and optional `sortOrder` | created product image (`201`) |
| PUT | `/admin/products/{productId}/images/{imageId}/sort` | OPERATOR/ADMIN | `{ sortOrder }` | updated product image |
| DELETE | `/admin/products/{productId}/images/{imageId}` | OPERATOR/ADMIN | none | `null` |

The management product query accepts an optional keyword and one of `DRAFT`, `PENDING_REVIEW`, `ON_SALE`, `OFF_SALE`, or `REJECTED`; unlike the public catalog, it returns products in every lifecycle state. New products require an enabled `pickupPointId` and enter `PENDING_REVIEW`. Updating category, pickup point, name, subtitle, description, sale type, or price also returns the product to `PENDING_REVIEW`; changing only stock or `limitPerUser` preserves its current status. Product cover ownership remains with the image-gallery endpoints and cannot be overwritten by the management update.

Operators can take a product from `ON_SALE` to `OFF_SALE` and relist it only from `OFF_SALE`. Since content edits move the product to `PENDING_REVIEW`, relisting cannot bypass review. Physical deletion rejects `ON_SALE` products and any product referenced by an order item or activity; favorites are removed only when an otherwise unreferenced product is deleted. Category management exposes active and inactive rows, supports ordering and enable/disable state, and rejects deletion while any product references the category. The public `/categories` endpoint continues to return active categories only.

Each SKU is a real stock unit with `{ id, productId, skuCode, name, price, stock, enabled, sortOrder }`. Enabled SKU stock is summed into `product.stock`; once a product has SKUs, aggregate stock cannot be edited through the product endpoint. Creating or deleting a SKU, or changing its code, name, price, or enabled state returns the product to `PENDING_REVIEW`; changing only SKU stock or order preserves the current product status. A SKU referenced by an order that can still be cancelled or refunded cannot be deleted. Deleting the final otherwise-unused SKU preserves the current product stock so the operator can return the product to single-stock mode. Historical order items keep the SKU code and name snapshot even when the source SKU is later renamed or deleted.

Each product image contains `{ id, productId, objectName, displayName?, url, contentType, sizeBytes, sortOrder, createdBy, createdAt }`. The server records a sanitized original filename for internal traceability but never exposes it in API responses or uses it as the MinIO key. The public gallery is available only for `ON_SALE` products, while the management gallery can inspect products in any status. A product accepts at most `PRODUCT_MAX_IMAGES` images (8 by default), and `sortOrder` must be non-negative; results are ordered by `sortOrder` and then image ID.

The first uploaded image becomes the product cover when `coverUrl` is empty. Deleting the current cover promotes the first remaining image, or clears the cover when the gallery becomes empty. Upload failures after object persistence compensate by deleting the new object. Normal image deletion removes the database ownership record transactionally and deletes the MinIO object only after the database transaction commits.

## User profile and pickup points

| Method | Path | Auth | Body | Data |
| --- | --- | --- | --- | --- |
| GET | `/users/me` | user | none | current profile |
| PUT | `/users/me` | user | `{ nickname }` | updated profile |
| GET | `/pickup-points` | user | none | enabled pickup point array |

The profile response exposes the fixed school `成都信息工程大学` and does not expose a username, student number, or pickup-point preference. Pickup-point responses may include `longitude` and `latitude`; both fields are nullable for legacy rows. Students do not select or maintain order pickup locations.

## Activities and reservations

| Method | Path | Role | Body | Data |
| --- | --- | --- | --- | --- |
| GET | `/admin/activities` | OPERATOR/ADMIN | none | all activities including unpublished, rejected and terminated states |
| POST | `/activities` | OPERATOR/ADMIN | activity creation payload; pickup point is inherited from product | activity |
| PUT | `/admin/activities/{id}` | OPERATOR/ADMIN | activity creation payload; pickup point is inherited from product | updated activity resubmitted for review |
| POST | `/activities/{id}/publish` | OPERATOR/ADMIN | none | activity |
| POST | `/activities/{id}/terminate` | OPERATOR/ADMIN | `{ reason }` | activity |
| POST | `/activities/{id}/reservations` | STUDENT | none | reservation |
| GET | `/activities/{id}/reservation` | STUDENT | none | reservation |
| POST | `/activities/{id}/lottery` | OPERATOR/ADMIN | `{ seed?, winnerCount? }` | draw batch and reservation results |
| GET | `/activities/{id}/lottery` | OPERATOR/ADMIN | none | existing draw batch and reservation results |
| GET | `/admin/activities/{id}/reservations?status=` | OPERATOR/ADMIN | none | reservation roster with student identity and lottery trace fields |
| GET | `/admin/activities/{id}/reservations/export?status=` | OPERATOR/ADMIN | none | raw `.xlsx` reservation roster download |

Reservation creation is idempotent for `(activityId, currentUser)`: repeated requests return the original reservation number. Lottery draws are idempotent per activity and preserve the original batch number, random seed, draw rank, and result.

The management roster can be filtered by `PENDING`, `QUALIFIED`, `NOT_QUALIFIED`, or `CANCELLED`; omitting `status` returns every reservation. Each row includes the activity, student email and nickname, reservation number, qualification status, lottery batch number, draw rank, and timestamps. An unsupported status returns `INVALID_RESERVATION_STATUS`. The export endpoint applies the same filter and returns an Excel workbook directly with content type `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` rather than the JSON response envelope.

Activity lifecycle transitions are defined by Spring Statemachine. The supported event path is edit/resubmit and review approval or rejection, followed by direct publication to `PENDING` or reservation publication to `RESERVING`, then reservation close, `RUNNING`, `ENDED`, or an explicit `TERMINATED` event. Illegal edges return `ACTIVITY_TRANSITION_NOT_ALLOWED`. Creation and every edit set `reviewStatus` to `PENDING`; publication requires `APPROVED`, an on-sale product, sufficient product stock and a future sale window. Lottery mode requires a complete reservation window ending before the sale begins. State writes use the expected database status so a concurrent transition cannot be overwritten by a stale request.

An activity inherits the enabled pickup point from its product and does not accept a merchant-selected `pickupPointId`. The inherited value is retained on the activity for compatibility and fulfillment traceability.

## Content reviews

| Method | Path | Role | Body | Data |
| --- | --- | --- | --- | --- |
| GET | `/admin/reviews?type=&status=PENDING_REVIEW` | ADMIN | none | pending product and activity review items |
| POST | `/admin/reviews/{type}/{id}/approve` | ADMIN | none | approved product or activity |
| POST | `/admin/reviews/{type}/{id}/reject` | ADMIN | `{ reason }` | rejected product or activity |
| GET | `/admin/reviews/{type}/{id}/logs` | ADMIN | none | latest review logs for one content item |
| GET | `/admin/reviews/logs?type=&contentId=&limit=` | ADMIN | none | filtered recent review logs |

`type` is `PRODUCT` or `ACTIVITY`. Rejection requires a non-blank reason of at most 500 characters. The shared log endpoint returns at most 100 records and rejects a `limit` outside `1-100`. Review state updates are conditional on the expected pending state and pending review marker so concurrent or repeated approval and rejection requests cannot create duplicate successful audit entries.

## Orders and fulfillment

| Method | Path | Role | Body | Data |
| --- | --- | --- | --- | --- |
| POST | `/orders` | STUDENT | `{ requestNo, productId, quantity, activityId?, skuId? }` | order (`201 Created`) with pickup-point snapshot |
| POST | `/orders/flash-sale` | STUDENT | `{ requestNo, productId, quantity, activityId, skuId? }` | flash-sale request (`202 Accepted`) |
| GET | `/orders/flash-sale/{requestNo}` | STUDENT | none | current flash-sale request result |
| GET | `/orders?status=...` | STUDENT | none | order array |
| GET | `/orders/{id}` | STUDENT | none | `{ order, items, payment?, pickupVerification?, refund? }` |
| POST | `/orders/{id}/pay` | STUDENT | `{ idempotencyKey }` | payment record |
| POST | `/orders/{id}/cancel` | STUDENT | none | order |
| GET | `/orders/{id}/pickup` | STUDENT | none | pickup verification |
| POST | `/orders/{id}/refund` | STUDENT | `{ reason }` | refund record |
| POST | `/orders/verification` | OPERATOR/ADMIN | `{ pickupCode }` | completed order; operators require point authorization |
| POST | `/orders/{id}/refund/approve` | OPERATOR/ADMIN | `{ reason }` | refund record |
| POST | `/orders/{id}/refund/reject` | OPERATOR/ADMIN | `{ reason }` | refund record |
| GET | `/admin/orders/export?activityId=&status=&pickupPointId=&verificationStatus=` | MERCHANT/OPERATOR/ADMIN | none | raw `.xlsx` order and verification report download |

`requestNo` makes order and flash-sale request creation idempotent per user. Activity purchases use the flash-sale endpoint: Redis + Lua atomically validates the activity state, stock and per-user limit, then RabbitMQ creates the order asynchronously. The client polls the result until `PENDING` changes to `SUCCEEDED` (with `orderId`) or `FAILED` (with `failureCode` and `failureMessage`). Messages are retried up to three deliveries; exhausted technical failures are sent to the dead-letter queue. Cancellation, payment timeout and approved refunds return both MySQL and Redis inventory.

The synchronous `/orders` endpoint remains available for normal products. Products with SKU rows require an enabled, product-owned `skuId`; products without SKUs remain backward compatible. Order creation atomically deducts both SKU stock and aggregate product stock, snapshots the SKU price/code/name, and restores both inventory levels on cancellation, payment timeout, or approved refund. Activity orders additionally deduct and restore activity inventory. Both paths validate product match, sale window, cumulative limit and lottery qualification. `idempotencyKey` makes simulated payment idempotent. A pickup code can be verified once; verification is rejected for unpaid, cancelled, refunding, refunded, or already completed orders.

Every order uses the enabled pickup point configured on its product. The order persists `pickupPointId`, `pickupPointName`, and `pickupPointAddress`, so later product or point edits cannot change the promised location. `GET` and `PUT /admin/operators/{operatorId}/pickup-points` are ADMIN-only; `PUT` replaces the operator's authorized point set. Operators may verify only orders at an authorized point, while administrators may verify every point.

The management export accepts optional activity, order-status and pickup-point filters. `verificationStatus` is `VERIFIED` or `PENDING`; omitting it includes both results. Order status uses the `OrderStatus` enum and unsupported values return `INVALID_ORDER_STATUS` or `INVALID_VERIFICATION_STATUS`. Each workbook row contains student identity, order and activity identifiers, product/SKU snapshots, amounts, pickup-point snapshots, pickup code, verification operator and timestamps. Operators and merchants only receive rows from their authorized pickup points even when `pickupPointId` is omitted; administrators may export every point. The endpoint returns the workbook directly with the XLSX content type instead of the JSON response envelope.

## Student realtime status

Students can connect to `GET /ws/flash-sale` with a WebSocket upgrade. Authentication uses the `Sec-WebSocket-Protocol` header so JWTs are not exposed in URLs or access logs. Browser clients request both protocols in this order:

```text
campus-flash-sale
bearer.<JWT>
```

The server accepts active `STUDENT` tokens and negotiates `campus-flash-sale`. Operator, administrator, missing, and invalid tokens are rejected during the handshake. Allowed browser origins are configured with `WEBSOCKET_ALLOWED_ORIGIN_PATTERNS`.

After connection, the server emits these JSON events:

```json
{"type":"READY","updatedAt":"2026-08-21T18:00:00"}
{"type":"FLASH_SALE_STATUS","requestNo":"act-42-...","status":"PENDING","updatedAt":"2026-08-21T18:00:01"}
{"type":"ORDER_STATUS","order":{"id":91,"orderNo":"ORDER-91","status":"WAIT_VERIFICATION"},"updatedAt":"2026-08-21T18:00:02"}
{"type":"ACTIVITY_STATUS","activity":{"id":42,"name":"迎新限定发行","status":"RUNNING"},"updatedAt":"2026-08-21T18:00:03"}
```

`FLASH_SALE_STATUS` is delivered only to the request owner. `ORDER_STATUS` contains the current order and is delivered only to its owning student. `ACTIVITY_STATUS` is broadcast to connected students when a published activity changes state. Order and activity events are emitted only after the database transaction commits.

WebSocket delivery is an acceleration channel rather than the source of truth. Clients keep the original `requestNo` when falling back to `GET /orders/flash-sale/{requestNo}`, reload orders through `GET /orders`, and refresh activity state through `GET /activities/{id}` after connection loss or a push timeout.

## Inventory operations

| Method | Path | Role | Body | Data |
| --- | --- | --- | --- | --- |
| GET | `/admin/activities/{activityId}/inventory` | OPERATOR/ADMIN | none | database, Redis, pending, paid and expected stock snapshot |
| GET | `/admin/activities/{activityId}/inventory/reconciliations` | OPERATOR/ADMIN | none | latest 20 reconciliation audit records |
| POST | `/admin/activities/{activityId}/inventory/reconcile` | OPERATOR/ADMIN | `{ reason }` | completed reconciliation audit record |

The expected Redis stock is `databaseAvailableStock - pendingQuantity`. Reconciliation rebuilds activity state, available stock and per-user purchase counters from active orders plus pending flash-sale requests. It requires an operator reason and persists a `PENDING`, `SUCCEEDED`, or `FAILED` audit record rather than silently overwriting cache state.

Reconciliation holds a short activity-scoped Redis lock. Flash-sale submissions use the transient `ACCEPTING` state and settle to `PENDING` or `FAILED` before the rebuild snapshot is calculated. Requests arriving while the lock is held receive `INVENTORY_RECONCILIATION_RUNNING` and may retry with the same `requestNo` after the operation finishes.

## Flash-sale failure compensation

| Method | Path | Role | Body | Data |
| --- | --- | --- | --- | --- |
| GET | `/admin/flash-sale/failed-requests?activityId=&failureCode=` | OPERATOR/ADMIN | none | latest 50 failed requests |
| GET | `/admin/flash-sale/failed-requests/{requestNo}/compensations` | OPERATOR/ADMIN | none | latest 20 compensation audit records |
| POST | `/admin/flash-sale/failed-requests/{requestNo}/retry` | OPERATOR/ADMIN | `{ reason }` | pending compensation audit record |

Retry uses the conditional transition `FAILED -> COMPENSATING -> PENDING`, so concurrent or repeated operator submissions cannot enqueue the same request twice. The service revalidates the student account, product, activity window, qualification, purchase limit and inventory, then atomically reserves Redis inventory with the original `requestNo` and republishes the normal order message.

Every attempt requires a reason and records the operator plus the previous failure details. Queue publication failure restores the request to `FAILED` and releases its Redis reservation. Once queued, the normal consumer changes the audit from `PENDING` to `SUCCEEDED` or `FAILED` together with the request result; no failure is treated as a successful compensation merely because publishing succeeded.

## Frontend integration rules

- Use the relative `/api/v1` base URL; Vite proxies `/api` to the Spring Boot server in development.
- Frontend reads and writes always use the backend API. Connectivity failures and HTTP errors are surfaced to the page and must never be replaced with local business data.
- Keep `requestNo` and `idempotencyKey` stable across retries of the same user action. Poll a flash-sale request with the same `requestNo`; do not submit a new business request while it is `PENDING`.
- Treat `OrderStatus`, `ActivityStatus`, and `ProductStatus` as server-owned state; the client may display or disable actions but must not infer a successful transition locally.
- On an authenticated request returning HTTP `401`, submit one shared refresh request and retry the original request at most once. A failed refresh clears the local access token and requires a new login.
## 评价与举报治理

### 商品评价

- `GET /api/v1/products/{productId}/reviews`：公开查询商品当前可见评价，返回昵称、1-5 星评分、正文、最多 3 张站内图片地址和发布时间。
- `POST /api/v1/orders/{orderId}/reviews`：学生为本人已完成订单中的商品提交评价。请求体包含 `productId`、`rating`、`content` 和可选 `imageUrls`。
- 同一订单中的同一商品只能评价一次，唯一约束为 `(order_id, product_id)`；未完成订单、非本人订单和不属于该订单的商品返回 `REVIEW_ORDER_NOT_ELIGIBLE`。
- 评价图片必须先通过现有图片上传接口取得 `/api/v1/files/images/` 下的站内地址，单条评价最多 3 张。

### 举报治理

- `POST /api/v1/reports`：学生举报商品或评价，请求体为 `targetType`（`PRODUCT`/`REVIEW`）、`targetId` 和 `reason`。同一用户重复举报同一目标时幂等返回原记录。
- `GET /api/v1/admin/reports?status=PENDING`：管理员按 `PENDING`、`RESOLVED` 或 `REJECTED` 查询举报队列。
- `POST /api/v1/admin/reports/{id}/resolve`：确认举报成立，请求体为 `result`；评价举报成立后评价立即转为隐藏。
- `POST /api/v1/admin/reports/{id}/reject`：驳回举报，请求体为 `result`。
- 举报仅允许从 `PENDING` 处理一次，处理人、结果和时间写入审计字段；重复处理返回 `REPORT_ALREADY_HANDLED`。

## 站内消息

- `GET /api/v1/users/me/messages?unreadOnly=false`：查询当前用户最近 100 条站内消息，支持仅看未读。
- `GET /api/v1/users/me/messages/unread-count`：返回 `{ count }` 未读数量。
- `POST /api/v1/users/me/messages/{id}/read`：将当前用户的一条消息标记为已读；不能操作其他用户消息。
- `POST /api/v1/users/me/messages/read-all`：将当前用户全部未读消息标记为已读，返回 `{ updated }`。
- 活动预约、抽签结果、支付成功和领取核销通过独立 RabbitMQ 通知队列异步生成消息。`notice.business_key` 与 `(notice_id, user_id)` 唯一约束保证重复投递不会产生重复用户消息。
- 通知只在业务事务提交后发布；活动提醒在预约成功后生成并明确开售时间，支付和核销通知链接到订单，抽签通知链接到活动。

## 搜索、图片与运营治理

## 三端认证入口

- `POST /api/v1/auth/login`：学生、商家和管理员共用账号认证，请求增加 `portalRole`（`STUDENT`、`MERCHANT`、`ADMIN`）；账号实际角色与入口不一致时返回 `LOGIN_PORTAL_MISMATCH`。
- `POST /api/v1/auth/register` 与 `POST /api/v1/auth/email-codes`：学生邮箱验证注册。
- `POST /api/v1/auth/merchants/email-codes`：发送商家注册邮箱验证码。
- `POST /api/v1/auth/merchants/register`：商家公开注册，请求包含 `email`、`password`、`confirmPassword`、`displayName`、`merchantName` 和 `emailCode`；`displayName` 可留空且最多 6 个字符，账号与商家资料在同一事务写入。
- 管理员不提供注册接口，账号通过数据库初始化或受控运维脚本创建。

### 商品搜索

- `GET /api/v1/products` 的既有关键词、分类、发售方式、价格、排序和分页参数会在 `ELASTICSEARCH_ENABLED=true` 时优先使用 Elasticsearch。
- 关键词同时匹配商品名称、卖点和描述；ES 连接失败、超时、非成功响应或响应解析失败时自动执行相同条件的 MySQL 查询。
- `POST /api/v1/admin/search-index/rebuild`：管理员全量重建商品索引，返回 `{ indexed }`。搜索未启用时返回 `SEARCH_UNAVAILABLE`。

### 图片压缩与清理

- 图片上传继续执行 5MB 限制与 PNG/JPEG/GIF/WebP 文件头校验。JPEG/PNG 超过配置长边时等比缩放，且仅在编码结果更小时使用压缩内容。
- 上传对象写入 `storage_object` 台账。直接删除被商品图或评价图引用的对象返回 `FILE_IN_USE`。
- 定时任务按 `IMAGE_ORPHAN_RETENTION_HOURS` 保留期清理无业务引用的对象，删除前再次检查引用；失败仅记录日志并在后续周期重试。

### 数据看板

- `GET /api/v1/admin/dashboard?startDate=&endDate=&activityId=&productId=`：MERCHANT、OPERATOR、ADMIN 查询用户、商品、活动、预约、订单、成功支付金额、可核销数、已核销数和核销率。
- 日期为闭区间自然日，默认最近 30 天，最长两年；活动和商品筛选会作用于预约、订单、支付及核销统计。

### 用户治理

- `GET /api/v1/admin/users?keyword=&status=&page=&size=`：管理员搜索用户。
- `GET /api/v1/admin/users/{userId}`：返回账号、预约数、订单数、举报数和信用分历史。
- `POST /api/v1/admin/users/{userId}/disable`、`/enable`：请求体 `{ reason }`，按预期状态条件更新并记录操作日志。禁用用户的已有 JWT 会在预约、抢购和支付业务实时状态校验处被拒绝。
- `POST /api/v1/admin/users/{userId}/credit`：请求体 `{ score, reason }`，分值范围 0-100，记录调整前后分值、操作人和原因。
- 举报 `targetType` 新增 `USER`，可将用户纳入现有举报处理队列。

### 系统配置

- `GET /api/v1/admin/system-configs`：管理员查询白名单配置。
- `PUT /api/v1/admin/system-configs/{key}`：请求体 `{ value, reason }`，使用旧值条件更新并写入变更日志。
- `GET /api/v1/admin/system-configs/logs?key=`：查询最近 200 条变更记录。
- 支持 `PAYMENT_TIMEOUT_MINUTES`、`DEFAULT_PURCHASE_LIMIT`、`RESERVATION_LEAD_MINUTES`、`WRITE_RATE_LIMIT`、`FLASH_SALE_RATE_LIMIT` 和 `LOGIN_FAILURE_LIMIT`；每个键均执行整数范围校验。普通订单支付时限及三项风控阈值即时读取最新配置。
