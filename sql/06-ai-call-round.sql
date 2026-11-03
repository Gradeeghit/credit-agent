-- credit-agent H3 成本可观测: ai_call_log 增加"轮次"维度
-- 依据: docs/需求规格说明书 §9.1 / H3「按 trace_id 聚合 cost = 各条之和」
-- 背景: 带 Function Calling 的一次 ask(s) 在供应商侧是 N 次计费调用(每轮重发完整历史),
--       旧口径只在网关末尾记一条(只能拿到最后一次响应), 会系统性低报单笔审批成本。
--       网关已改为逐轮各记一条, 本脚本补上落库所需列。
-- 性质: 只有 DDL; ⚠️ 必须本人逐行 review 后手动执行
-- 用法: psql -h <host> -U <user> -d <db> -f sql/06-ai-call-round.sql
-- ============================================================

SET client_encoding = 'UTF8';

-- 存量数据统一为 1: 旧口径本来就只记了"最后一轮", 无法回填前面几轮, 不做假数据
ALTER TABLE ai_call_log ADD COLUMN IF NOT EXISTS round_no INT NOT NULL DEFAULT 1;

COMMENT ON COLUMN ai_call_log.round_no IS
    '一次 ask 内的模型调用轮次(从 1 起), 无工具时恒为 1; 一次 ask 可能对应多条, 成本 = Σ各轮';
COMMENT ON TABLE ai_call_log IS 'LLM 调用明细(逐轮); 按 trace_id 聚合即为单笔审批成本';

-- 按 trace 逐轮查看时按时序取数; 已有 idx_ai_call_trace 只按 trace_id, 排序还得额外排序
CREATE INDEX IF NOT EXISTS idx_ai_call_trace_round ON ai_call_log (trace_id, round_no);

-- ============================================================
-- 执行后自查
-- ============================================================
-- 1) 列是否加上
-- SELECT column_name, data_type, column_default FROM information_schema.columns
--  WHERE table_name = 'ai_call_log' AND column_name = 'round_no';
-- 2) 逐轮增量(差值 = 本轮因工具结果多出来的输入 token)
-- SELECT round_no, prompt_tokens, completion_tokens,
--        prompt_tokens - lag(prompt_tokens, 1, 0) OVER (ORDER BY round_no) AS prompt_delta,
--        cost
--   FROM ai_call_log WHERE trace_id = '<trace>' AND kind = 'CHAT' ORDER BY round_no;
-- 3) 真实单笔成本(逐轮求和; 旧口径只算最后一轮会偏小)
-- SELECT trace_id, count(*) AS rounds, sum(prompt_tokens + completion_tokens) AS tokens,
--        sum(cost) AS total_cost
--   FROM ai_call_log WHERE kind = 'CHAT' AND trace_id IS NOT NULL
--  GROUP BY trace_id ORDER BY 4 DESC NULLS LAST LIMIT 10;
