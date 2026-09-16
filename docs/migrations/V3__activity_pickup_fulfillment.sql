ALTER TABLE flash_activity
    ADD COLUMN pickup_point_id BIGINT NULL AFTER product_id;

UPDATE flash_activity
SET pickup_point_id = (SELECT id FROM pickup_point WHERE status = 'ACTIVE' ORDER BY id LIMIT 1)
WHERE pickup_point_id IS NULL;

ALTER TABLE flash_activity
    MODIFY COLUMN pickup_point_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_activity_pickup_point
        FOREIGN KEY (pickup_point_id) REFERENCES pickup_point(id);

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

CREATE TABLE IF NOT EXISTS operator_pickup_point (
    operator_id BIGINT NOT NULL,
    pickup_point_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (operator_id, pickup_point_id),
    CONSTRAINT fk_operator_pickup_point_user FOREIGN KEY (operator_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_operator_pickup_point_location FOREIGN KEY (pickup_point_id) REFERENCES pickup_point(id) ON DELETE CASCADE,
    INDEX idx_operator_pickup_point_location (pickup_point_id, operator_id)
);
