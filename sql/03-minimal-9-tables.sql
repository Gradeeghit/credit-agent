-- ============================================================
-- credit-agent 最小可跑通建表脚本（9 张表）
-- 生成: 2026-10-28 | 依据: docs/需求规格说明书-codebuddy-deepseek-v4.1flash.md（需求基线 v1.0）
-- ⚠️ 本文件为草稿，必须本人逐行 review 后才可执行
-- 用法: psql -h localhost -U postgres -d credit_agent -f sql/03-minimal-9-tables.sql
-- 前置: 全新空库（本文件不含 DROP，重复执行会报"已存在"）
--
-- 覆盖的闭环: 客户提交 → 信审员受理 → 解析切片 → 规则引擎算钱
--             → 分域 Agent 分析 → 报告 v1 锁定 → 主管签批 → 终态
--
-- 本脚本已固化两项本人决策:
--   ① CONFLICT 是 approval_report.conclusion 的合法取值（跨材料信息冲突）
--   ② 提交幂等键为「客户维度」: UNIQUE(customer_id, client_request_id)
--
-- 建表顺序按外键依赖排列，直接顺序执行即可
-- ============================================================

SET client_encoding = 'UTF8';
CREATE EXTENSION IF NOT EXISTS vector;

-- ------------------------------------------------------------
-- 1. app_user 客户
--    链路位置: 提交申请的主体 / I1 换凭证 / I4 越权判定 / H5 脱敏来源
-- ------------------------------------------------------------
CREATE TABLE app_user (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(64) NOT NULL,
    id_card_no VARCHAR(18) NOT NULL,
    phone      VARCHAR(20) NOT NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT uk_app_user_id_card UNIQUE (id_card_no)
);
COMMENT ON TABLE  app_user IS '客户(申请人); 敏感字段按角色脱敏输出, 不在 SQL 层脱敏';
COMMENT ON COLUMN app_user.phone IS 'I1 取后 4 位做身份校验, 响应中恒脱敏为 138****5678';

-- ------------------------------------------------------------
-- 2. sys_user 内部员工
--    链路位置: I2 登录 / I3 角色注入 / G4 签批人身份 / J1 产品维护权限
-- ------------------------------------------------------------
CREATE TABLE sys_user (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(100),                        -- BCrypt 摘要, 由 I2 落地时收紧为 NOT NULL
    role          VARCHAR(16)  NOT NULL,               -- CHECKER / APPROVER
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    fail_count    INT          NOT NULL DEFAULT 0,     -- 连续登录失败次数
    locked_until  TIMESTAMP,                           -- 非空且晚于 now() = 锁定中(601 秒)
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uk_sys_user_username UNIQUE (username),
    CONSTRAINT ck_sys_user_role CHECK (role IN ('CHECKER', 'APPROVER')),
    CONSTRAINT ck_sys_user_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);
COMMENT ON TABLE  sys_user IS '内部员工账号; 禁止存明文密码';
COMMENT ON COLUMN sys_user.locked_until IS 'I2: 连续错 3 次锁 601 秒, 到期后由登录逻辑清零';

-- ------------------------------------------------------------
-- 3. product 产品参数
--    链路位置: 规则引擎唯一算钱输入(D2 额度 / D4 月供 / D5 手续费)
--    注意: 计算逻辑在 domain 纯 Java, 本表只提供参数
-- ------------------------------------------------------------
CREATE TABLE product (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(32)   NOT NULL,            -- P1 / P2 / P3
    name            VARCHAR(64)   NOT NULL,
    base_rate_ref   VARCHAR(16)   NOT NULL DEFAULT 'LPR',
    rate_bp         INT           NOT NULL,            -- 利率 = LPR + 加点(bp)
    term_min_months INT           NOT NULL,
    term_max_months INT           NOT NULL,
    income_multiple NUMERIC(6,2)  NOT NULL,            -- 额度倍数(月薪贷按月收入, 经营贷按年流水)
    max_amount      NUMERIC(18,2) NOT NULL,            -- 单笔上限
    fee_rate        NUMERIC(6,4)  NOT NULL,            -- 手续费率
    status          VARCHAR(16)   NOT NULL DEFAULT 'ON',
    created_at      TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT uk_product_code UNIQUE (code),
    CONSTRAINT ck_product_term CHECK (term_min_months > 0 AND term_max_months >= term_min_months),
    CONSTRAINT ck_product_amount CHECK (max_amount > 0 AND income_multiple > 0),
    CONSTRAINT ck_product_fee CHECK (fee_rate >= 0 AND fee_rate < 1)
);
COMMENT ON TABLE product IS '模拟产品参数; 口径由本人最终确认(PRD §12-1)';

