package com.credit.agent.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.credit.agent.infra.persistence.entity.AppUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 客户档案 Mapper。
 * 业务含义: app_user 的基础读写, 身份证号唯一性由库约束保证。
 */
@Mapper
public interface AppUserMapper extends BaseMapper<AppUser> {
}
