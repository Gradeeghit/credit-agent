-- ============================================================
-- credit-agent H3 成本可观测: ai_call_log
-- 依据: docs/需求规格说明书 §9.1 / H3「按 trace_id 聚合 cost = 各条之和」
-- 性质: 只有 DDL; ⚠️ 必须本人逐行 review 后手动执行
-- 用法: psql -h <host> -U <user> -d <db> -f sql/04-ai-call-log.sql
-- ======================================================QQ======

SET client_encoding = 'UTF8';

CREATE TABLE IF NOT EXISTS ai_call_log (
    id                BIGSERIAL PRIMARY KEY,
    trace_id          VARCHAR(64),                  -- 与 approval_trace.trace_id 关联; 允许为空(如离线条目)
    kind              VARCHAR(16)   NOT NULL DEFAULT 'CHAT',  -- CHAT / EMBEDDING: 两类单价不同, 混用会算错账
    model             VARCHAR(64)   NOT NULL,        -- 模型标识, 取不到时写 unknown
    prompt_tokens     BIGINT        NOT NULL DEFAULT 0,
    completion_tokens BIGINT        NOT NULL DEFAULT 0,
    cost              NUMERIC(12,6),                 -- 元, 6 位小数; 单价未配置时为 NULL
    latency_ms        BIGINT        NOT NULL DEFAULT 0,
    created_at        TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT ck_ai_call_tokens CHECK (prompt_tokens >= 0 AND completion_tokens >= 0),
    CONSTRAINT ck_ai_call_kind CHECK (kind IN ('CHAT', 'EMBEDDING', 'RERANK')),
    CONSTRAINT ck_ai_call_cost CHECK (cost IS NULL OR cost >= 0)
);

-- 按 trace 聚合成本(单笔审批成本查询)
CREATE INDEX IF NOT EXISTS idx_ai_call_trace ON ai_call_log (trace_id);
-- 90 天归档扫描
CREATE INDEX IF NOT EXISTS idx_ai_call_created ON ai_call_log (created_at);

COMMENT ON TABLE  ai_call_log IS 'LLM 调用明细; 按 trace_id 聚合即为单笔审批成本';
COMMENT ON COLUMN ai_call_log.cost IS '金额由 domain 的 TokenCost 按单价确定性计算, 不在应用层用 double 乘';

-- ============================================================
-- 执行后自查
-- ============================================================
-- 1) 表结构
-- SELECT column_name, data_type, is_nullable FROM information_schema.columns
--  WHERE table_name = 'ai_call_log' ORDER BY ordinal_position;
-- 2) 按 trace 聚合
-- SELECT trace_id, count(*) AS calls, sum(prompt_tokens + completion_tokens) AS tokens,
--        sum(cost) AS total_cost
--   FROM ai_call_log WHERE trace_id IS NOT NULL GROUP BY trace_id ORDER BY 2 DESC LIMIT 10;

ALTER TABLE ai_call_log ADD COLUMN IF NOT EXISTS kind VARCHAR(16) NOT NULL DEFAULT 'CHAT';
ALTER TABLE ai_call_log DROP CONSTRAINT IF EXISTS ck_ai_call_kind;
ALTER TABLE ai_call_log ADD CONSTRAINT ck_ai_call_kind CHECK (kind IN ('CHAT', 'EMBEDDING', 'RERANK'));
