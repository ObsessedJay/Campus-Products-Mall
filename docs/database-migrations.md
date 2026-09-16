# Database Migration Notes

An early nine-table development database must first run [`migrations/V1_1__legacy_baseline_upgrade.sql`](migrations/V1_1__legacy_baseline_upgrade.sql), then the current repeatable schema, and finally V5. The legacy upgrade is intentionally one-time and must not be rerun after its columns exist.

## Three-portal account profiles

Run [`migrations/V5__account_profiles_and_admin_bootstrap.sql`](migrations/V5__account_profiles_and_admin_bootstrap.sql) after V1-V4. It adds the one-to-one `student_profile` projection and merchant-to-pickup-point authorization table while keeping `sys_user` as the compatibility account root used by current business foreign keys. Existing student rows are backfilled once, and the migration is safe to rerun.

Administrators are intentionally not self-registered. Create them through a controlled SQL deployment using a BCrypt hash generated outside the repository; never place a plaintext password or a reusable default hash in source control.

The repeatable `src/main/resources/schema.sql` contains the current schema for fresh environments. Existing MySQL databases created before the content-review and pickup-point features need the relevant one-time migrations before deploying the new backend.

## V2 merchant account provisioning

For the current compatibility runtime, create the following tables once before enabling `POST /api/v1/admin/accounts/merchants`:

```sql
CREATE TABLE IF NOT EXISTS merchant_profile (
    account_id BIGINT PRIMARY KEY,
    merchant_name VARCHAR(128) NOT NULL,
    contact_name VARCHAR(64),
    contact_phone VARCHAR(32),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_merchant_profile_user FOREIGN KEY (account_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    INDEX idx_merchant_profile_name (merchant_name)
);

CREATE TABLE IF NOT EXISTS account_operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    operator_account_id BIGINT NOT NULL,
    target_account_id BIGINT NOT NULL,
    action VARCHAR(64) NOT NULL,
    detail VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_account_log_operator_user FOREIGN KEY (operator_account_id) REFERENCES sys_user(id),
    CONSTRAINT fk_account_log_target_user FOREIGN KEY (target_account_id) REFERENCES sys_user(id),
    INDEX idx_account_log_target_created (target_account_id, created_at),
    INDEX idx_account_log_operator_created (operator_account_id, created_at)
);
```

For a new V2 database, use [`database-v2/auth-schema.sql`](database-v2/auth-schema.sql) followed by [`database-v2/profile-schema.sql`](database-v2/profile-schema.sql). Do not run both V2 scripts against the current `sys_user` database; they represent different migration targets.

## Product SKUs

Run [`migrations/V2__product_sku.sql`](migrations/V2__product_sku.sql) once for an existing database. It creates `product_sku`, adds nullable `sku_id` to flash-sale requests, and adds SKU identity plus immutable code/name snapshots to order items. Deploy this migration before the backend version that accepts `skuId`.

Products without rows in `product_sku` continue to use `product.price` and `product.stock`. Once the first SKU is created, enabled SKU stock becomes the aggregate `product.stock`; order deduction and restoration update both levels in the same database transaction.

## Activity pickup fulfillment

Run [`migrations/V3__activity_pickup_fulfillment.sql`](migrations/V3__activity_pickup_fulfillment.sql) once before deploying activity pickup-point fulfillment. It adds the required activity pickup point, immutable order pickup-point snapshots, and the `operator_pickup_point` authorization table. Existing activities and orders are backfilled to the first active pickup point; verify the backfill before applying the non-null constraints in production.

## Product pickup ownership

Run [`migrations/V9__product_pickup_ownership.sql`](migrations/V9__product_pickup_ownership.sql) before deploying product-owned pickup fulfillment. It adds `product.pickup_point_id`, backfills each product from an existing activity when possible and otherwise from the first configured pickup point, then synchronizes existing activity snapshots. Existing products without any available point are taken off sale and remain nullable until a merchant edits them; a database check prevents such rows from entering review or sale. Fresh databases use a non-null column. New activity requests no longer accept a pickup point, and student profile pickup columns remain legacy-only and are not read by the runtime.

## Content review

