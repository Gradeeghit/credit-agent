package com.credit.agent.infra.persistence.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.credit.agent.infra.persistence.entity.Product;

/**
 * 产品参数持久化服务。
 * 业务含义: 为规则引擎提供产品参数; 只做增删改查, 不做额度/利率/手续费计算。
 */
public interface ProductService extends IService<Product> {
}
