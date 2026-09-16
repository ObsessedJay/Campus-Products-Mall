package com.sk.onlinemall.system.mapper;

import com.sk.onlinemall.system.model.SystemConfigItem;
import com.sk.onlinemall.system.model.SystemConfigLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SystemConfigMapper {
    /**
     * 查询全部系统配置。
     *
     * @return 配置列表
     */
    @Select("SELECT config_key, config_value, value_type, description, updated_by, updated_at FROM system_config ORDER BY config_key")
    List<SystemConfigItem> findAll();

    /**
     * 按键查询系统配置。
     *
     * @param key 配置键
     * @return 配置项
     */
    @Select("SELECT config_key, config_value, value_type, description, updated_by, updated_at FROM system_config WHERE config_key = #{key}")
    SystemConfigItem findByKey(String key);

    /**
     * 按旧值条件更新配置。
     *
     * @param key 配置键
     * @param oldValue 原配置值
     * @param newValue 新配置值
     * @param operatorId 操作人主键
     * @return 受影响行数
     */
    @Update("UPDATE system_config SET config_value = #{newValue}, updated_by = #{operatorId}, updated_at = CURRENT_TIMESTAMP WHERE config_key = #{key} AND config_value = #{oldValue}")
    int update(@Param("key") String key, @Param("oldValue") String oldValue,
               @Param("newValue") String newValue, @Param("operatorId") Long operatorId);

    /**
     * 写入配置变更日志。
     *
     * @param key 配置键
     * @param oldValue 原配置值
     * @param newValue 新配置值
     * @param reason 变更原因
     * @param operatorId 操作人主键
     * @return 受影响行数
     */
    @Insert("INSERT INTO system_config_log (config_key, previous_value, updated_value, reason, operated_by) VALUES (#{key}, #{oldValue}, #{newValue}, #{reason}, #{operatorId})")
    int insertLog(@Param("key") String key, @Param("oldValue") String oldValue,
                  @Param("newValue") String newValue, @Param("reason") String reason,
                  @Param("operatorId") Long operatorId);

    /**
     * 查询配置变更记录。
     *
     * @param key 可选配置键
     * @return 变更记录
     */
    @Select("<script>SELECT id, config_key, previous_value, updated_value, reason, operated_by, created_at FROM system_config_log <if test='key != null'>WHERE config_key = #{key}</if> ORDER BY id DESC LIMIT 200</script>")
    List<SystemConfigLog> findLogs(@Param("key") String key);
}
