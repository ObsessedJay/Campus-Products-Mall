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

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    email VARCHAR(128) NULL,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(64),
    school VARCHAR(128),
    student_no VARCHAR(64),
    default_pickup_point_id BIGINT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'STUDENT',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    credit_score INT NOT NULL DEFAULT 100,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_user_username UNIQUE (username),
    CONSTRAINT uk_sys_user_email_role UNIQUE (email, role),
    CONSTRAINT ck_sys_user_credit CHECK (credit_score BETWEEN 0 AND 100),
    CONSTRAINT fk_user_default_pickup_point FOREIGN KEY (default_pickup_point_id) REFERENCES pickup_point(id)
);

CREATE TABLE IF NOT EXISTS merchant_profile (
    account_id BIGINT PRIMARY KEY,
    merchant_name VARCHAR(128) NOT NULL,
    logo_url VARCHAR(500) NULL,
    contact_name VARCHAR(64),
    contact_phone VARCHAR(32),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_merchant_profile_user FOREIGN KEY (account_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    INDEX idx_merchant_profile_name (merchant_name)
);

CREATE TABLE IF NOT EXISTS student_profile (
    account_id BIGINT PRIMARY KEY,
    default_pickup_point_id BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_student_profile_user FOREIGN KEY (account_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_student_profile_pickup_point FOREIGN KEY (default_pickup_point_id) REFERENCES pickup_point(id),
    INDEX idx_student_profile_pickup_point (default_pickup_point_id)
);

CREATE TABLE IF NOT EXISTS merchant_pickup_point (
    merchant_account_id BIGINT NOT NULL,
    pickup_point_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (merchant_account_id, pickup_point_id),
    CONSTRAINT fk_merchant_pickup_account FOREIGN KEY (merchant_account_id)
        REFERENCES merchant_profile(account_id) ON DELETE CASCADE,
    CONSTRAINT fk_merchant_pickup_point FOREIGN KEY (pickup_point_id)
        REFERENCES pickup_point(id) ON DELETE CASCADE,
    INDEX idx_merchant_pickup_point (pickup_point_id, merchant_account_id)
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

CREATE TABLE IF NOT EXISTS user_credit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    previous_score INT NOT NULL,
    adjusted_score INT NOT NULL,
    change_amount INT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    operated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_credit_log_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_credit_log_operator FOREIGN KEY (operated_by) REFERENCES sys_user(id),
    INDEX idx_credit_log_user_created (user_id, created_at)
);

CREATE TABLE IF NOT EXISTS system_config (
    config_key VARCHAR(64) PRIMARY KEY,
    config_value VARCHAR(255) NOT NULL,
    value_type VARCHAR(16) NOT NULL,
    description VARCHAR(255) NOT NULL,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_system_config_operator FOREIGN KEY (updated_by) REFERENCES sys_user(id)
);

CREATE TABLE IF NOT EXISTS system_config_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(64) NOT NULL,
    previous_value VARCHAR(255) NOT NULL,
    updated_value VARCHAR(255) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    operated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_config_log_operator FOREIGN KEY (operated_by) REFERENCES sys_user(id),
    INDEX idx_config_log_key_created (config_key, created_at)
);

INSERT INTO system_config (config_key, config_value, value_type, description)
SELECT 'PAYMENT_TIMEOUT_MINUTES', '15', 'INTEGER', '非活动订单支付超时分钟数'
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'PAYMENT_TIMEOUT_MINUTES');
INSERT INTO system_config (config_key, config_value, value_type, description)
SELECT 'DEFAULT_PURCHASE_LIMIT', '1', 'INTEGER', '商品默认单人限购数量'
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'DEFAULT_PURCHASE_LIMIT');
INSERT INTO system_config (config_key, config_value, value_type, description)
SELECT 'RESERVATION_LEAD_MINUTES', '30', 'INTEGER', '预约最小提前分钟数'
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'RESERVATION_LEAD_MINUTES');
INSERT INTO system_config (config_key, config_value, value_type, description)
SELECT 'WRITE_RATE_LIMIT', '120', 'INTEGER', '用户写请求每窗口阈值'
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'WRITE_RATE_LIMIT');
INSERT INTO system_config (config_key, config_value, value_type, description)
SELECT 'FLASH_SALE_RATE_LIMIT', '10', 'INTEGER', '抢购请求每窗口阈值'
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'FLASH_SALE_RATE_LIMIT');
INSERT INTO system_config (config_key, config_value, value_type, description)
SELECT 'LOGIN_FAILURE_LIMIT', '5', 'INTEGER', '登录失败封禁阈值'
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'LOGIN_FAILURE_LIMIT');

CREATE TABLE IF NOT EXISTS storage_object (
    object_name VARCHAR(128) PRIMARY KEY,
    content_type VARCHAR(64) NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_storage_object_creator FOREIGN KEY (created_by) REFERENCES sys_user(id),
    INDEX idx_storage_object_created (created_at)
);

