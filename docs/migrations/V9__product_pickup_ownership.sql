ALTER TABLE product
    ADD COLUMN pickup_point_id BIGINT NULL AFTER created_by;

UPDATE product p
LEFT JOIN (
    SELECT product_id, MIN(pickup_point_id) AS pickup_point_id
    FROM flash_activity
    GROUP BY product_id
) activity_pickup ON activity_pickup.product_id = p.id
SET p.pickup_point_id = COALESCE(
    activity_pickup.pickup_point_id,
    (SELECT fallback.id FROM (SELECT id FROM pickup_point ORDER BY id LIMIT 1) fallback)
)
WHERE p.pickup_point_id IS NULL;

UPDATE product
SET status = 'OFF_SALE', updated_at = CURRENT_TIMESTAMP
WHERE pickup_point_id IS NULL AND status IN ('PENDING_REVIEW', 'ON_SALE');

ALTER TABLE product
    ADD CONSTRAINT fk_product_pickup_point
        FOREIGN KEY (pickup_point_id) REFERENCES pickup_point(id),
    ADD INDEX idx_product_pickup_point (pickup_point_id),
    ADD CONSTRAINT ck_product_publish_pickup
        CHECK (pickup_point_id IS NOT NULL OR status NOT IN ('PENDING_REVIEW', 'ON_SALE'));

UPDATE flash_activity activity
JOIN product p ON p.id = activity.product_id
SET activity.pickup_point_id = p.pickup_point_id
WHERE p.pickup_point_id IS NOT NULL
  AND activity.pickup_point_id <> p.pickup_point_id;
