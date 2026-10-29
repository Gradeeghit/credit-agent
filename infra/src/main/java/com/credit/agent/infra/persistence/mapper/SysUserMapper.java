package com.credit.agent.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.credit.agent.infra.persistence.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 员工账号 Mapper。
 * 业务含义: sys_user 的基础读写; 登录失败计数与锁定时间由登录逻辑用本 Mapper 更新。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
