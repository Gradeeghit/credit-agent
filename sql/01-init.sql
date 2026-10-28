-- ============================================================
-- credit-agent 数据库初始化脚本 (PostgreSQL 16 + pgvector)
-- 生成: 2026-10-28 by Claude Code | 依据: docs/需求规格说明书.md §7
-- ⚠️ DDL 由 Claude 生成, 必须本人逐行 review 后再用于正式库
-- 用法: psql -h localhost -U postgres -d credit_agent -f sql/01-init.sql
-- ============================================================

SET client_encoding = 'UTF8';

CREATE EXTENSION IF NOT EXISTS vector;

-- ------------------------------------------------------------
-- 1. 客户 (app_user)
-- ------------------------------------------------------------
CREATE TABLE app_user (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(64)  NOT NULL,
    id_card_no  VARCHAR(18)  NOT NULL,
    phone       VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uk_app_user_id_card UNIQUE (id_card_no)
);
COMMENT ON TABLE app_user IS '客户(申请人), 敏感字段按角色脱敏';

-- ------------------------------------------------------------
-- 2. 内部员工 (sys_user)
-- ------------------------------------------------------------
CREATE TABLE sys_user (
    id         BIGSERIAL PRIMARY KEY,
    username   VARCHAR(64) NOT NULL,
    role       VARCHAR(16) NOT NULL,           -- CHECKER / APPROVER
    status     VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT uk_sys_user_username UNIQUE (username),
    CONSTRAINT ck_sys_user_role CHECK (role IN ('CHECKER', 'APPROVER'))
);

-- ------------------------------------------------------------
-- 3. 产品参数 (product) —— 规则引擎算钱的输入
-- ------------------------------------------------------------
CREATE TABLE product (
    id               BIGSERIAL PRIMARY KEY,
    code             VARCHAR(32)   NOT NULL,
    name             VARCHAR(64)   NOT NULL,
    base_rate_ref    VARCHAR(16)   NOT NULL DEFAULT 'LPR',  -- 基准利率类型
    rate_bp          INT           NOT NULL,                -- 利率 = LPR + 加点(bp)
    term_min_months  INT           NOT NULL,
    term_max_months  INT           NOT NULL,
    income_multiple  NUMERIC(6,2)  NOT NULL,                -- 额度倍数
    max_amount       NUMERIC(18,2) NOT NULL,                -- 单笔上限
    fee_rate         NUMERIC(6,4)  NOT NULL,                -- 手续费率
    status           VARCHAR(16)   NOT NULL DEFAULT 'ON',   -- ON / OFF
    created_at       TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT uk_product_code UNIQUE (code),
    CONSTRAINT ck_product_term CHECK (term_min_months > 0 AND term_max_months >= term_min_months),
    CONSTRAINT ck_product_amount CHECK (max_amount > 0 AND income_multiple > 0)
);

-- ------------------------------------------------------------
-- 4. 黑名单 (mock)
-- ------------------------------------------------------------
CREATE TABLE blacklist (
    id             BIGSERIAL PRIMARY KEY,
    id_card_no     VARCHAR(18) NOT NULL,
    reason         VARCHAR(255),
    source         VARCHAR(32),
    effective_from DATE,
    effective_to   DATE,
    CONSTRAINT uk_blacklist_id_card UNIQUE (id_card_no)
);

