package com.credit.agent.orchestration.application.observability;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.credit.agent.infra.observability.ToolCallRecord;
import com.credit.agent.infra.persistence.entity.ApprovalStep;
import com.credit.agent.infra.persistence.service.ApprovalStepService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** 工具留痕落库: 回填到同一步, 缺步号时一条都不写。 */
class DbToolCallRecorderTest {

    private final ApprovalStepService stepService = mock(ApprovalStepService.class);
    private final DbToolCallRecorder recorder = new DbToolCallRecorder(stepService);

    @Test
    @DisplayName("有 traceId 与步号: 更新该步的 tool_name 与 failure_reason")
    @SuppressWarnings("unchecked")
    void writesToolColumns() {
        recorder.record(new ToolCallRecord("trace-1", 2, "searchPolicy",
                "{\"keyword\":\"逾期\"}", "{\"status\":\"OK\"}", 12, null));

        ArgumentCaptor<Wrapper<ApprovalStep>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(stepService).update(captor.capture());
        String sqlSet = ((UpdateWrapper<ApprovalStep>) captor.getValue()).getSqlSet();
        assertTrue(sqlSet.contains("tool_name"), "必须写 tool_name: " + sqlSet);
        assertTrue(sqlSet.contains("failure_reason"));
    }

    @Test
    @DisplayName("不在具体步骤内(无步号)时不写库, 避免误改别的步骤")
    void skipsWithoutStepIndex() {
        recorder.record(new ToolCallRecord("trace-1", null, "searchPolicy", "in", "out", 1, null));

        verify(stepService, never()).update(any());
    }
}
