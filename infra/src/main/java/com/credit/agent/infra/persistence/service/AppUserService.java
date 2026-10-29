package com.credit.agent.infra.persistence.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.credit.agent.infra.persistence.entity.AppUser;

/**
 * 客户档案持久化服务。
 * 业务含义: app_user 的基础读写; 身份校验(I1)与脱敏(H5)规则不在此处实现。
 */
public interface AppUserService extends IService<AppUser> {
}
