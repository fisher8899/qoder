package com.ccerphr.assessment.common;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("参数校验失败");
        return Result.error(400, message);
    }

    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("参数绑定失败");
        return Result.error(400, message);
    }

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public Result<Void> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        String msg = e.getMessage();
        // 根据异常信息判断具体原因，返回更友好的提示
        if (msg != null && msg.contains("Duplicate entry")) {
            // 唯一键冲突，提取具体值
            return Result.error(500, "数据重复，该记录已存在");
        }
        if (msg != null && msg.contains("cannot be null")) {
            return Result.error(500, "必填字段不能为空，请检查输入数据");
        }
        return Result.error(500, "数据保存失败，请检查输入数据的完整性");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        return Result.error(500, "系统繁忙，请稍后重试");
    }
}
