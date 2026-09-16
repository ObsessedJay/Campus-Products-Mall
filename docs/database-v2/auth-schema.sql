-- V2 认证基线：用于全新数据库，不兼容旧 sys_user 表。
-- 仅覆盖登录、注册和三端身份识别；业务资料表在后续模块按需增加。

CREATE TABLE auth_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(128) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    role VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    last_login_at TIMESTAMP NULL,
    password_changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_auth_account_email UNIQUE (email),
    CONSTRAINT ck_auth_account_role CHECK (role IN ('STUDENT', 'MERCHANT', 'ADMIN')),
    CONSTRAINT ck_auth_account_status CHECK (status IN ('ACTIVE', 'DISABLED', 'LOCKED')),
    INDEX idx_auth_account_role_status (role, status)
);

CREATE TABLE account_operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    operator_account_id BIGINT NOT NULL,
    target_account_id BIGINT NOT NULL,
    action VARCHAR(64) NOT NULL,
    detail VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_account_log_operator FOREIGN KEY (operator_account_id) REFERENCES auth_account(id),
    CONSTRAINT fk_account_log_target FOREIGN KEY (target_account_id) REFERENCES auth_account(id),
    INDEX idx_account_log_target_created (target_account_id, created_at),
    INDEX idx_account_log_operator_created (operator_account_id, created_at)
);

-- 学生自助注册只写 auth_account，角色固定为 STUDENT。
-- MERCHANT 和 ADMIN 账号由管理员创建，不开放公共注册入口。
-- 邮箱验证码、图形验证码、限流计数和刷新令牌继续存 Redis，不重复落 MySQL。
-- confirmPassword 是请求字段，只做一致性校验，禁止持久化。
