package com.credit.agent.api.web;

import com.credit.agent.orchestration.application.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.io.UncheckedIOException;

/**
 * 全局异常处理。
 * 业务含义: 把业务异常与库约束冲突翻译成结构化错误体, 避免把堆栈或 SQL 细节暴露给前端。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常: 按异常自带的 status 返回, 错误码保持原样。 */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBiz(BizException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ApiResponse.fail(ex.getCode(), ex.getMessage()));
    }

    /** 唯一约束冲突: 常见于并发重复提交, 明确告知前端重试或改查。 */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicate(DuplicateKeyException ex) {
        log.warn("唯一约束冲突: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(409).body(ApiResponse.fail("DUPLICATE_KEY", "记录已存在, 请勿重复提交"));
    }

    /** 参数类异常统一按 400 返回。 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.fail("INVALID_PARAM", ex.getMessage()));
    }

    /** 上传体积超过容器上限: 与 A2 的 413 口径保持一致, 不让前端拿到 500。 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(413).body(ApiResponse.fail("FILE_TOO_LARGE", "单文件不得超过 10MB"));
    }

    /** 本地文件读写失败: 不向前端暴露路径细节。 */
    @ExceptionHandler(UncheckedIOException.class)
    public ResponseEntity<ApiResponse<Void>> handleIo(UncheckedIOException ex) {
        log.error("文件读写失败", ex);
        return ResponseEntity.status(500).body(ApiResponse.fail("FILE_IO_ERROR", "文件读写失败, 请重试"));
    }
}
