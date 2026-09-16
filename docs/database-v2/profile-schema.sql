-- V2 三端资料结构：先执行 auth-schema.sql，再执行本脚本。
-- pickup_point 由履约模块创建，因此部署时需保证该表已存在。

CREATE TABLE student_profile (
    account_id BIGINT PRIMARY KEY,
    default_pickup_point_id BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_student_profile_account FOREIGN KEY (account_id) REFERENCES auth_account(id) ON DELETE CASCADE,
    CONSTRAINT fk_student_profile_pickup_point FOREIGN KEY (default_pickup_point_id) REFERENCES pickup_point(id)
);

CREATE TABLE merchant_profile (
    account_id BIGINT PRIMARY KEY,
    merchant_name VARCHAR(128) NOT NULL,
    contact_name VARCHAR(64) NULL,
    contact_phone VARCHAR(32) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_merchant_profile_account FOREIGN KEY (account_id) REFERENCES auth_account(id) ON DELETE CASCADE,
    INDEX idx_merchant_profile_name (merchant_name)
);

CREATE TABLE merchant_pickup_point (
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

-- 管理员不建空资料表；管理员特有行为写入 account_operation_log。
-- 学校固定为业务常量，不重复保存；昵称使用 auth_account.display_name。
