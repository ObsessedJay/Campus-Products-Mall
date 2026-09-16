-- 账号资料边界兼容迁移：保留 sys_user 作为现有业务外键根表。
-- 执行前请确认 V1-V4 已完成；重复执行 CREATE TABLE 不会破坏已有数据。

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

-- 为历史学生账号建立资料行；默认自提点从 sys_user 快照迁移。
INSERT INTO student_profile (account_id, default_pickup_point_id)
SELECT u.id, u.default_pickup_point_id
FROM sys_user u
WHERE u.role = 'STUDENT'
  AND NOT EXISTS (SELECT 1 FROM student_profile p WHERE p.account_id = u.id);

-- 管理员账号必须由部署人员使用组织密码策略生成 BCrypt 哈希后插入：
-- INSERT INTO sys_user (username, email, password_hash, nickname, role, status)
-- VALUES ('admin@example.edu', 'admin@example.edu', '<BCRYPT_HASH>', '平台管理员', 'ADMIN', 'ACTIVE');
