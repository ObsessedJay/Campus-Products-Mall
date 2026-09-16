package com.sk.onlinemall.user.mapper;

import com.sk.onlinemall.user.model.UserEntity;
import com.sk.onlinemall.user.model.UserRole;
import com.sk.onlinemall.user.model.UserStatus;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper {

    /**
     * 按用户名查询用户。
     *
     * @param username 当前用户名
     * @return 查询结果
     */
    @Select("""
            SELECT id, username, email, password_hash, nickname, school, student_no,
                   role, status, credit_score, created_at, updated_at
            FROM sys_user WHERE username = #{username}
            """)
    UserEntity findByUsername(String username);

    /**
     * 按邮箱地址和角色查询用户。
     *
     * @param email 邮箱地址
     * @param role 账号角色
     * @return 查询结果
     */
    @Select("SELECT id, username, email, password_hash, nickname, school, student_no, role, status, credit_score, created_at, updated_at FROM sys_user WHERE email = #{email} AND role = #{role}")
    UserEntity findByEmailAndRole(@Param("email") String email, @Param("role") UserRole role);

    /**
     * 按主键查询记录。
     *
     * @param id 记录主键
     * @return 查询结果
     */
    @Select("SELECT id, username, email, password_hash, nickname, school, student_no, role, status, credit_score, created_at, updated_at FROM sys_user WHERE id = #{id}")
    UserEntity findById(Long id);

    /**
     * 新增用户账号。
     *
     * @param user 用户信息
     * @return 数据库受影响行数
     */
    @Insert("""
            INSERT INTO sys_user (username, email, password_hash, nickname, school, student_no, role, status)
            VALUES (#{username}, #{email}, #{passwordHash}, #{nickname}, #{school}, #{studentNo}, #{role}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(UserEntity user);

    /**
     * 更新个人资料。
     *
     * @param id 记录主键
     * @param nickname 用户昵称
     * @return 数据库受影响行数
     */
    @Update("""
            UPDATE sys_user
            SET nickname = #{nickname}, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateProfile(@Param("id") Long id, @Param("nickname") String nickname);

    /**
     * 更新用户密码哈希。
     *
     * @param id 用户标识
     * @param passwordHash 新密码哈希
     * @return 数据库受影响行数
     */
    @Update("UPDATE sys_user SET password_hash = #{passwordHash}, updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int updatePassword(@Param("id") Long id, @Param("passwordHash") String passwordHash);

    /**
     * 分页搜索用户账号。
     *
     * @param keyword 可选用户名、邮箱或昵称关键词
     * @param status 可选账号状态
     * @param offset 偏移量
     * @param limit 每页数量
     * @return 用户列表
     */
    @Select("""
            <script>
            SELECT id, username, email, password_hash, nickname, school, student_no,
                   role, status, credit_score, created_at, updated_at
            FROM sys_user
            WHERE 1 = 1
            <if test='keyword != null'>AND (LOWER(username) LIKE LOWER(CONCAT('%', #{keyword}, '%')) OR LOWER(email) LIKE LOWER(CONCAT('%', #{keyword}, '%')) OR LOWER(nickname) LIKE LOWER(CONCAT('%', #{keyword}, '%')))</if>
            <if test='status != null'>AND status = #{status}</if>
            ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    java.util.List<UserEntity> search(@Param("keyword") String keyword, @Param("status") String status,
                                      @Param("offset") int offset, @Param("limit") int limit);

    /**
     * 统计符合条件的用户数。
     *
     * @param keyword 可选关键词
     * @param status 可选账号状态
     * @return 用户数量
     */
    @Select("""
            <script>SELECT COUNT(*) FROM sys_user WHERE 1 = 1
            <if test='keyword != null'>AND (LOWER(username) LIKE LOWER(CONCAT('%', #{keyword}, '%')) OR LOWER(email) LIKE LOWER(CONCAT('%', #{keyword}, '%')) OR LOWER(nickname) LIKE LOWER(CONCAT('%', #{keyword}, '%')))</if>
            <if test='status != null'>AND status = #{status}</if></script>
            """)
    long countSearch(@Param("keyword") String keyword, @Param("status") String status);

    /**
     * 按预期状态更新账号状态。
     *
     * @param id 用户主键
     * @param expected 预期状态
     * @param target 目标状态
     * @return 受影响行数
     */
    @Update("UPDATE sys_user SET status = #{target}, updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND status = #{expected}")
    int updateStatus(@Param("id") Long id, @Param("expected") UserStatus expected,
                     @Param("target") UserStatus target);

    /**
     * 按原分值条件更新信用分。
     *
     * @param id 用户主键
     * @param previous 原信用分
     * @param adjusted 新信用分
     * @return 受影响行数
     */
    @Update("UPDATE sys_user SET credit_score = #{adjusted}, updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND credit_score = #{previous}")
    int updateCredit(@Param("id") Long id, @Param("previous") int previous,
                     @Param("adjusted") int adjusted);
}
