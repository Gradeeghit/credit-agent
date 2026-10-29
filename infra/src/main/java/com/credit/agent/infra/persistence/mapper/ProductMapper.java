package com.credit.agent.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.credit.agent.infra.persistence.entity.Product;
import org.apache.ibatis.annotations.Mapper;

/**
 * 产品参数 Mapper。
 * 业务含义: 提供规则引擎算钱所需的只读参数; 本 Mapper 不做任何计算。
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}
