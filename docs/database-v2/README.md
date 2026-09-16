# V2 Database Design: Authentication First

旧 `schema.sql` 视为 V1 遗留结构。V2 从三端账号边界重新开始设计，不在 `sys_user` 上继续累加学生资料、商家资料和认证字段。

## 当前阶段

本阶段建立 [`auth-schema.sql`](auth-schema.sql) 的 `auth_account`、账号操作审计，以及 [`profile-schema.sql`](profile-schema.sql) 的学生/商家资料边界。一张账号表完成三端登录和权限识别，资料表只保存端侧业务字段：

| 角色 | 注册方式 | 登录后入口 | 数据边界 |
| --- | --- | --- | --- |
| `STUDENT` | 邮箱验证码自助注册 | 学生端 | 后续关联 `student_profile` |
| `MERCHANT` | 管理员创建 | 商家端 | 后续关联 `merchant_profile` |
| `ADMIN` | 初始化或管理员创建 | 管理端 | 无空壳资料表 |

`auth_account` 只保存认证必需信息：规范化邮箱、BCrypt 密码哈希、显示名称、角色、状态和必要时间戳。`student_profile` 只保存默认自提点，`merchant_profile` 保存店铺和联系人信息，`merchant_pickup_point` 保存商家可履约点位。验证码、登录限流和刷新令牌具有时效性，继续保存在 Redis，避免新增低价值表。

## 密码规则

- 注册请求必须提交 `password` 和 `confirmPassword`，两者完全一致后才能消费邮箱验证码。
- 数据库只保存 `password_hash`，不保存明文密码和确认密码。
- 学生不能通过请求体指定角色，公共注册固定创建 `STUDENT`。
- 商家和管理员不开放公共注册，避免越权创建高权限账号。

管理员通过 `POST /api/v1/admin/accounts/merchants` 创建商家账号。请求必须提交 `email`、`password`、`confirmPassword`、`displayName` 和 `merchantName`；账号、商家资料和 `CREATE_MERCHANT_ACCOUNT` 审计日志在同一事务中写入。

## 后续表

商品、活动和订单表继续按业务阶段增加。资料表以 `account_id` 一对一关联 `auth_account`，避免认证表继续膨胀。

当前运行中的旧代码仍使用 `sys_user` 和兼容角色 `OPERATOR`；本阶段新增账号使用 `MERCHANT`，权限链路同时接受两者。切换到 V2 新库前，需要单独完成一次账号/资料迁移，并将业务表外键从 `sys_user` 指向 `auth_account`。

该脚本面向全新数据库。切换生产环境前应另行编写一次性数据迁移和回滚方案，不应直接在现有库执行删除操作。