CREATE TABLE IF NOT EXISTS product_category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_product_category_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_id BIGINT,
    created_by BIGINT,
    pickup_point_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    subtitle VARCHAR(255),
    description VARCHAR(2000),
    cover_url VARCHAR(500),
    sale_type VARCHAR(32) NOT NULL DEFAULT 'FLASH_SALE',
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    price DECIMAL(10,2) NOT NULL,
    stock INT NOT NULL DEFAULT 0,
    sold_count INT NOT NULL DEFAULT 0,
    limit_per_user INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_product_price CHECK (price >= 0),
    CONSTRAINT ck_product_stock CHECK (stock >= 0),
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES product_category(id),
    CONSTRAINT fk_product_pickup_point FOREIGN KEY (pickup_point_id) REFERENCES pickup_point(id),
    INDEX idx_product_status_category (status, category_id),
    INDEX idx_product_pickup_point (pickup_point_id),
    INDEX idx_product_sale_type_price (status, sale_type, price)
);

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
    CONSTRAINT fk_product_image_product FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE,
    CONSTRAINT fk_product_image_creator FOREIGN KEY (created_by) REFERENCES sys_user(id),
    INDEX idx_product_image_product_sort (product_id, sort_order, id)
);

CREATE TABLE IF NOT EXISTS product_sku (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    sku_code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    stock INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_product_sku_code UNIQUE (product_id, sku_code),
    CONSTRAINT ck_product_sku_price CHECK (price >= 0),
    CONSTRAINT ck_product_sku_stock CHECK (stock >= 0),
    CONSTRAINT ck_product_sku_sort CHECK (sort_order >= 0),
    CONSTRAINT fk_product_sku_product FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE,
    INDEX idx_product_sku_product_enabled_sort (product_id, enabled, sort_order)
);

CREATE TABLE IF NOT EXISTS flash_activity (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    product_id BIGINT NOT NULL,
    pickup_point_id BIGINT NOT NULL,
    created_by BIGINT,
    mode VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'UNPUBLISHED',
    review_status VARCHAR(32) DEFAULT 'PENDING',
    reservation_start_at TIMESTAMP NULL,
    reservation_end_at TIMESTAMP NULL,
    start_at TIMESTAMP NOT NULL,
    end_at TIMESTAMP NOT NULL,
    stock INT NOT NULL,
    limit_per_user INT NOT NULL DEFAULT 1,
    payment_timeout_minutes INT NOT NULL DEFAULT 15,
    rule_description VARCHAR(2000),
    terminate_reason VARCHAR(500),
    terminated_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_activity_time CHECK (end_at > start_at),
    CONSTRAINT ck_activity_stock CHECK (stock > 0),
    CONSTRAINT fk_activity_product FOREIGN KEY (product_id) REFERENCES product(id),
    CONSTRAINT fk_activity_pickup_point FOREIGN KEY (pickup_point_id) REFERENCES pickup_point(id),
    INDEX idx_activity_status_start (status, start_at)
);

CREATE TABLE IF NOT EXISTS operator_pickup_point (
    operator_id BIGINT NOT NULL,
    pickup_point_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (operator_id, pickup_point_id),
    CONSTRAINT fk_operator_pickup_point_user FOREIGN KEY (operator_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_operator_pickup_point_location FOREIGN KEY (pickup_point_id) REFERENCES pickup_point(id) ON DELETE CASCADE,
    INDEX idx_operator_pickup_point_location (pickup_point_id, operator_id)
);

CREATE TABLE IF NOT EXISTS content_review_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    content_type VARCHAR(32) NOT NULL,
    content_id BIGINT NOT NULL,
    reviewer_id BIGINT,
    reviewer_username VARCHAR(64),
    result VARCHAR(32) NOT NULL,
    reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_review_content (content_type, content_id, created_at),
    INDEX idx_review_result_created (result, created_at)
);

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

CREATE TABLE IF NOT EXISTS activity_reservation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    reservation_no VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    lottery_batch_id BIGINT NULL,
    draw_rank INT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_activity_reservation_user UNIQUE (activity_id, user_id),
    CONSTRAINT uk_activity_reservation_no UNIQUE (reservation_no),
    CONSTRAINT fk_reservation_activity FOREIGN KEY (activity_id) REFERENCES flash_activity(id),
    CONSTRAINT fk_reservation_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_reservation_lottery_batch FOREIGN KEY (lottery_batch_id) REFERENCES lottery_batch(id)
);

CREATE TABLE IF NOT EXISTS user_favorite (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_favorite UNIQUE (user_id, product_id),
    CONSTRAINT fk_favorite_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_favorite_product FOREIGN KEY (product_id) REFERENCES product(id)
);

CREATE TABLE IF NOT EXISTS trade_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL,
    request_no VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    activity_id BIGINT NULL,
    pickup_point_id BIGINT NOT NULL,
    pickup_point_name VARCHAR(128) NOT NULL,
    pickup_point_address VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'WAIT_PAYMENT',
    total_amount DECIMAL(10,2) NOT NULL,
    payment_deadline TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_trade_order_no UNIQUE (order_no),
    CONSTRAINT uk_trade_order_request_no UNIQUE (request_no),
    CONSTRAINT ck_trade_order_amount CHECK (total_amount >= 0),
    CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_order_activity FOREIGN KEY (activity_id) REFERENCES flash_activity(id),
    CONSTRAINT fk_order_pickup_point FOREIGN KEY (pickup_point_id) REFERENCES pickup_point(id),
    INDEX idx_order_user_status_created (user_id, status, created_at)
);

