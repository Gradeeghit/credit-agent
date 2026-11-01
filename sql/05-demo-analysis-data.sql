-- ============================================================
-- credit-agent 演示数据: 供 POST /api/applications/{id}/analyze 直接出报告
-- 前置: 已执行 sql/03-minimal-9-tables.sql（含 product P1/P2/P3 种子）
-- 性质: 只有 INSERT, 无 DDL; ⚠️ 仍需本人 review 后手动执行
-- 用法: psql -h <host> -U <user> -d <db> -f sql/05-demo-analysis-data.sql
--
-- 产出两个申请单:
--   场景 A「材料一致」→ 报告结论期望 PASS_SUGGEST
--   场景 B「收入证明 3 万 / 流水月均 2 万」→ 报告结论期望 CONFLICT
-- 注意: 分析链路会真实调用 chat 模型, 需配置 OPENAI_API_KEY / OPENAI_BASE_URL;
--       不需要 embedding Key（切片已有文本, 未走向量检索）。
-- ============================================================

SET client_encoding = 'UTF8';

DO $$
DECLARE
    v_p1       BIGINT;
    v_checker  BIGINT;
    v_approver BIGINT;
    v_cust_a   BIGINT;
    v_cust_b   BIGINT;
    v_app_a    BIGINT;
    v_app_b    BIGINT;
    v_doc      BIGINT;
