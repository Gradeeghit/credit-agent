package com.credit.agent.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.credit.agent.infra.persistence.entity.Document;
import org.apache.ibatis.annotations.Mapper;

/**
 * 材料/政策文档 Mapper。
 * 业务含义: 支撑上传、解析状态机、重传换版本; 查"当前有效材料"必须带 is_current = true。
 */
@Mapper
public interface DocumentMapper extends BaseMapper<Document> {
}