-- ------------------------------------------------------------
-- 4. loan_application 申请单
--    链路位置: 状态机载体(§3.1 六个状态) / A1 幂等 / G5 客户查状态
-- ------------------------------------------------------------
CREATE TABLE loan_application (
    id                BIGSERIAL PRIMARY KEY,
    app_no            VARCHAR(32)   NOT NULL,          -- 对客申请编号
    customer_id       BIGINT        NOT NULL REFERENCES app_user (id),
    product_id        BIGINT        NOT NULL REFERENCES product (id),
    client_request_id VARCHAR(64),                     -- 客户端幂等键, 客户维度唯一
    amount            NUMERIC(18,2) NOT NULL,
    term_months       INT           NOT NULL,
    purpose           VARCHAR(32)   NOT NULL,          -- 用途类型
    purpose_desc      VARCHAR(512),                    -- 用途说明, 敏感词闸门输入
    income            NUMERIC(18,2),                   -- 月收入 或 年流水
    income_type       VARCHAR(8)    NOT NULL DEFAULT 'MONTHLY',  -- MONTHLY / YEARLY
    status            VARCHAR(24)   NOT NULL DEFAULT 'SUBMITTED',
    created_by        BIGINT        REFERENCES sys_user (id),    -- 受理人; 客户自提交时为 NULL
    created_at        TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT uk_application_no UNIQUE (app_no),
    CONSTRAINT ck_application_amount CHECK (amount > 0),
    CONSTRAINT ck_application_term CHECK (term_months > 0),
    CONSTRAINT ck_application_income CHECK (income IS NULL OR income > 0),
    CONSTRAINT ck_application_income_type CHECK (income_type IN ('MONTHLY', 'YEARLY')),
    CONSTRAINT ck_application_status CHECK (status IN
        ('SUBMITTED', 'PROCESSING', 'REPORT_READY', 'APPROVED', 'REJECTED', 'NEED_MORE_INFO'))
);

-- 幂等: 同一客户重复提交只落 1 条(PRD A1); PG 唯一约束允许多个 NULL, 不影响历史数据
ALTER TABLE loan_application
    ADD CONSTRAINT uk_application_client_req UNIQUE (customer_id, client_request_id);

-- 查积压/查失败、查我的申请
CREATE INDEX idx_application_status   ON loan_application (status, created_at);
CREATE INDEX idx_application_customer ON loan_application (customer_id, created_at);

COMMENT ON TABLE  loan_application IS '申请单; status 迁移以 PRD §3.1 为唯一权威, 终态不可迁移';
COMMENT ON COLUMN loan_application.client_request_id IS '客户维度幂等键: UNIQUE(customer_id, client_request_id)';

-- ------------------------------------------------------------
-- 5. document 材料 / 政策文档
--    链路位置: A2 上传 → A3 解析状态机(§3.4) → A4 重传 → B0 制度入库
--    业务材料与制度文档共用一表, 靠 doc_type 区分
-- ------------------------------------------------------------
CREATE TABLE document (
    id             BIGSERIAL PRIMARY KEY,
    application_id BIGINT       REFERENCES loan_application (id),  -- POLICY_DOC 时为 NULL
    doc_type       VARCHAR(32)  NOT NULL,
    file_path      VARCHAR(512) NOT NULL,
    version        INT          NOT NULL DEFAULT 1,                -- 重传次数
    is_current     BOOLEAN      NOT NULL DEFAULT true,             -- false=已被重传版本替代
    parse_status   VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    parse_error    VARCHAR(512),
    metadata       JSONB,
    created_at     TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT ck_document_type CHECK (doc_type IN
        ('ID_CARD', 'INCOME_CERT', 'CREDIT_REPORT', 'BANK_STATEMENT', 'LOAN_PURPOSE', 'POLICY_DOC')),
    CONSTRAINT ck_document_parse CHECK (parse_status IN
        ('PENDING', 'PARSED', 'FAILED', 'UNSUPPORTED')),
    -- 业务材料必须挂在申请单下; 制度文档不属于任何申请单
    CONSTRAINT ck_document_application CHECK (
           (doc_type =  'POLICY_DOC' AND application_id IS NULL)
        OR (doc_type <> 'POLICY_DOC' AND application_id IS NOT NULL)
    ),
    CONSTRAINT ck_document_version CHECK (version >= 1)
);

CREATE INDEX idx_document_application ON document (application_id, doc_type, is_current);
CREATE INDEX idx_document_parse_status ON document (parse_status);