BEGIN
    SELECT id INTO v_p1 FROM product WHERE code = 'P1';
    IF v_p1 IS NULL THEN
        RAISE EXCEPTION 'product P1 不存在, 请先执行 sql/03-minimal-9-tables.sql';
    END IF;

    -- ---------- 内部员工（供 X-User-Id / X-User-Role 使用） ----------
    INSERT INTO sys_user (username, role) VALUES ('demo_checker', 'CHECKER')
        ON CONFLICT (username) DO UPDATE SET role = EXCLUDED.role RETURNING id INTO v_checker;
    INSERT INTO sys_user (username, role) VALUES ('demo_approver', 'APPROVER')
        ON CONFLICT (username) DO UPDATE SET role = EXCLUDED.role RETURNING id INTO v_approver;

    -- ============================================================
    -- 场景 A：材料自洽（收入证明 3 万 / 流水月均约 3.03 万）
    -- ============================================================
    INSERT INTO app_user (name, id_card_no, phone)
    VALUES ('张伟', '110101199001011234', '13800005678')
        ON CONFLICT (id_card_no) DO UPDATE SET phone = EXCLUDED.phone RETURNING id INTO v_cust_a;

    INSERT INTO loan_application (app_no, customer_id, product_id, amount, term_months,
                                  purpose, purpose_desc, income, income_type, status, created_by)
    VALUES ('APP20261101001', v_cust_a, v_p1, 200000.00, 12,
            '装修', '住房装修, 合同金额 18 万', 30000.00, 'MONTHLY', 'SUBMITTED', v_checker)
        ON CONFLICT (app_no) DO UPDATE SET status = 'SUBMITTED' RETURNING id INTO v_app_a;

    IF NOT EXISTS (SELECT 1 FROM document WHERE application_id = v_app_a) THEN
        -- 材料域
        INSERT INTO document (application_id, doc_type, file_path, metadata)
        VALUES (v_app_a, 'ID_CARD', 'demo/a-id-card.pdf', '{"docName":"身份证.pdf"}'::jsonb)
            RETURNING id INTO v_doc;
        INSERT INTO document_chunk (doc_id, content, page_start, page_end, metadata) VALUES
            (v_doc, '姓名：张伟；身份证号：110101199001011234；签发机关：北京市公安局；有效期至 2035-06-30。', 1, 1, '{}'::jsonb);

        INSERT INTO document (application_id, doc_type, file_path, metadata)
        VALUES (v_app_a, 'INCOME_CERT', 'demo/a-in2come.pdf', '{"docName":"收入证明.pdf"}'::jsonb)
            RETURNING id INTO v_doc;
        INSERT INTO document_chunk (doc_id, content, page_start, page_end, metadata) VALUES
            (v_doc, '单位：某某科技有限公司；员工张伟，岗位：高级工程师；入职日期 2020-03-01；月收入（税前）30000.00 元。本证明仅用于贷款申请。', 1, 1, '{}'::jsonb);

        INSERT INTO document (application_id, doc_type, file_path, metadata)
        VALUES (v_app_a, 'LOAN_PURPOSE', 'demo/a-purpose.pdf', '{"docName":"用途说明.pdf"}'::jsonb)
            RETURNING id INTO v_doc;
        INSERT INTO document_chunk (doc_id, content, page_start, page_end, metadata) VALUES
            (v_doc, '贷款用途：住房装修；装修房屋地址：北京市朝阳区某小区；装修合同总价 180000.00 元；申请贷款 200000.00 元。', 1, 1, '{}'::jsonb);

        -- 征信域
        INSERT INTO document (application_id, doc_type, file_path, metadata)
        VALUES (v_app_a, 'CREDIT_REPORT', 'demo/a-credit.pdf', '{"docName":"征信报告.pdf"}'::jsonb)
            RETURNING id INTO v_doc;
        INSERT INTO document_chunk (doc_id, content, page_start, page_end, metadata) VALUES
            (v_doc, '近 24 个月还款记录：无逾期；当前信用卡使用率 25%；近 6 个月贷款审批查询 3 次；无呆账、无担保代偿记录。', 2, 3, '{}'::jsonb);

        -- 流水域（月均约 30333，与 30000 偏差约 1.1%）
        INSERT INTO document (application_id, doc_type, file_path, metadata)
        VALUES (v_app_a, 'BANK_STATEMENT', 'demo/a-statement.pdf', '{"docName":"银行流水.pdf"}'::jsonb)
            RETURNING id INTO v_doc;
        INSERT INTO document_chunk (doc_id, content, page_start, page_end, metadata) VALUES
            (v_doc, '账户流水明细（工资代发）：2026-08-05 入账 31000.00 元；2026-09-05 入账 29500.00 元；2026-10-05 入账 30500.00 元。期末余额 86000.00 元。', 4, 5, '{}'::jsonb);
    END IF;

    -- ============================================================
    -- 场景 B：收入证明 3 万 / 流水月均 2 万（偏差 33.33% > 30% 阈值 → CONFLICT）
    -- ============================================================
    INSERT INTO app_user (name, id_card_no, phone)
    VALUES ('李强', '310101198803045678', '13900005678')
        ON CONFLICT (id_card_no) DO UPDATE SET phone = EXCLUDED.phone RETURNING id INTO v_cust_b;

    INSERT INTO loan_application (app_no, customer_id, product_id, amount, term_months,
                                  purpose, purpose_desc, income, income_type, status, created_by)
    VALUES ('APP20261101002', v_cust_b, v_p1, 250000.00, 24,
            '装修', '旧房翻新', 30000.00, 'MONTHLY', 'SUBMITTED', v_checker)
        ON CONFLICT (app_no) DO UPDATE SET status = 'SUBMITTED' RETURNING id INTO v_app_b;

    IF NOT EXISTS (SELECT 1 FROM document WHERE application_id = v_app_b) THEN
        INSERT INTO document (application_id, doc_type, file_path, metadata)
        VALUES (v_app_b, 'ID_CARD', 'demo/b-id-card.pdf', '{"docName":"身份证.pdf"}'::jsonb)
            RETURNING id INTO v_doc;
        INSERT INTO document_chunk (doc_id, content, page_start, page_end, metadata) VALUES
            (v_doc, '姓名：李强；身份证号：310101198803045678；有效期至 2032-11-30。', 1, 1, '{}'::jsonb);

        INSERT INTO document (application_id, doc_type, file_path, metadata)
        VALUES (v_app_b, 'INCOME_CERT', 'demo/b-income.pdf', '{"docName":"收入证明.pdf"}'::jsonb)
            RETURNING id INTO v_doc;
        INSERT INTO document_chunk (doc_id, content, page_start, page_end, metadata) VALUES
            (v_doc, '单位：某某贸易有限公司；员工李强，岗位：销售经理；入职日期 2021-07-01；月收入（税前）30000.00 元。', 1, 1, '{}'::jsonb);

        INSERT INTO document (application_id, doc_type, file_path, metadata)
        VALUES (v_app_b, 'LOAN_PURPOSE', 'demo/b-purpose.pdf', '{"docName":"用途说明.pdf"}'::jsonb)
            RETURNING id INTO v_doc;
        INSERT INTO document_chunk (doc_id, content, page_start, page_end, metadata) VALUES
            (v_doc, '贷款用途：旧房翻新装修；装修预算 260000.00 元；申请贷款 250000.00 元。', 1, 1, '{}'::jsonb);

        INSERT INTO document (application_id, doc_type, file_path, metadata)
        VALUES (v_app_b, 'CREDIT_REPORT', 'demo/b-credit.pdf', '{"docName":"征信报告.pdf"}'::jsonb)
            RETURNING id INTO v_doc;
        INSERT INTO document_chunk (doc_id, content, page_start, page_end, metadata) VALUES
            (v_doc, '近 12 个月存在 2 次逾期（最长逾期 15 天, 均已结清）；当前信用卡使用率 78%；近 3 个月贷款审批查询 6 次。', 2, 2, '{}'::jsonb);

        -- 关键: 流水只有 2 万的入账, 与收入证明的 3 万矛盾
        INSERT INTO document (application_id, doc_type, file_path, metadata)
        VALUES (v_app_b, 'BANK_STATEMENT', 'demo/b-statement.pdf', '{"docName":"银行流水.pdf"}'::jsonb)
            RETURNING id INTO v_doc;
        INSERT INTO document_chunk (doc_id, content, page_start, page_end, metadata) VALUES
            (v_doc, '账户流水明细：2026-08-08 入账 20000.00 元；2026-09-08 入账 20000.00 元；2026-10-08 入账 20000.00 元。无其他经营性入账, 期末余额 12000.00 元。', 3, 4, '{}'::jsonb);
    END IF;

    RAISE NOTICE '演示数据就绪: 场景A applicationId=%, 场景B applicationId=%', v_app_a, v_app_b;
    RAISE NOTICE '调用示例: curl -X POST "http://localhost:8080/api/applications/%/analyze" -H "X-User-Id: %" -H "X-User-Role: CHECKER"', v_app_a, v_checker;
    RAISE NOTICE 'checkerUserId=% approverUserId=%', v_checker, v_approver;
