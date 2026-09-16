-- 商家注册支持持久化一张商家头像或门店标识图。
ALTER TABLE merchant_profile
    ADD COLUMN logo_url VARCHAR(500) NULL AFTER merchant_name;
