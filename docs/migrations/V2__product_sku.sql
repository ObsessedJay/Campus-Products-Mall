CREATE TABLE product_sku (
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

ALTER TABLE flash_sale_request
    ADD COLUMN sku_id BIGINT NULL AFTER product_id,
    ADD CONSTRAINT fk_flash_sale_request_sku FOREIGN KEY (sku_id) REFERENCES product_sku(id) ON DELETE SET NULL;

ALTER TABLE trade_order_item
    ADD COLUMN sku_id BIGINT NULL AFTER product_id,
    ADD COLUMN sku_code VARCHAR(64) NULL AFTER product_name,
    ADD COLUMN sku_name VARCHAR(128) NULL AFTER sku_code,
    ADD CONSTRAINT fk_order_item_sku FOREIGN KEY (sku_id) REFERENCES product_sku(id) ON DELETE SET NULL;