-- ------------------------------------------------------------
-- 5. 申请单 (loan_application)
-- ------------------------------------------------------------
CREATE TABLE loan_application (
    id            BIGSERIAL PRIMARY KEY,
    app_no        VARCHAR(32)   NOT NULL,          -- 对客申请编号
    customer_id   BIGINT        NOT NULL REFERENCES app_user (id),
    product_id    BIGINT        NOT NULL REFERENCES product (id),
    amount        NUMERIC(18,2) NOT NULL,          -- 申请金额
    term_months   INT           NOT NULL,
    purpose       VARCHAR(32)   NOT NULL,          -- 用途类型
    purpose_desc  VARCHAR(512),
    income        NUMERIC(18,2),                   -- 申报月收入/年流水
    income_type   VARCHAR(8)    NOT NULL DEFAULT 'MONTHLY', -- MONTHLY / YEARLY
    status        VARCHAR(24)   NOT NULL DEFAULT 'SUBMITTED',
    created_by    BIGINT        REFERENCES sys_user (id),
    created_at    TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT uk_application_no UNIQUE (app_no),
    CONSTRAINT ck_application_amount CHECK (amount > 0),
    CONSTRAINT ck_application_status CHECK (status IN
        ('SUBMITTED','PROCESSING','REPORT_READY','APPROVED','REJECTED','NEED_MORE_INFO'))
);
-- 支持"查某状态下的申请"与状态流转
CREATE INDEX idx_application_status ON loan_application (status, created_at);
CREATE INDEX idx_application_customer ON loan_application (customer_id);

-- ------------------------------------------------------------
-- 6. 材料 (document)
-- ------------------------------------------------------------
CREATE TABLE document (
    id             BIGSERIAL PRIMARY KEY,
    application_id BIGINT       NOT NULL REFERENCES loan_application (id),
    doc_type       VARCHAR(32)  NOT NULL,
    file_path      VARCHAR(512) NOT NULL,
    parse_status   VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    parse_error    VARCHAR(512),
    metadata       JSONB,
    created_at     TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT ck_document_type CHECK (doc_type IN
        ('ID_CARD','INCOME_CERT','CREDIT_REPORT','BANK_STATEMENT','LOAN_PURPOSE','POLICY_DOC')),
    CONSTRAINT ck_document_parse CHECK (parse_status IN
        ('PENDING','PARSED','FAILED','UNSUPPORTED'))
);
CREATE INDEX idx_document_application ON document (application_id);
CREATE INDEX idx_document_parse_status ON document (parse_status);