COMMENT ON TABLE  document IS '材料/政策文档; 重传产生新记录(version+1)并置旧记录 is_current=false';
COMMENT ON COLUMN document.parse_status IS 'PENDING/PARSED/FAILED/UNSUPPORTED, UNSUPPORTED=影像件无文本层';

-- ------------------------------------------------------------
-- 6. document_chunk 切片
--    链路位置: B1–B3 切分 → B5 向量化 → C1 BM25 / C2 向量 / C3 RRF → C6 引用回链
--    注意: 报告里的每条 [文件名 p.12-13] 都必须能回到本表某一行
-- ------------------------------------------------------------
CREATE TABLE document_chunk (
    id         BIGSERIAL PRIMARY KEY,
    doc_id     BIGINT       NOT NULL REFERENCES document (id),
    content    TEXT         NOT NULL,
    embedding  vector(1024),                       -- 维度待 PRD §12-7 定稿
    page_start INT,
    page_end   INT,
    is_current BOOLEAN      NOT NULL DEFAULT true, -- 材料作废后置 false, 检索只命中 true
    metadata   JSONB        NOT NULL,              -- {docId, section, charStart}
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT ck_chunk_page CHECK (
        page_start IS NULL OR page_end IS NULL OR page_end >= page_start
    )
);

-- 检索过滤: 只召回当前有效材料的切片
CREATE INDEX idx_chunk_doc_current ON document_chunk (doc_id, is_current);
-- 向量召回; PRD §9.3 要求部分索引(WHERE is_current), 与检索 SQL 的过滤条件对齐:
-- 索引里不塞已作废切片, 且查询带 is_current = true 时才能命中该索引
CREATE INDEX idx_chunk_embedding ON document_chunk
       USING hnsw (embedding vector_cosine_ops) WHERE is_current;

COMMENT ON TABLE  document_chunk IS '文档切片; embedding 列在 B5 解锁前保持 NULL, 不影响建表';
COMMENT ON COLUMN document_chunk.page_start IS '起始页码; 跨页时 page_end != page_start, 引用渲染为 p.12-13';

-- ------------------------------------------------------------
-- 7. approval_trace 一次审批
--    链路位置: H1 五段追溯的根 / H3 成本汇总 / F2 终止条件 / §3.2 人工复核标记
-- ------------------------------------------------------------
CREATE TABLE approval_trace (
    id                    BIGSERIAL PRIMARY KEY,
    trace_id              VARCHAR(64) NOT NULL,
    application_id        BIGINT      NOT NULL REFERENCES loan_application (id),
    status                VARCHAR(24) NOT NULL,      -- RUNNING / COMPLETED / FAILED
    conclusion            VARCHAR(32),               -- 与 approval_report 同枚举, 冗余汇总
    confidence            NUMERIC(5,4),
    total_latency_ms      BIGINT,
    model_version         VARCHAR(64),
    prompt_version        VARCHAR(64),
    total_tokens          BIGINT,
    total_cost            NUMERIC(12,6),
    requires_human_review BOOLEAN     NOT NULL DEFAULT true,
    failure_reason        VARCHAR(32),
    created_at            TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT uk_trace_trace_id UNIQUE (trace_id),
    CONSTRAINT ck_trace_status CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_trace_conclusion CHECK (conclusion IS NULL OR conclusion IN
        ('PASS_SUGGEST', 'REJECT_SUGGEST', 'NEED_MORE_INFO', 'CONFLICT')),
    -- PRD §3.5 全 7 个枚举值
    CONSTRAINT ck_trace_failure CHECK (failure_reason IS NULL OR failure_reason IN
        ('TOOL_TIMEOUT', 'TOOL_PARAM_INVALID', 'LLM_TIMEOUT', 'BUDGET_EXCEEDED',
         'MAX_STEPS', 'TOOL_NOT_FOUND', 'RETRIEVAL_DEGRADED'))
);

CREATE INDEX idx_trace_status      ON approval_trace (status, created_at);
CREATE INDEX idx_trace_application ON approval_trace (application_id);

COMMENT ON TABLE  approval_trace IS '一次审批的汇总记录; 按 trace_id 纵向串联 HTTP→Agent→LLM→Tool→DB';
COMMENT ON COLUMN approval_trace.requires_human_review IS '§3.2: true=存在实质分歧, 签批意见必填且禁止批量通过';

