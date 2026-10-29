package com.credit.agent.infra.persistence.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.credit.agent.infra.persistence.entity.Product;
import com.credit.agent.infra.persistence.mapper.ProductMapper;
import com.credit.agent.infra.persistence.service.ProductService;
import org.springframework.stereotype.Service;

/**
 * 产品参数持久化实现。
 * 业务含义: 只提供 product 的增删改查, 金额与费率计算一律在 domain 纯 Java 中完成。
 */
@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {
}