-- ------------------------------------------------------------
-- 7. 文档切片 (document_chunk) —— pgvector
-- ------------------------------------------------------------
CREATE TABLE document_chunk (
    id            BIGSERIAL PRIMARY KEY,
    doc_id        BIGINT       NOT NULL REFERENCES document (id),
    content       TEXT         NOT NULL,
    embedding     vector(1024),               -- 与 EMBEDDING_MODEL 维度一致
    metadata      JSONB        NOT NULL,      -- {docId,pageNo,section,charStart}
    created_at    TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_chunk_doc ON document_chunk (doc_id);
-- 近似向量索引; 数据量小时也可全表精确扫描
CREATE INDEX idx_chunk_embedding ON document_chunk USING hnsw (embedding vector_cosine_ops);

-- ------------------------------------------------------------
-- 8. 审批过程 (approval_trace)
-- ------------------------------------------------------------
CREATE TABLE approval_trace (
    id                    BIGSERIAL PRIMARY KEY,
    trace_id             VARCHAR(64)   NOT NULL,
    application_id       BIGINT        NOT NULL REFERENCES loan_application (id),
    status               VARCHAR(24)   NOT NULL,      -- RUNNING / COMPLETED / FAILED
    conclusion           TEXT,
    confidence           NUMERIC(5,4),
    total_latency_ms     BIGINT,
    model_version        VARCHAR(64),
    prompt_version       VARCHAR(64),
    total_tokens         BIGINT,
    total_cost           NUMERIC(12,6),
    requires_human_review BOOLEAN      NOT NULL DEFAULT true,
    failure_reason       VARCHAR(32),                 -- 见枚举
    created_at           TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT uk_trace_trace_id UNIQUE (trace_id),
    CONSTRAINT ck_trace_status CHECK (status IN ('RUNNING','COMPLETED','FAILED')),
    CONSTRAINT ck_trace_failure CHECK (failure_reason IS NULL OR failure_reason IN
        ('TOOL_TIMEOUT','TOOL_PARAM_INVALID','LLM_TIMEOUT','BUDGET_EXCEEDED','MAX_STEPS'))
);
-- 查失败 trace / 按状态扫描
CREATE INDEX idx_trace_status ON approval_trace (status, created_at);
CREATE INDEX idx_trace_application ON approval_trace (application_id);

-- ------------------------------------------------------------
-- 9. 审批步骤明细 (approval_step)
-- ------------------------------------------------------------
CREATE TABLE approval_step (
    id             BIGSERIAL PRIMARY KEY,
    trace_id       VARCHAR(64) NOT NULL,
    step_index     INT         NOT NULL,
    agent_role     VARCHAR(32) NOT NULL,
    tool_name      VARCHAR(64),
    input_summary  TEXT,
    output_summary TEXT,
    latency_ms     BIGINT,
    created_at     TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT uk_step_trace_index UNIQUE (trace_id, step_index)
);

-- ------------------------------------------------------------
-- 10. LLM 调用成本 (ai_call_log)
-- ------------------------------------------------------------
CREATE TABLE ai_call_log (
    id                BIGSERIAL PRIMARY KEY,
    trace_id          VARCHAR(64),
    model             VARCHAR(64)   NOT NULL,
    prompt_tokens     BIGINT,
    completion_tokens BIGINT,
    cost              NUMERIC(12,6),
    latency_ms        BIGINT,
    created_at        TIMESTAMP     NOT NULL DEFAULT now()
);
CREATE INDEX idx_ai_call_trace ON ai_call_log (trace_id);

-- ------------------------------------------------------------
-- 11. 审计日志 (audit_log)
-- ------------------------------------------------------------
CREATE TABLE audit_log (
    id            BIGSERIAL PRIMARY KEY,
    operator_id   BIGINT,
    operator_name VARCHAR(64),
    action        VARCHAR(64) NOT NULL,
    target_type   VARCHAR(32),
    target_id     VARCHAR(64),
    before_data   JSONB,
    after_data    JSONB,
    created_at    TIMESTAMP   NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_action ON audit_log (action, created_at);

-- ------------------------------------------------------------
-- 12. 复式记账 (account_entry) —— 金融业务演示, 不接入主审批流
-- ------------------------------------------------------------
CREATE TABLE account_entry (
    id           BIGSERIAL PRIMARY KEY,
    entry_no     VARCHAR(32)   NOT NULL,          -- 凭证号
    direction    VARCHAR(8)    NOT NULL,          -- DEBIT / CREDIT
    account_code VARCHAR(16)   NOT NULL,
    account_name VARCHAR(64)   NOT NULL,
    amount       NUMERIC(18,2) NOT NULL,
    biz_type     VARCHAR(32),
    trace_id     VARCHAR(64),
    created_at   TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT ck_entry_direction CHECK (direction IN ('DEBIT','CREDIT')),
    CONSTRAINT ck_entry_amount CHECK (amount > 0)
);
CREATE INDEX idx_entry_no ON account_entry (entry_no);
CREATE INDEX idx_entry_trace ON account_entry (trace_id);

-- ============================================================
-- 模拟产品种子数据 (口径待业务确认, 见 PRD §10)
-- ============================================================
INSERT INTO product (code, name, base_rate_ref, rate_bp, term_min_months, term_max_months,
                     income_multiple, max_amount, fee_rate, status)
VALUES
    ('P1', '薪金贷', 'LPR', 100, 6, 36, 12.00, 300000.00, 0.0050, 'ON'),
    ('P2', '公积金信用贷', 'LPR', 50, 6, 60, 18.00, 500000.00, 0.0030, 'ON'),
    ('P3', '小微经营贷', 'LPR', 250, 12, 60, 24.00, 1000000.00, 0.0100, 'ON');

-- ============================================================
-- 归档提醒: approval_trace / approval_step / ai_call_log 建议 90 天归档
-- (由定时任务实现, 不在本 DDL 范围)
-- ============================================================