-- ------------------------------------------------------------
-- 8. approval_step 步骤明细
--    链路位置: F5 每步落库 / F2 步数断言 / H2 回放 / E5 错误入账
-- ------------------------------------------------------------
CREATE TABLE approval_step (
    id             BIGSERIAL PRIMARY KEY,
    trace_id       VARCHAR(64) NOT NULL,
    step_index     INT         NOT NULL,
    agent_role     VARCHAR(32) NOT NULL,   -- MATERIAL / CREDIT / STATEMENT / POLICY / SUPERVISOR
    tool_name      VARCHAR(64),
    input_summary  TEXT,
    output_summary TEXT,
    latency_ms     BIGINT,
    failure_reason VARCHAR(32),
    created_at     TIMESTAMP   NOT NULL DEFAULT now(),
    -- F5 验收点: 重复 (trace_id, step_index) 必须抛唯一约束异常
    CONSTRAINT uk_step_trace_index UNIQUE (trace_id, step_index),
    CONSTRAINT ck_step_index CHECK (step_index >= 1),
    CONSTRAINT ck_step_failure CHECK (failure_reason IS NULL OR failure_reason IN
        ('TOOL_TIMEOUT', 'TOOL_PARAM_INVALID', 'LLM_TIMEOUT', 'BUDGET_EXCEEDED',
         'MAX_STEPS', 'TOOL_NOT_FOUND', 'RETRIEVAL_DEGRADED'))
);

COMMENT ON TABLE approval_step IS '每一步执行明细; 只追加不修改, 支撑 Trace 回放';
COMMENT ON COLUMN approval_step.failure_reason IS 'E3 工具超时等错误在此留痕, 与 approval_trace 汇总互不替代';

-- ------------------------------------------------------------
-- 9. approval_report 初审报告
--    链路位置: G1 六节结构 / G3 锁定 / G4 签批版本校验 / G6 报告查看
-- ------------------------------------------------------------
CREATE TABLE approval_report (
    id                    BIGSERIAL PRIMARY KEY,
    application_id        BIGINT      NOT NULL REFERENCES loan_application (id),
    trace_id              VARCHAR(64) NOT NULL,
    version               INT         NOT NULL,
    conclusion            VARCHAR(32) NOT NULL,
    content_json          JSONB       NOT NULL,   -- 结论/引用/风险点/规则引擎结果/置信度/待补清单
    requires_human_review BOOLEAN     NOT NULL DEFAULT true,
    locked                BOOLEAN     NOT NULL DEFAULT true,
    created_at            TIMESTAMP   NOT NULL DEFAULT now(),
    -- 并发与重跑的唯一防线: 签批时按 (application_id, version) 定位, 不匹配返回 409
    CONSTRAINT uk_report_app_version UNIQUE (application_id, version),
    CONSTRAINT ck_report_version CHECK (version >= 1),
    -- 本人决策: CONFLICT 是结论取值(跨材料信息冲突), 不是仅存在于正文里的一句话
    CONSTRAINT ck_report_conclusion CHECK (conclusion IN
        ('PASS_SUGGEST', 'REJECT_SUGGEST', 'NEED_MORE_INFO', 'CONFLICT'))
);

-- 查"待人工复核"清单
CREATE INDEX idx_report_pending_review ON approval_report (requires_human_review, created_at);

COMMENT ON TABLE  approval_report IS '初审报告; 首次 version=1, NEED_MORE_INFO 重跑生成 N+1, 旧版本只读保留';
COMMENT ON COLUMN approval_report.locked IS 'true=已锁定, 任何修改请求返回 409';
COMMENT ON COLUMN approval_report.content_json IS '六节结构(G1); 规则引擎算出的金额也存于此, 不另建表';

-- ============================================================
-- 种子数据: 3 个模拟产品(口径待 PRD §12-1 确认)
-- ============================================================
INSERT INTO product (code, name, base_rate_ref, rate_bp, term_min_months, term_max_months,
                     income_multiple, max_amount, fee_rate, status)
VALUES
    ('P1', '薪金贷',       'LPR', 100,  6, 36, 12.00,  300000.00, 0.0050, 'ON'),
    ('P2', '公积金信用贷', 'LPR',  50,  6, 60, 18.00,  500000.00, 0.0030, 'ON'),
    ('P3', '小微经营贷',   'LPR', 250, 12, 60, 24.00, 1000000.00, 0.0100, 'ON');

-- ============================================================
-- 自查（执行后手工核对）
-- ============================================================
-- 1) 表数量: 9
--    SELECT count(*) FROM information_schema.tables
--     WHERE table_schema='public' AND table_type='BASE TABLE';
-- 2) 幂等键为复合唯一
--    SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname='uk_application_client_req';
--    -- 预期: UNIQUE (customer_id, client_request_id)
-- 3) 报告结论含 CONFLICT
--    SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname='ck_report_conclusion';
-- 4) 步骤唯一约束(F5 验收点)
--    SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname='uk_step_trace_index';
-- 5) 产品种子: 3
--    SELECT code, rate_bp, income_multiple, max_amount FROM product ORDER BY code;
