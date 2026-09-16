package com.sk.onlinemall.user.mapper;

import com.sk.onlinemall.user.model.MerchantProfileEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MerchantProfileMapper {

    /**
     * 新增商家资料。
     *
     * @param profile 商家资料
     * @return 数据库受影响行数
     */
    @Insert("""
            INSERT INTO merchant_profile (account_id, merchant_name, logo_url, contact_name, contact_phone)
            VALUES (#{accountId}, #{merchantName}, #{logoUrl}, #{contactName}, #{contactPhone})
            """)
    int insert(MerchantProfileEntity profile);

    /**
     * 按账号主键查询商家资料。
     *
     * @param accountId 账号主键
     * @return 商家资料
     */
    @Select("""
            SELECT account_id, merchant_name, logo_url, contact_name, contact_phone, created_at, updated_at
            FROM merchant_profile WHERE account_id = #{accountId}
            """)
    MerchantProfileEntity findByAccountId(Long accountId);
}
