package com.credit.agent.infra.persistence.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.credit.agent.infra.persistence.entity.SysUser;
import com.credit.agent.infra.persistence.mapper.SysUserMapper;
import com.credit.agent.infra.persistence.service.SysUserService;
import org.springframework.stereotype.Service;

/**
 * 员工账号持久化实现。
 * 业务含义: 只提供 sys_user 的增删改查; 登录锁定策略由登录逻辑调用本服务完成落库。
 */
@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {
}
