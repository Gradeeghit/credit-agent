# credit-agent

> 信贷申请的**材料核验 + 制度检索 + 初审报告生成**助手。算钱的部分是确定性代码，AI 只负责"读懂、找依据、讲理由"，最终由人签批。

Java 17 · Spring Boot 3.5 · Spring AI 1.1 · MyBatis-Plus · PostgreSQL 16 + pgvector · 8 个 Maven 模块 · **273 个单测全绿（0 跳过）**

---

## 一、业务边界

| ✅ 做什么 | ❌ 明确不做什么 |
|---|---|
| 材料完整性核验 | **不做最终放贷决策**（只出初审意见，人签批） |
| 额度/利率**测算**（规则引擎，非 AI） | 不自动调额、不放款 |
| 监管政策与内部制度**检索引用** | 不对客户做承诺 |
| 风险提示与**拒贷理由生成** | 不做真实征信查询（用模拟数据） |
| 低置信度**转人工复核** | 不做影像 OCR（只处理文本层 PDF） |

---

## 二、快速启动（约 5 分钟）

**前置**：JDK 17、Docker、Maven 3.9.x；也可以用仓库自带的 `mvnw`（Windows 用 `mvnw.cmd`，它会下载自己的 Maven 版本）。

### 1. 起数据库

```bash
docker compose up -d
```

`docker-compose.yml` 把 `./sql` 挂到 `/docker-entrypoint-initdb.d`，**首次启动会按文件名顺序自动执行** `03`（9 张业务表 + 产品种子）→ `04`（`ai_call_log`）→ `05`（演示数据）→ `06`（`ai_call_log.round_no`）→ `07`（`audit_log`，签批审计）。

> 卷已存在时 initdb 不会重跑，需手动执行：
>
> ```bash
> for f in sql/0*.sql; do psql -h localhost -U postgres -d credit_agent -f "$f"; done
> ```

### 2. 配环境变量

`application.yml` 里的默认值指向作者本机环境，**必须覆盖**：

| 变量 | 说明 |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/credit_agent` |
| `OPENAI_API_KEY` | chat 与 embedding 共用（OpenAI 兼容端点） |
| `OPENAI_BASE_URL` | 如 `https://dashscope.aliyuncs.com/compatible-mode`（**不能带 `/v1`**） |
| `OPENAI_MODEL` | chat 模型名 |
| `EMBEDDING_MODEL` | 维度必须是 1024，与 `vector(1024)` 对齐 |
| `RERANK_ENABLED` | 是否对检索结果重排，默认 `true` |

```bash
export DB_URL=jdbc:postgresql://localhost:5432/credit_agent
export OPENAI_API_KEY=sk-xxx
export OPENAI_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode
export OPENAI_MODEL=qwen-plus
export EMBEDDING_MODEL=text-embedding-v4
```

Windows PowerShell 用 `$env:DB_URL="..."`，一次性设置；要持久化用 `setx`。

### 3. 启动服务（端口 8081）

```bash
./mvnw spring-boot:run -pl api
```

### 4. 跑一遍主链路

```bash
# 上传一份制度 PDF（必须是文本层，扫描件会 parse_status=UNSUPPORTED）
# 自备一份法规/制度 PDF；若你本地有 docs/samples/，可直接用其中的法规原文
curl -F "file=@个人贷款管理办法.pdf" http://localhost:8081/api/documents/policy

# 触发一次初审分析（身份目前由请求头直传，见「已知限制」）
curl -X POST http://localhost:8081/api/applications/1/analyze \
     -H "X-User-Id: 1" -H "X-User-Role: CHECKER"
```

其余接口（客户/申请单/产品/检索/报告查询）见仓库根目录 `test.http`。

> **没有可用模型 Key 也能跑**：`EMBEDDING_ENABLED=false` 时切片只落库不向量化，检索退化为纯 BM25；模型调用失败会走降级分支，报告标注 `AI_DEGRADED` 而不是整单失败。

---

## 三、检索评测（可复现）

自建标注集，两套语料、同一套指标口径（`RecallEvaluator`：命中窗口、名次取首条 gold、未命中记 0）：

**第一套：虚构语料 30 段 / 65 条**（31 字面 + 34 口语改写）

| 档位 | Recall@3 | MRR | 改写档 |
|---|---|---|---|
| 纯向量 | **0.9692** | 0.9538 | 0.9412 |
| RRF 融合 | 0.9538 | 0.9231 | 0.9118 |
| BM25 | 0.9231 | 0.8872 | 0.8529 |

**第二套：3 份真实监管办法 170 片 / 92 条**（46 字面 + 46 改写）

