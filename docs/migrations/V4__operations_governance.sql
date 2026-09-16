ALTER TABLE sys_user ADD COLUMN credit_score INT NOT NULL DEFAULT 100;
ALTER TABLE sys_user ADD CONSTRAINT ck_sys_user_credit CHECK (credit_score BETWEEN 0 AND 100);

CREATE TABLE user_credit_log (
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

CREATE TABLE system_config (
    config_key VARCHAR(64) PRIMARY KEY,
    config_value VARCHAR(255) NOT NULL,
    value_type VARCHAR(16) NOT NULL,
    description VARCHAR(255) NOT NULL,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_system_config_operator FOREIGN KEY (updated_by) REFERENCES sys_user(id)
);

CREATE TABLE system_config_log (
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

CREATE TABLE storage_object (
    object_name VARCHAR(128) PRIMARY KEY,
    content_type VARCHAR(64) NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_storage_object_creator FOREIGN KEY (created_by) REFERENCES sys_user(id),
    INDEX idx_storage_object_created (created_at)
);

INSERT INTO system_config (config_key, config_value, value_type, description) VALUES
('PAYMENT_TIMEOUT_MINUTES', '15', 'INTEGER', '非活动订单支付超时分钟数'),
('DEFAULT_PURCHASE_LIMIT', '1', 'INTEGER', '商品默认单人限购数量'),
('RESERVATION_LEAD_MINUTES', '30', 'INTEGER', '预约最小提前分钟数'),
('WRITE_RATE_LIMIT', '120', 'INTEGER', '用户写请求每窗口阈值'),
('FLASH_SALE_RATE_LIMIT', '10', 'INTEGER', '抢购请求每窗口阈值'),
('LOGIN_FAILURE_LIMIT', '5', 'INTEGER', '登录失败封禁阈值');