```sql
ALTER TABLE flash_activity
    ADD COLUMN review_status VARCHAR(32) DEFAULT 'PENDING' AFTER status;

ALTER TABLE product
    ADD COLUMN created_by BIGINT NULL AFTER category_id;

ALTER TABLE flash_activity
    ADD COLUMN created_by BIGINT NULL AFTER product_id;

CREATE TABLE IF NOT EXISTS content_review_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    content_type VARCHAR(32) NOT NULL,
    content_id BIGINT NOT NULL,
    reviewer_id BIGINT NULL,
    reviewer_username VARCHAR(64),
    result VARCHAR(32) NOT NULL,
    reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_review_content (content_type, content_id, created_at),
    INDEX idx_review_result_created (result, created_at)
);
```

Run each `ALTER TABLE` only when its column is absent. The application test profile starts from the repeatable schema and does not need this migration.

## Pickup points and user profiles

```sql
CREATE TABLE IF NOT EXISTS pickup_point (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    campus VARCHAR(128) NOT NULL,
    address VARCHAR(255) NOT NULL,
    longitude DECIMAL(10, 7) NULL,
    latitude DECIMAL(10, 7) NULL,
    opening_hours VARCHAR(128),
    contact_phone VARCHAR(32),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_pickup_point_status_campus (status, campus)
);

ALTER TABLE sys_user
    ADD COLUMN default_pickup_point_id BIGINT NULL AFTER student_no,
    ADD CONSTRAINT fk_user_default_pickup_point
        FOREIGN KEY (default_pickup_point_id) REFERENCES pickup_point(id);
```

Run the `ALTER TABLE` only when `default_pickup_point_id` is absent. Add the enabled campus pickup-point rows for the target environment before exposing profile editing.

Existing `pickup_point` tables need optional coordinates for the AMap pickup-point view:

```sql
ALTER TABLE pickup_point
    ADD COLUMN longitude DECIMAL(10, 7) NULL AFTER address,
    ADD COLUMN latitude DECIMAL(10, 7) NULL AFTER longitude;
```

Populate both coordinates for precise markers. Rows without coordinates remain usable; the frontend attempts address geocoding and keeps the text selector available if geocoding fails.

## Registration email

```sql
ALTER TABLE sys_user
    ADD COLUMN email VARCHAR(128) NULL AFTER username,
    ADD CONSTRAINT uk_sys_user_email UNIQUE (email);
```

The column remains nullable for legacy operator and administrator accounts. New student registrations require a unique verified email address.

## Lottery batches and qualification trace

```sql
CREATE TABLE IF NOT EXISTS lottery_batch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_id BIGINT NOT NULL,
    batch_no VARCHAR(64) NOT NULL,
    random_seed BIGINT NOT NULL,
    total_reservations INT NOT NULL,
    winner_count INT NOT NULL,
    drawn_by BIGINT NULL,
    drawn_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_lottery_batch_activity UNIQUE (activity_id),
    CONSTRAINT uk_lottery_batch_no UNIQUE (batch_no),
    CONSTRAINT fk_lottery_batch_activity FOREIGN KEY (activity_id) REFERENCES flash_activity(id) ON DELETE CASCADE,
    CONSTRAINT fk_lottery_batch_operator FOREIGN KEY (drawn_by) REFERENCES sys_user(id)
);

ALTER TABLE activity_reservation
    ADD COLUMN lottery_batch_id BIGINT NULL AFTER status,
    ADD COLUMN draw_rank INT NULL AFTER lottery_batch_id,
    ADD CONSTRAINT fk_reservation_lottery_batch
        FOREIGN KEY (lottery_batch_id) REFERENCES lottery_batch(id);
```

Run the `ALTER TABLE` only when these columns are absent. One immutable batch is allowed per activity; rerunning the draw endpoint returns the original batch and never replaces qualification results.

## Asynchronous flash-sale requests

```sql
CREATE TABLE IF NOT EXISTS flash_sale_request (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_no VARCHAR(64) NOT NULL,
    activity_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    order_id BIGINT NULL,
    failure_code VARCHAR(64) NULL,
    failure_message VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_flash_sale_request_no UNIQUE (request_no),
    CONSTRAINT ck_flash_sale_request_quantity CHECK (quantity > 0),
    CONSTRAINT fk_flash_sale_request_activity FOREIGN KEY (activity_id) REFERENCES flash_activity(id),
    CONSTRAINT fk_flash_sale_request_product FOREIGN KEY (product_id) REFERENCES product(id),
    CONSTRAINT fk_flash_sale_request_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_flash_sale_request_order FOREIGN KEY (order_id) REFERENCES trade_order(id),
    INDEX idx_flash_sale_request_user_created (user_id, created_at),
    INDEX idx_flash_sale_request_status_updated (status, updated_at)
);
```

