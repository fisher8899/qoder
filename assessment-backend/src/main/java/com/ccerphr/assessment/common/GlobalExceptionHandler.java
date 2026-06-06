package com.ccerphr.assessment.common;

import com.ccerphr.assessment.security.LoginRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e,
                                                              HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("参数校验失败");
        log.warn("requestId={} uri={} validation failed: {}",
                resolveRequestId(request), getUri(request), message);
        return Result.error(400, message);
    }

    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e, HttpServletRequest request) {
        String message = e.getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("参数绑定失败");
        log.warn("requestId={} uri={} bind failed: {}",
                resolveRequestId(request), getUri(request), message);
        return Result.error(400, message);
    }

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("requestId={} uri={} business exception: code={}, message={}",
                resolveRequestId(request), getUri(request), e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(LoginRateLimiter.RateLimitExceededException.class)
    public ResponseEntity<Result<Void>> handleRateLimit(LoginRateLimiter.RateLimitExceededException e,
                                                        HttpServletRequest request) {
        log.warn("requestId={} uri={} rate limit exceeded: {}",
                resolveRequestId(request), getUri(request), e.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Result.error(429, e.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public Result<Void> handleDataIntegrityViolation(DataIntegrityViolationException e,
                                                     HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        log.error("requestId={} uri={} data integrity violation", requestId, getUri(request), e);
        return Result.error(500, "数据保存失败，请联系管理员并提供参考号: " + requestId);
    }

    @ExceptionHandler(SecurityException.class)
    public Result<Void> handleSecurityException(SecurityException e, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        log.warn("requestId={} uri={} security exception: {}",
                requestId, getUri(request), e.getMessage());
        return Result.error(403, "无权限访问该资源。参考号: " + requestId);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        log.error("requestId={} uri={} unhandled exception", requestId, getUri(request), e);
        return Result.error(500, "系统繁忙，请稍后重试。参考号: " + requestId);
    }

    private String resolveRequestId(HttpServletRequest request) {
        if (request == null) {
            return UUID.randomUUID().toString();
        }
        String requestId = request.getHeader("X-Request-Id");
        if (isBlank(requestId)) {
            Object attr = request.getAttribute("requestId");
            requestId = attr instanceof String ? (String) attr : null;
        }
        if (isBlank(requestId)) {
            requestId = UUID.randomUUID().toString();
            request.setAttribute("requestId", requestId);
        }
        return requestId;
    }

    private String getUri(HttpServletRequest request) {
        return request == null ? "N/A" : request.getRequestURI();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
