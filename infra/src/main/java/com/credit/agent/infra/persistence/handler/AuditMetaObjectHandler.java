package com.credit.agent.infra.persistence.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审计字段自动填充处理器。
 * 业务含义: 申请单的 created_at / updated_at 必须由服务端统一写入,
 * 不允许客户端传值, 也不允许依赖数据库 now() 造成应用与库时间不一致。
 */
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    /**
     * 插入时填充创建时间与更新时间; 已显式赋值的字段不覆盖。
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
    }

    /**
     * 更新时刷新更新时间(状态迁移也走这条路), 创建时间保持不动。
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