Apply this table before enabling `FLASH_SALE_ENABLED`. Redis keys use the `campus-creative:flash-sale:` prefix and expire 24 hours after the activity ends by default.

## Flash-sale manual compensation audit

```sql
CREATE TABLE IF NOT EXISTS flash_sale_compensation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id BIGINT NOT NULL,
    request_no VARCHAR(64) NOT NULL,
    operated_by BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    previous_failure_code VARCHAR(64) NULL,
    previous_failure_message VARCHAR(500) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    failure_message VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    CONSTRAINT fk_flash_sale_compensation_request FOREIGN KEY (request_id)
        REFERENCES flash_sale_request(id) ON DELETE CASCADE,
    CONSTRAINT fk_flash_sale_compensation_operator FOREIGN KEY (operated_by) REFERENCES sys_user(id),
    INDEX idx_flash_sale_compensation_request_created (request_no, created_at),
    INDEX idx_flash_sale_compensation_status_created (status, created_at)
);
```

Apply this table after `flash_sale_request`. No enum migration is required because request states are stored as `VARCHAR`; the application now also uses the transient `COMPENSATING` state.

## Inventory reconciliation audit

```sql
CREATE TABLE IF NOT EXISTS inventory_reconciliation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_id BIGINT NOT NULL,
    database_stock INT NOT NULL,
    redis_stock_before INT NULL,
    redis_stock_after INT NOT NULL,
    pending_quantity INT NOT NULL,
    difference_before INT NULL,
    reason VARCHAR(500) NOT NULL,
    adjusted_by BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    failure_message VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_inventory_reconciliation_activity FOREIGN KEY (activity_id)
        REFERENCES flash_activity(id) ON DELETE CASCADE,
    CONSTRAINT fk_inventory_reconciliation_operator FOREIGN KEY (adjusted_by) REFERENCES sys_user(id),
    INDEX idx_inventory_reconciliation_activity_created (activity_id, created_at),
    INDEX idx_inventory_reconciliation_status_created (status, created_at)
);
```

Create this table before exposing the operator inventory page. Existing `flash_sale_request.status` columns require no DDL change; the transient `ACCEPTING` value uses the existing `VARCHAR(32)` field.

## Product image galleries

Existing databases created before product-owned image galleries need the following table:

```sql
CREATE TABLE IF NOT EXISTS product_image (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    object_name VARCHAR(128) NOT NULL,
    display_name VARCHAR(100) NULL,
    original_name VARCHAR(255) NULL,
    url VARCHAR(500) NOT NULL,
    content_type VARCHAR(64) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_product_image_object UNIQUE (object_name),
    CONSTRAINT ck_product_image_size CHECK (size_bytes > 0),
    CONSTRAINT ck_product_image_sort CHECK (sort_order >= 0),
    CONSTRAINT fk_product_image_product FOREIGN KEY (product_id)
        REFERENCES product(id) ON DELETE CASCADE,
    CONSTRAINT fk_product_image_creator FOREIGN KEY (created_by) REFERENCES sys_user(id),
    INDEX idx_product_image_product_sort (product_id, sort_order, id)
);
```

Create this table before enabling the product-gallery management endpoints. Existing values in `product.cover_url` remain valid; the first newly uploaded owned image fills only an empty cover, and subsequent cover replacement is managed by the application.

## Role-scoped account identity

Existing databases with globally unique user email addresses must apply `docs/migrations/V6__role_scoped_account_identity.sql`:

```sql
ALTER TABLE sys_user
    DROP INDEX uk_sys_user_email,
    ADD CONSTRAINT uk_sys_user_email_role UNIQUE (email, role);
```

This preserves every existing account and username. New account usernames are generated from `role + email`, while user-facing login, registration, and password reset query by both email and portal role. The merchant portal treats legacy `OPERATOR` accounts as merchant accounts for compatibility.

## Merchant profile logo

Apply `docs/migrations/V7__merchant_profile_logo.sql` before enabling merchant registration image uploads:

```sql
ALTER TABLE merchant_profile
    ADD COLUMN logo_url VARCHAR(500) NULL AFTER merchant_name;
```

The URL references the existing image object endpoint. Storage reference checks include this column, so active merchant images are not treated as orphaned objects.
