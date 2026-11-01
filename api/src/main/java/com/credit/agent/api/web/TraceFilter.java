package com.credit.agent.api.web;

import com.credit.agent.orchestration.application.observability.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 全链路 trace 入口(H1 的第一段: HTTP)。
 * 业务含义: 每个请求进来先定 trace_id —— 请求头带了就沿用(便于串前端链路), 没带就生成;
 * 写入 MDC 后所有日志自动带 traceId, 并通过响应头回传, 前端/联调可直接拿去查回放。
 * 必须在 finally 清理 MDC: Tomcat 线程池会复用线程, 不清会把上一个请求的 traceId 带脏。
 */
@Component
public class TraceFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TraceFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String incoming = request.getHeader(TraceContext.TRACE_HEADER);
        String traceId = (incoming == null || incoming.isBlank())
                ? TraceContext.newTraceId() : incoming.trim();
        TraceContext.bind(traceId);
        response.setHeader(TraceContext.TRACE_HEADER, traceId);
        long start = System.nanoTime();
        try {
            log.info("HTTP_ENTER {} {}", request.getMethod(), request.getRequestURI());
            filterChain.doFilter(request, response);
        } finally {
            long costMs = (System.nanoTime() - start) / 1_000_000L;
            log.info("HTTP_EXIT {} {} status={} costMs={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), costMs);
            TraceContext.clear();
        }
    }
}
