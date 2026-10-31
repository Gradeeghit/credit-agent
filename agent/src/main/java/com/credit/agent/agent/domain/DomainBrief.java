package com.credit.agent.agent.domain;

import java.util.List;

/**
 * 单个子 Agent 的输入上下文(分域隔离的载体)。
 * 业务含义: 这是"控幻觉"的物理边界 —— 一个 DomainBrief 只允许装填一个域的证据,
 * 其余三个域在构造时就固定为空列表, 外部**无法**造出"材料 + 征信混在一起"的 brief;
 * 因此子 Agent 不可能引用其他域的内容, 冲突只能由 Supervisor 在汇聚阶段显式提出。
 */
public final class DomainBrief {

    private final AgentRole role;
    private final Long applicationId;
    private final List<DomainEvidence> materials;
    private final List<DomainEvidence> creditReports;
    private final List<DomainEvidence> statements;
    private final List<DomainEvidence> policyChunks;

    private DomainBrief(AgentRole role, Long applicationId, List<DomainEvidence> materials,
                        List<DomainEvidence> creditReports, List<DomainEvidence> statements,
                        List<DomainEvidence> policyChunks) {
        this.role = role;
        this.applicationId = applicationId;
        this.materials = materials;
        this.creditReports = creditReports;
        this.statements = statements;
        this.policyChunks = policyChunks;
    }

    /** 材料核验域: 必须挂在某张申请单下。 */
    public static DomainBrief forMaterial(Long applicationId, List<DomainEvidence> evidence) {
        return new DomainBrief(AgentRole.MATERIAL, requireApplication(applicationId),
                copy(evidence), List.of(), List.of(), List.of());
    }

    /** 征信域: 必须挂在某张申请单下。 */
    public static DomainBrief forCredit(Long applicationId, List<DomainEvidence> evidence) {
        return new DomainBrief(AgentRole.CREDIT, requireApplication(applicationId),
                List.of(), copy(evidence), List.of(), List.of());
    }

    /** 流水域: 必须挂在某张申请单下。 */
    public static DomainBrief forStatement(Long applicationId, List<DomainEvidence> evidence) {
        return new DomainBrief(AgentRole.STATEMENT, requireApplication(applicationId),
                List.of(), List.of(), copy(evidence), List.of());
    }

    /** 制度域: 属于公共语料, 不挂申请单。 */
    public static DomainBrief forPolicy(List<DomainEvidence> chunks) {
        return new DomainBrief(AgentRole.POLICY, null, List.of(), List.of(), List.of(), copy(chunks));
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
}
