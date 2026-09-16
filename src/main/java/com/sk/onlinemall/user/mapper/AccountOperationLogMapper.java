package com.sk.onlinemall.user.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AccountOperationLogMapper {

    /**
     * 写入管理员账号操作日志。
     *
     * @param operatorAccountId 操作管理员账号主键
     * @param targetAccountId 目标账号主键
     * @param action 操作类型
     * @param detail 操作摘要
     * @return 数据库受影响行数
     */
    @Insert("""
            INSERT INTO account_operation_log (operator_account_id, target_account_id, action, detail)
            VALUES (#{operatorAccountId}, #{targetAccountId}, #{action}, #{detail})
            """)
    int insert(@Param("operatorAccountId") Long operatorAccountId,
               @Param("targetAccountId") Long targetAccountId,
               @Param("action") String action,
               @Param("detail") String detail);
}
