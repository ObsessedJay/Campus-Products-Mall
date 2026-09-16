-- 商品图片使用安全对象名存储，同时保留商家展示名称和原始文件名用于追溯。
ALTER TABLE product_image
    ADD COLUMN display_name VARCHAR(100) NULL AFTER object_name,
    ADD COLUMN original_name VARCHAR(255) NULL AFTER display_name;
