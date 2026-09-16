-- 仅供 H2 集成测试使用，不会加载到开发或生产 MySQL。
MERGE INTO pickup_point (id, name, campus, address, status)
KEY (id)
VALUES (1, '东区图书馆服务台', '东校区', '图书馆一层东侧服务台', 'ACTIVE');

-- 旧测试夹具可省略新增列；业务接口测试仍需显式提交领取点。
ALTER TABLE product ALTER COLUMN pickup_point_id SET DEFAULT 1;