| 档位 | Recall@3 | MRR | 字面档 | 改写档 |
|---|---|---|---|---|
| 纯向量 | 0.8913 | 0.7572 | 0.9348 | 0.8478 |
| RRF 融合 | 0.8261 | 0.7101 | 0.9783 | 0.6739 |
| BM25 | 0.7283 | 0.6322 | 0.9783 | 0.4348 |
| **+ 专用重排** | **0.9674** | **0.8460** | 0.9783 | **0.9565** |

> 两套语料的结论一致：**融合不如纯向量**（会同时继承两路的错误），因此主链保持纯向量；重排在真实语料上 +8.54%。注意提升幅度受**卷子上限**约束——第一套基线 63/65，理论上限只有 3.17%，同一个实现换到第二套（上限 12.2%）才有 8.54%。详见 `docs/检索评测说明.md`。

复现：

```bash
# 纯 BM25 基线：离线，不需要任何 Key
./mvnw test -pl rag -Dtest=C5Bm25BaselineTest

# 三档对比 / 真实语料 / 重排：会真实调用 embedding 与 rerank
./mvnw test -pl rag -Dtest=C5RealCorpusTest -Dsurefire.excludedGroups=noop
./mvnw test -pl rag -Dtest=C5RerankTest -Dsurefire.excludedGroups=noop
```

`@Tag("live")` 用例默认被 surefire 排除，所以 `mvn test` 永远是全绿 0 跳过；上面 `-Dsurefire.excludedGroups=noop` 是放行开关。

---

## 四、模块结构

| 模块 | 职责 |
|---|---|
| `domain` | 业务模型与规则计算（**零依赖**，不含任何框架与 AI） |
| `infra` | 持久化、`AiCallLogger` 留痕、MDC |
| `rag` | 解析、切分、向量/BM25/融合/重排、评测 |
| `tool` | 模型可调用的工具（`queryProduct` / `searchPolicy`）与统一契约 `ToolResponse` |
| `agent` | `LlmRequest`/网关、四域 Agent、状态机 |
| `orchestration` | 编排运行时、trace、报告、签批 |
| `api` | Controller、异常处理、Filter |
| `config` | 装配与横切配置 |

依赖方向单向：`api → orchestration → agent → tool/rag → infra`，`domain` 被所有人依赖但自己不依赖任何人。

---

## 五、几个关键取舍

| 取舍 | 原因 | 代码位置 |
|---|---|---|
| **金额不用 AI** | 概率模型同一输入可能给不同数字，且无法复算；金额必须确定、可审计、可单测 | `domain/loan/`（额度 / 等额本息 / 手续费 / 超限判定，零依赖） |
| **分域 Agent + 构造期隔离** | 幻觉的根因是"看到了不该看的证据"，靠 prompt 约束不够，`DomainBrief` 用私有构造按域生成 | `agent/domain/DomainBrief` |
| **砍掉混合检索、保留重排** | 两轮评测都显示融合低于纯向量；重排在真实语料上 +8.54% | `docs/检索评测说明.md`、`rag/retrieval/` |
| **工具循环自驱动 + 逐轮记账** | 框架默认一次 `call()` 跑完 N 轮、只返回最后一轮 token，成本系统性低估 | `agent/llm/SpringAiLlmGateway` |
| **不用 `ChatClient` 的 Advisor/结构化输出** | 不用 chat memory（隔离靠构造期）、流水域是"正文 + 尾附 JSON"混合输出 | 同上；取舍见对应类的 Javadoc |

---

## 六、已知限制

- **身份是请求头直传**（`X-User-Id` / `X-User-Role`），未接 Spring Security，可伪造；仅开发态可用。
- **签批只支持单笔**：G4 已闭环（通过 / 驳回 / 要求补材料、版本 409、幂等、乐观锁），但无批量签批，也不能对需人工复核的报告"一键通过"（强制写 ≥10 字意见）。
- **审计只覆盖签批一类**：`audit_log` 建表脚本已备（`sql/07`）；报告查看、越权尝试两类留痕未接。
- **字段级权限（H5）与黑名单闸门（H7）未做**：`blacklist` 表未建，敏感词清单未定。
- **重排只接在检索接口上**（`/api/rag/search` 的 `mode`/`rerank` 参数），制度域主链（`PolicyTools` → `VectorRetriever`）仍走纯向量。
- 评测语料偏小（65 / 92 条），且真实语料是**按条切分**，与线上"按章节 + 800 字滑窗"不一致。
- 无性能压测、无 SLA 数据；无 Testcontainers，数据库约束类断言靠手工验证。

---

## 七、许可

[Apache License 2.0](LICENSE) © 2026 credit-agent contributors

第三方依赖各自遵循其原始许可（Spring、MyBatis-Plus、PDFBox、jieba 等）。若仓库包含 `docs/samples/` 目录，其中的监管文件为公开法规原文，仅供检索演示，不随本许可授权。
