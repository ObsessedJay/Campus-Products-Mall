-- 三端账号按邮箱和角色独立，同一邮箱可分别注册学生、商家和管理员账号。
ALTER TABLE sys_user
    DROP INDEX uk_sys_user_email,
    ADD CONSTRAINT uk_sys_user_email_role UNIQUE (email, role);
