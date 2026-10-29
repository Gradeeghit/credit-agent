package com.credit.agent.infra.persistence.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.credit.agent.infra.persistence.entity.SysUser;

/**
 * 员工账号持久化服务。
 * 业务含义: sys_user 的基础读写; 登录锁定策略(错 3 次锁 601 秒)由登录逻辑驱动本服务落库。
 */
public interface SysUserService extends IService<SysUser> {
}
