package com.credit.agent.infra.persistence.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.credit.agent.infra.persistence.entity.AppUser;
import com.credit.agent.infra.persistence.mapper.AppUserMapper;
import com.credit.agent.infra.persistence.service.AppUserService;
import org.springframework.stereotype.Service;

/**
 * 客户档案持久化实现。
 * 业务含义: 只提供 app_user 的增删改查, 不承载身份校验与脱敏规则。
 */
@Service
public class AppUserServiceImpl extends ServiceImpl<AppUserMapper, AppUser> implements AppUserService {
}
