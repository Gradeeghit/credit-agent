package com.credit.agent.tool.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 统一 Tool 契约单测: 三态语义与 isOk 判定。 */
class ToolResponseTest {

    @Test
    @DisplayName("正常返回: status=OK, 无错误码")
    void okHasNoCode() {
        ToolResponse<String> response = ToolResponse.ok("data");

        assertEquals("OK", response.status());
        assertNull(response.code());
        assertTrue(response.isOk());
        assertEquals("data", response.data());
    }

    @Test
    @DisplayName("参数错误: status=ERROR, data 为空, isOk=false")
    void errorCarriesCode() {
        ToolResponse<String> response = ToolResponse.error("PARAM_INVALID", "productId 必须是正整数");

        assertEquals("ERROR", response.status());
        assertEquals("PARAM_INVALID", response.code());
        assertNull(response.data());
        assertTrue(!response.isOk());
    }

    @Test
    @DisplayName("超时降级: status=DEGRADED, 带空结果与 TOOL_TIMEOUT, 不算成功")
    void degradedKeepsPartialData() {
        ToolResponse<List<String>> response = ToolResponse.degraded("TOOL_TIMEOUT", "检索超时", List.of());

        assertEquals("DEGRADED", response.status());
        assertEquals("TOOL_TIMEOUT", response.code());
        assertTrue(response.data().isEmpty());
        assertTrue(!response.isOk());
    }
}
