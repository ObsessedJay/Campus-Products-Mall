-- 仅用于早期九表开发库升级到当前 schema；全新数据库不要执行本文件。
-- 执行前应备份非空业务库，并按 information_schema 核对缺失列。

ALTER TABLE sys_user
    ADD COLUMN email VARCHAR(128) NULL AFTER username,
    ADD COLUMN default_pickup_point_id BIGINT NULL AFTER student_no,
    ADD COLUMN credit_score INT NOT NULL DEFAULT 100 AFTER status,
    ADD CONSTRAINT uk_sys_user_email UNIQUE (email),
    ADD CONSTRAINT ck_sys_user_credit CHECK (credit_score BETWEEN 0 AND 100),
    ADD CONSTRAINT fk_user_default_pickup_point
        FOREIGN KEY (default_pickup_point_id) REFERENCES pickup_point(id);

ALTER TABLE product
    ADD COLUMN created_by BIGINT NULL AFTER category_id;

ALTER TABLE flash_activity
    ADD COLUMN pickup_point_id BIGINT NULL AFTER product_id,
    ADD COLUMN created_by BIGINT NULL AFTER pickup_point_id,
    ADD COLUMN review_status VARCHAR(32) DEFAULT 'PENDING' AFTER status;

UPDATE flash_activity
SET pickup_point_id = (SELECT id FROM pickup_point WHERE status = 'ACTIVE' ORDER BY id LIMIT 1)
WHERE pickup_point_id IS NULL;

ALTER TABLE flash_activity
    MODIFY COLUMN pickup_point_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_activity_pickup_point
        FOREIGN KEY (pickup_point_id) REFERENCES pickup_point(id);

ALTER TABLE activity_reservation
    ADD COLUMN lottery_batch_id BIGINT NULL AFTER status,
    ADD COLUMN draw_rank INT NULL AFTER lottery_batch_id,
    ADD CONSTRAINT fk_reservation_lottery_batch
        FOREIGN KEY (lottery_batch_id) REFERENCES lottery_batch(id);

ALTER TABLE trade_order
    ADD COLUMN pickup_point_id BIGINT NULL AFTER activity_id,
    ADD COLUMN pickup_point_name VARCHAR(128) NULL AFTER pickup_point_id,
    ADD COLUMN pickup_point_address VARCHAR(255) NULL AFTER pickup_point_name;

UPDATE trade_order o
LEFT JOIN flash_activity a ON a.id = o.activity_id
LEFT JOIN sys_user u ON u.id = o.user_id
JOIN pickup_point p ON p.id = COALESCE(a.pickup_point_id, u.default_pickup_point_id,
    (SELECT id FROM pickup_point WHERE status = 'ACTIVE' ORDER BY id LIMIT 1))
SET o.pickup_point_id = p.id,
    o.pickup_point_name = p.name,
    o.pickup_point_address = p.address
WHERE o.pickup_point_id IS NULL;

ALTER TABLE trade_order
    MODIFY COLUMN pickup_point_id BIGINT NOT NULL,
    MODIFY COLUMN pickup_point_name VARCHAR(128) NOT NULL,
    MODIFY COLUMN pickup_point_address VARCHAR(255) NOT NULL,
    ADD CONSTRAINT fk_order_pickup_point
        FOREIGN KEY (pickup_point_id) REFERENCES pickup_point(id);

ALTER TABLE trade_order_item
    ADD COLUMN sku_id BIGINT NULL AFTER product_id,
    ADD COLUMN sku_code VARCHAR(64) NULL AFTER product_name,
    ADD COLUMN sku_name VARCHAR(128) NULL AFTER sku_code,
    ADD CONSTRAINT fk_order_item_sku
        FOREIGN KEY (sku_id) REFERENCES product_sku(id) ON DELETE SET NULL;