CREATE TABLE IF NOT EXISTS flash_sale_request (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_no VARCHAR(64) NOT NULL,
    activity_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    sku_id BIGINT NULL,
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
    CONSTRAINT fk_flash_sale_request_sku FOREIGN KEY (sku_id) REFERENCES product_sku(id) ON DELETE SET NULL,
    CONSTRAINT fk_flash_sale_request_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_flash_sale_request_order FOREIGN KEY (order_id) REFERENCES trade_order(id),
    INDEX idx_flash_sale_request_user_created (user_id, created_at),
    INDEX idx_flash_sale_request_status_updated (status, updated_at)
);

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

CREATE TABLE IF NOT EXISTS trade_order_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    sku_id BIGINT NULL,
    product_name VARCHAR(128) NOT NULL,
    sku_code VARCHAR(64) NULL,
    sku_name VARCHAR(128) NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    quantity INT NOT NULL,
    line_amount DECIMAL(10,2) NOT NULL,
    CONSTRAINT ck_order_item_quantity CHECK (quantity > 0),
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES trade_order(id),
    CONSTRAINT fk_order_item_product FOREIGN KEY (product_id) REFERENCES product(id)
    ,CONSTRAINT fk_order_item_sku FOREIGN KEY (sku_id) REFERENCES product_sku(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS product_review (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    rating INT NOT NULL,
    content VARCHAR(1000) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'VISIBLE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_product_review_order_product UNIQUE (order_id, product_id),
    CONSTRAINT ck_product_review_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT fk_product_review_order FOREIGN KEY (order_id) REFERENCES trade_order(id),
    CONSTRAINT fk_product_review_product FOREIGN KEY (product_id) REFERENCES product(id),
    CONSTRAINT fk_product_review_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    INDEX idx_product_review_product_created (product_id, status, created_at)
);

CREATE TABLE IF NOT EXISTS product_review_image (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    review_id BIGINT NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_product_review_image_review FOREIGN KEY (review_id)
        REFERENCES product_review(id) ON DELETE CASCADE,
    INDEX idx_product_review_image_sort (review_id, sort_order)
);

CREATE TABLE IF NOT EXISTS report_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reporter_id BIGINT NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    handled_by BIGINT NULL,
    handle_result VARCHAR(500) NULL,
    handled_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_report_reporter_target UNIQUE (reporter_id, target_type, target_id),
    CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_id) REFERENCES sys_user(id),
    CONSTRAINT fk_report_handler FOREIGN KEY (handled_by) REFERENCES sys_user(id),
    INDEX idx_report_status_created (status, created_at)
);

CREATE TABLE IF NOT EXISTS payment_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    payment_no VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    paid_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_payment_no UNIQUE (payment_no),
    CONSTRAINT uk_payment_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES trade_order(id)
);

CREATE TABLE IF NOT EXISTS pickup_verification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    pickup_code VARCHAR(32) NOT NULL,
    verified_by BIGINT NULL,
    verified_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_pickup_verification_order UNIQUE (order_id),
    CONSTRAINT uk_pickup_verification_code UNIQUE (pickup_code),
    CONSTRAINT fk_pickup_verification_order FOREIGN KEY (order_id) REFERENCES trade_order(id) ON DELETE CASCADE,
    CONSTRAINT fk_pickup_verification_user FOREIGN KEY (verified_by) REFERENCES sys_user(id),
    INDEX idx_pickup_verification_code (pickup_code)
);

CREATE TABLE IF NOT EXISTS refund_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    refund_no VARCHAR(64) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    processed_by BIGINT NULL,
    processed_at TIMESTAMP NULL,
    process_reason VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_refund_order UNIQUE (order_id),
    CONSTRAINT uk_refund_no UNIQUE (refund_no),
    CONSTRAINT fk_refund_order FOREIGN KEY (order_id) REFERENCES trade_order(id) ON DELETE CASCADE,
    CONSTRAINT fk_refund_processor FOREIGN KEY (processed_by) REFERENCES sys_user(id),
    INDEX idx_refund_status_created (status, created_at)
);

CREATE TABLE IF NOT EXISTS notice (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    notice_type VARCHAR(32) NOT NULL,
    business_key VARCHAR(128) NOT NULL,
    title VARCHAR(128) NOT NULL,
    content VARCHAR(500) NOT NULL,
    link VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_notice_business_key UNIQUE (business_key),
    INDEX idx_notice_type_created (notice_type, created_at)
);

CREATE TABLE IF NOT EXISTS user_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    notice_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    read_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_message_notice_user UNIQUE (notice_id, user_id),
    CONSTRAINT fk_user_message_notice FOREIGN KEY (notice_id) REFERENCES notice(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_message_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    INDEX idx_user_message_user_read_created (user_id, read_at, created_at)
);
