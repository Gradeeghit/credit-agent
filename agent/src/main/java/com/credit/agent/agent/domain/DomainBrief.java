package com.credit.agent.agent.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 单个子 Agent 的输入上下文(分域隔离的载体)。
 * 业务含义: 这是"控幻觉"的物理边界 —— 一个 DomainBrief 只允许装填一个域的证据,
 * 其余三个域在构造时就固定为空列表, 外部**无法**造出"材料 + 征信混在一起"的 brief。
 * facts 携带的是**库里的确定性数值**(如客户申报收入), 由域 Agent 原样带回, 不由模型生成。
 */
public final class DomainBrief {

    private final AgentRole role;
    private final Long applicationId;
    private final List<DomainEvidence> materials;
    private final List<DomainEvidence> creditReports;
    private final List<DomainEvidence> statements;
    private final List<DomainEvidence> policyChunks;
    private final Map<String, BigDecimal> facts;

    private DomainBrief(AgentRole role, Long applicationId, List<DomainEvidence> materials,
                        List<DomainEvidence> creditReports, List<DomainEvidence> statements,
                        List<DomainEvidence> policyChunks, Map<String, BigDecimal> facts) {
        this.role = role;
        this.applicationId = applicationId;
        this.materials = materials;
        this.creditReports = creditReports;
        this.statements = statements;
        this.policyChunks = policyChunks;
        this.facts = facts;
    }

    /** 材料核验域: 必须挂在某张申请单下。 */
    public static DomainBrief forMaterial(Long applicationId, List<DomainEvidence> evidence) {
        return forMaterial(applicationId, evidence, Map.of());
    }

    /** 材料核验域(带确定性事实, 如客户申报月收入)。 */
    public static DomainBrief forMaterial(Long applicationId, List<DomainEvidence> evidence,
                                          Map<String, BigDecimal> facts) {
        return new DomainBrief(AgentRole.MATERIAL, requireApplication(applicationId),
                copy(evidence), List.of(), List.of(), List.of(), copyFacts(facts));
    }

    /** 征信域: 必须挂在某张申请单下。 */
    public static DomainBrief forCredit(Long applicationId, List<DomainEvidence> evidence) {
        return new DomainBrief(AgentRole.CREDIT, requireApplication(applicationId),
                List.of(), copy(evidence), List.of(), List.of(), Map.of());
    }

    /** 流水域: 必须挂在某张申请单下。 */
    public static DomainBrief forStatement(Long applicationId, List<DomainEvidence> evidence) {
        return forStatement(applicationId, evidence, Map.of());
    }

    /** 流水域(带确定性事实)。 */
    public static DomainBrief forStatement(Long applicationId, List<DomainEvidence> evidence,
                                           Map<String, BigDecimal> facts) {
        return new DomainBrief(AgentRole.STATEMENT, requireApplication(applicationId),
                List.of(), List.of(), copy(evidence), List.of(), copyFacts(facts));
    }

    /** 制度域: 属于公共语料, 不挂申请单。 */
    public static DomainBrief forPolicy(List<DomainEvidence> chunks) {
        return new DomainBrief(AgentRole.POLICY, null, List.of(), List.of(), List.of(), copy(chunks), Map.of());
    }

    public AgentRole role() {
        return role;
    }

    public Long applicationId() {
        return applicationId;
    }

    public List<DomainEvidence> materials() {
        return materials;
    }

    public List<DomainEvidence> creditReports() {
        return creditReports;
    }

    public List<DomainEvidence> statements() {
        return statements;
    }

    public List<DomainEvidence> policyChunks() {
        return policyChunks;
    }

    /** 本域已知的确定性数值; 域 Agent 必须原样带回, 不允许模型改写。 */
    public Map<String, BigDecimal> facts() {
        return facts;
    }

    /** 取某项确定性数值, 没有则返回 null(不补 0, 避免伪造事实)。 */
    public BigDecimal fact(String key) {
        return facts.get(key);
    }

    /** 本域证据; 其他域恒为空是设计保证, 不是运行时判断。 */
    public List<DomainEvidence> evidence() {
        return switch (role) {
            case MATERIAL -> materials;
            case CREDIT -> creditReports;
            case STATEMENT -> statements;
            case POLICY -> policyChunks;
            case SUPERVISOR -> List.of();
        };
    }

    /** 本域证据条数, 供降载与预算评估。 */
    public int evidenceCount() {
        return evidence().size();
    }

    private static Long requireApplication(Long applicationId) {
        if (applicationId == null) {
            throw new IllegalArgumentException("材料/征信/流水域必须挂在申请单下");
        }
        return applicationId;
    }

    private static List<DomainEvidence> copy(List<DomainEvidence> evidence) {
        return evidence == null ? List.of() : List.copyOf(evidence);
    }

    private static Map<String, BigDecimal> copyFacts(Map<String, BigDecimal> facts) {
        return facts == null ? Map.of() : Map.copyOf(facts);
    }
}