END $$;

-- ============================================================
-- 执行后自查
-- ============================================================
-- 1) 两张申请单及其切片数（A/B 各 5 类材料, 每类 1 片）
-- SELECT a.app_no, d.doc_type, count(c.id) AS chunks
--   FROM loan_application a JOIN document d ON d.application_id = a.id
--   JOIN document_chunk c ON c.doc_id = d.id AND c.is_current
--  WHERE a.app_no LIKE 'APP2026110100%' GROUP BY a.app_no, d.doc_type ORDER BY 1, 2;
--
-- 2) 分析后查报告
-- SELECT application_id, version, conclusion, requires_human_review, locked, created_at
--   FROM approval_report ORDER BY id DESC LIMIT 5;
--
-- 3) 看六节内容（含冲突摘要与引用 chunkId）
-- SELECT content_json -> 'conclusion'      AS conclusion,
--        content_json -> 'risks'           AS risks,
--        content_json -> 'ruleEngine'      AS rule_engine,
--        content_json -> 'confidence'      AS confidence,
--        content_json -> 'pendingItems'    AS pending_items,
--        jsonb_array_length(content_json -> 'citations') AS citation_count
--   FROM approval_report ORDER BY id DESC LIMIT 1;
--
-- ============================================================
-- 衍生玩法（不用改脚本, 直接 SQL 微调后再分析）
-- ============================================================
-- ① 触发 D3 超限降额: 场景A 收入 2 万 → 可批额度 24 万, 申请 30 万
--    UPDATE loan_application SET income = 20000.00, amount = 300000.00 WHERE app_no = 'APP20261101001';
-- ② 触发 NEED_MORE_INFO: 删掉场景A 的征信切片
--    DELETE FROM document_chunk WHERE doc_id IN (
--      SELECT d.id FROM document d JOIN loan_application a ON a.id = d.application_id
--       WHERE a.app_no = 'APP20261101001' AND d.doc_type = 'CREDIT_REPORT');
-- ③ 触发降级报告(MAX_STEPS/BUDGET_EXCEEDED): 调小预算启动
--    java -jar ... --credit-agent.agent.token-budget=50
