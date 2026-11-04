-- ============================================================
-- 07 audit_log 审计日志(H6 / G4)
-- 执行顺序: 03-minimal-9-tables.sql → 04-ai-call-log.sql → 06-ai-call-round.sql → 本文件
-- 【待本人逐行 review 后再执行】
--
-- 为什么单独建表: 签批是"人做的决定", 它的 who/when/what 不该混进 AI 链路的
-- approval_trace/approval_step 里; 审计要能在 AI 全挂掉时照样留痕, 所以不依赖任何 AI 表。
-- 注意: data.sql 里 approval_step.agent_role 没有 CHECK 约束, 签批写 APPROVER 无需 ALTER。
-- ============================================================

CREATE TABLE audit_log (
    id            BIGSERIAL   PRIMARY KEY,
    trace_id      VARCHAR(64) NOT NULL,
    operator_id   BIGINT,
    operator_role VARCHAR(16) NOT NULL,
    action        VARCHAR(32) NOT NULL,
    target_type   VARCHAR(24) NOT NULL,
    target_id     BIGINT,
    before_json   TEXT,
    after_json    TEXT,
    detail        TEXT,
    outcome       VARCHAR(16) NOT NULL DEFAULT 'SUCCESS',
    created_at    TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT ck_audit_action CHECK (action IN
        ('APPROVE', 'REJECT', 'NEED_MORE_INFO', 'VIEW_REPORT', 'ACCESS_DENIED', 'PRODUCT_UPDATE')),
    CONSTRAINT ck_audit_target CHECK (target_type IN ('APPLICATION', 'REPORT', 'PRODUCT')),
    CONSTRAINT ck_audit_outcome CHECK (outcome IN ('SUCCESS', 'DENIED', 'FAILED'))
);

-- 按申请单追一张单子的完整操作史(最高频查询)
CREATE INDEX idx_audit_target   ON audit_log (target_type, target_id, created_at);
-- 按人追责 / 按 trace 回放
CREATE INDEX idx_audit_operator ON audit_log (operator_id, created_at);
CREATE INDEX idx_audit_trace    ON audit_log (trace_id);

COMMENT ON TABLE  audit_log IS 'H6 审计日志: 签批/报告查看/越权尝试三类操作必留痕, 只追加不修改';
COMMENT ON COLUMN audit_log.before_json IS '操作前关键状态快照(status/version 文本), 用于还原当时的判断依据';
COMMENT ON COLUMN audit_log.detail IS '操作说明或签批意见原文, 供审计复核';
