package com.ccerphr.assessment.controller;

import com.ccerphr.assessment.common.Result;
import com.ccerphr.assessment.dto.AppealCreateDTO;
import com.ccerphr.assessment.dto.AppealHandleDTO;
import com.ccerphr.assessment.dto.AppealQueryDTO;
import com.ccerphr.assessment.entity.BizAppeal;
import com.ccerphr.assessment.entity.BizAppealAttachment;
import com.ccerphr.assessment.service.BizAppealService;
import com.ccerphr.assessment.util.DataScopeFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/appeal")
public class BizAppealController {

    private final BizAppealService appealService;

    @Value("${app.upload.path:uploads/appeal/}")
    private String uploadPath;

    private final com.ccerphr.assessment.mapper.BizAppealAttachmentMapper attachmentMapper;

    // 允许上传的文件类型白名单
    private static final java.util.Set<String> ALLOWED_EXTENSIONS = java.util.Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp",  // 图片
            ".pdf",  // PDF
            ".doc", ".docx",  // Word
            ".xls", ".xlsx",  // Excel
            ".txt", ".csv"  // 文本
    );

    // 最大文件大小 20MB
    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024;

    public BizAppealController(BizAppealService appealService,
                               com.ccerphr.assessment.mapper.BizAppealAttachmentMapper attachmentMapper) {
        this.appealService = appealService;
        this.attachmentMapper = attachmentMapper;
    }

    @GetMapping("/list")
    public Result<Map<String, Object>> list(AppealQueryDTO queryDTO) {
        List<BizAppeal> records = appealService.getAppealList(queryDTO);
        Long total = appealService.countAppealList(queryDTO);
        Map<String, Object> result = Map.of(
            "records", records,
            "total", total,
            "current", queryDTO.getCurrent(),
            "size", queryDTO.getSize()
        );
        return Result.success(result);
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(appealService.getAppealDetail(id));
    }

    @PostMapping
    public Result<BizAppeal> create(@Valid @RequestBody AppealCreateDTO dto, HttpServletRequest request) {
        if (DataScopeFilter.isReadOnly()) {
            return Result.error("当前数据范围下不可修改业务数据");
        }
        String createdBy = getCurrentUser(request);
        return Result.success(appealService.createAppeal(dto, createdBy));
    }

    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        appealService.submitAppeal(id);
        return Result.success();
    }

    @PostMapping("/{id}/reassign")
    public Result<Void> reassign(@PathVariable Long id) {
        appealService.reassignAppeal(id);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        if (DataScopeFilter.isReadOnly()) {
            return Result.error("当前数据范围下不可修改业务数据");
        }
        appealService.removeById(id);
        return Result.success();
    }

    @PostMapping("/{id}/handle")
    public Result<Void> handle(@PathVariable Long id, @Valid @RequestBody AppealHandleDTO dto, HttpServletRequest request) {
        dto.setAppealId(id);
        String handledBy = getCurrentUser(request);
        appealService.handleAppeal(dto, handledBy);
        return Result.success();
    }

    @PostMapping("/upload")
    public Result<BizAppealAttachment> upload(@RequestParam("file") MultipartFile file,
                                               @RequestParam("appealId") Long appealId) {
        if (file.isEmpty()) {
            return Result.error("文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            return Result.error("文件大小不能超过20MB");
        }

        // 获取原始文件名和扩展名
        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf(".")).toLowerCase();
        }

        // 安全检查：验证文件扩展名是否在白名单中
        if (ext.isEmpty() || !ALLOWED_EXTENSIONS.contains(ext)) {
            return Result.error("不支持的文件类型: " + ext + "。允许的类型: " + ALLOWED_EXTENSIONS);
        }

        // 安全检查：验证文件 MIME 类型
        String contentType = file.getContentType();
        if (contentType == null || !isAllowedMimeType(contentType)) {
            return Result.error("不支持的文件内容类型: " + contentType);
        }

        try {
            Path dirPath = Paths.get(uploadPath);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            String fileName = UUID.randomUUID().toString().replace("-", "") + ext;
            Path filePath = dirPath.resolve(fileName);
            file.transferTo(filePath.toFile());

            BizAppealAttachment attachment = new BizAppealAttachment();
            attachment.setAppealId(appealId);
            attachment.setFileName(originalName);
            attachment.setFileUrl(filePath.toString());
            attachment.setFileSize(file.getSize());
            attachment.setFileType(file.getContentType());
            attachment.setCreatedTime(java.time.LocalDateTime.now());
            attachmentMapper.insert(attachment);
            return Result.success(attachment);
        } catch (IOException e) {
            return Result.error("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 验证 MIME 类型是否在允许范围内
     */
    private boolean isAllowedMimeType(String mimeType) {
        mimeType = mimeType.toLowerCase();
        return mimeType.startsWith("image/") ||
               mimeType.equals("application/pdf") ||
               mimeType.equals("application/msword") ||
               mimeType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document") ||
               mimeType.equals("application/vnd.ms-excel") ||
               mimeType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") ||
               mimeType.equals("text/plain") ||
               mimeType.equals("text/csv");
    }

    @GetMapping("/{appealId}/attachments")
    public Result<List<BizAppealAttachment>> attachments(@PathVariable Long appealId) {
        return Result.success(attachmentMapper.selectByAppealId(appealId));
    }

    @DeleteMapping("/attachment/{attachmentId}")
    public Result<Void> deleteAttachment(@PathVariable Long attachmentId) {
        BizAppealAttachment att = attachmentMapper.selectById(attachmentId);
        if (att != null && att.getFileUrl() != null) {
            try {
                java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(att.getFileUrl()));
            } catch (Exception e) {
                // ignore file delete error
            }
        }
        attachmentMapper.deleteById(attachmentId);
        return Result.success();
    }

    @GetMapping("/pending-reeval")
    public Result<List<BizAppeal>> pendingReeval(@RequestParam Long scorerOrgId) {
        return Result.success(appealService.getPendingReevalList(scorerOrgId));
    }

    @PostMapping("/{id}/re-score")
    public Result<Void> reScore(@PathVariable Long id, @RequestBody AppealHandleDTO dto, HttpServletRequest request) {
        String handledBy = getCurrentUser(request);
        appealService.reScoreAppeal(id, dto, handledBy);
        return Result.success();
    }

    private String getCurrentUser(HttpServletRequest request) {
        String user = request.getHeader("X-Current-User");
        if (user == null || user.isEmpty()) {
            user = "system";
        }
        return user;
    }
}
