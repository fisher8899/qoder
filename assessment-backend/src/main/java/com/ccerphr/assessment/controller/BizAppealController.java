package com.ccerphr.assessment.controller;

import com.ccerphr.assessment.common.Result;
import com.ccerphr.assessment.dto.AppealCreateDTO;
import com.ccerphr.assessment.dto.AppealHandleDTO;
import com.ccerphr.assessment.dto.AppealQueryDTO;
import com.ccerphr.assessment.entity.BizAppeal;
import com.ccerphr.assessment.entity.BizAppealAttachment;
import com.ccerphr.assessment.mapper.BizAppealAttachmentMapper;
import com.ccerphr.assessment.security.RequireRole;
import com.ccerphr.assessment.security.SecurityUtil;
import com.ccerphr.assessment.service.BizAppealService;
import com.ccerphr.assessment.util.DataScopeFilter;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/appeal")
public class BizAppealController {

    private static final Logger log = LoggerFactory.getLogger(BizAppealController.class);

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp",
            ".pdf",
            ".doc", ".docx",
            ".xls", ".xlsx",
            ".txt", ".csv"
    );

    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024;

    private final BizAppealService appealService;
    private final BizAppealAttachmentMapper attachmentMapper;

    @Value("${app.upload.path:./uploads/appeal}")
    private String uploadPath;

    public BizAppealController(BizAppealService appealService,
                               BizAppealAttachmentMapper attachmentMapper) {
        this.appealService = appealService;
        this.attachmentMapper = attachmentMapper;
    }

    @GetMapping("/list")
    public Result<Map<String, Object>> list(AppealQueryDTO queryDTO) {
        List<BizAppeal> records = appealService.getAppealList(queryDTO);
        Long total = appealService.countAppealList(queryDTO);
        return Result.success(Map.of(
                "records", records,
                "total", total,
                "current", queryDTO.getCurrent(),
                "size", queryDTO.getSize()
        ));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        Map<String, Object> detail = new HashMap<>(appealService.getAppealDetail(id));
        @SuppressWarnings("unchecked")
        List<BizAppealAttachment> attachments = (List<BizAppealAttachment>) detail.get("attachments");
        detail.put("attachments", attachments.stream().map(this::sanitizeAttachment).toList());
        return Result.success(detail);
    }

    @PostMapping
    public Result<BizAppeal> create(@Valid @RequestBody AppealCreateDTO dto) {
        if (DataScopeFilter.isReadOnly()) {
            return Result.error("当前数据范围下不可修改业务数据");
        }
        String createdBy = SecurityUtil.getCurrentUserName();
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
        appealService.getAccessibleAppeal(id);
        appealService.removeById(id);
        return Result.success();
    }

    @PostMapping("/{id}/handle")
    @RequireRole({"SUPERVISOR", "FIN_ADMIN", "ADMIN"})
    public Result<Void> handle(@PathVariable Long id, @Valid @RequestBody AppealHandleDTO dto) {
        dto.setAppealId(id);
        String handledBy = SecurityUtil.getCurrentUserName();
        appealService.handleAppeal(dto, handledBy);
        return Result.success();
    }

    @PostMapping("/upload")
    public Result<BizAppealAttachment> upload(@RequestParam("file") MultipartFile file,
                                              @RequestParam("appealId") Long appealId) {
        appealService.getAccessibleAppeal(appealId);

        if (file.isEmpty()) {
            return Result.error("文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            return Result.error("文件大小不能超过20MB");
        }

        String originalName = file.getOriginalFilename();
        String extension = resolveExtension(originalName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            return Result.error("不支持的文件类型: " + extension);
        }

        try {
            byte[] fileData = file.getBytes();
            if (!isAllowedFileContent(extension, fileData)) {
                return Result.error("文件扩展名与内容不匹配");
            }
            String contentType = resolveSafeContentType(extension);

            Path dirPath = getAttachmentDirectory();
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            String fileName = UUID.randomUUID().toString().replace("-", "") + extension;
            Path filePath = dirPath.resolve(fileName).normalize();
            if (!filePath.startsWith(dirPath)) {
                return Result.error("非法文件路径");
            }
            Files.write(filePath, fileData);

            BizAppealAttachment attachment = new BizAppealAttachment();
            attachment.setAppealId(appealId);
            attachment.setFileName(originalName);
            attachment.setFileUrl(fileName); // 存储文件名而非绝对路径
            attachment.setFileSize(file.getSize());
            attachment.setFileType(contentType);
            attachment.setCreatedTime(LocalDateTime.now());
            attachmentMapper.insert(attachment);
            return Result.success(sanitizeAttachment(attachment));
        } catch (IOException e) {
            log.warn("Failed to upload appeal attachment, appealId={}, file={}", appealId, originalName, e);
            return Result.error("文件上传失败，请稍后重试");
        }
    }

    @GetMapping("/{appealId}/attachments")
    public Result<List<BizAppealAttachment>> attachments(@PathVariable Long appealId) {
        appealService.getAccessibleAppeal(appealId);
        return Result.success(
                attachmentMapper.selectByAppealId(appealId).stream()
                        .map(this::sanitizeAttachment)
                        .toList()
        );
    }

    @GetMapping("/attachment/{attachmentId}/download")
    public void downloadAttachment(@PathVariable Long attachmentId, HttpServletResponse response) throws IOException {
        BizAppealAttachment attachment = appealService.getAccessibleAttachment(attachmentId);
        Path filePath = resolveStoredAttachmentPath(attachment.getFileUrl());
        String contentType = resolveSafeContentType(resolveExtension(attachment.getFileName()));
        response.setContentType(contentType != null ? contentType : "application/octet-stream");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader(
                "Content-Disposition",
                "attachment; filename*=UTF-8''" + URLEncoder.encode(attachment.getFileName(), StandardCharsets.UTF_8)
        );
        Files.copy(filePath, response.getOutputStream());
        response.getOutputStream().flush();
    }

    @DeleteMapping("/attachment/{attachmentId}")
    public Result<Void> deleteAttachment(@PathVariable Long attachmentId) {
        BizAppealAttachment attachment = appealService.getAccessibleAttachment(attachmentId);
        try {
            Files.deleteIfExists(resolveStoredAttachmentPath(attachment.getFileUrl()));
        } catch (IOException e) {
            log.warn("Failed to delete appeal attachment file, attachmentId={}", attachmentId, e);
        }
        attachmentMapper.deleteById(attachmentId);
        return Result.success();
    }

    @GetMapping("/pending-reeval")
    public Result<List<BizAppeal>> pendingReeval(@RequestParam Long scorerOrgId) {
        return Result.success(appealService.getPendingReevalList(scorerOrgId));
    }

    @PostMapping("/{id}/re-score")
    @RequireRole({"SUPERVISOR", "FIN_ADMIN", "ADMIN"})
    public Result<Void> reScore(@PathVariable Long id, @RequestBody AppealHandleDTO dto) {
        String handledBy = SecurityUtil.getCurrentUserName();
        appealService.reScoreAppeal(id, dto, handledBy);
        return Result.success();
    }

    private boolean isAllowedFileContent(String extension, byte[] fileData) {
        return switch (extension) {
            case ".pdf" -> startsWith(fileData, new byte[]{0x25, 0x50, 0x44, 0x46});
            case ".jpg", ".jpeg" -> startsWith(fileData, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
            case ".png" -> startsWith(fileData, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});
            case ".gif" -> startsWith(fileData, "GIF87a".getBytes(StandardCharsets.US_ASCII))
                    || startsWith(fileData, "GIF89a".getBytes(StandardCharsets.US_ASCII));
            case ".bmp" -> startsWith(fileData, new byte[]{0x42, 0x4D});
            case ".webp" -> startsWith(fileData, "RIFF".getBytes(StandardCharsets.US_ASCII))
                    && hasBytesAt(fileData, 8, "WEBP".getBytes(StandardCharsets.US_ASCII));
            case ".doc", ".xls" -> startsWith(fileData, new byte[]{
                    (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1
            });
            case ".docx", ".xlsx" -> isZipHeader(fileData);
            case ".txt", ".csv" -> looksLikeText(fileData);
            default -> false;
        };
    }

    private String resolveSafeContentType(String extension) {
        return switch (extension) {
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".png" -> "image/png";
            case ".gif" -> "image/gif";
            case ".bmp" -> "image/bmp";
            case ".webp" -> "image/webp";
            case ".pdf" -> "application/pdf";
            case ".doc" -> "application/msword";
            case ".docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case ".xls" -> "application/vnd.ms-excel";
            case ".xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case ".csv" -> "text/csv;charset=UTF-8";
            case ".txt" -> "text/plain;charset=UTF-8";
            default -> "application/octet-stream";
        };
    }

    private boolean isZipHeader(byte[] fileData) {
        return startsWith(fileData, new byte[]{0x50, 0x4B, 0x03, 0x04})
                || startsWith(fileData, new byte[]{0x50, 0x4B, 0x05, 0x06})
                || startsWith(fileData, new byte[]{0x50, 0x4B, 0x07, 0x08});
    }

    private boolean looksLikeText(byte[] fileData) {
        if (fileData == null || fileData.length == 0) {
            return false;
        }
        int checked = Math.min(fileData.length, 4096);
        for (int i = 0; i < checked; i++) {
            if (fileData[i] == 0) {
                return false;
            }
        }
        return true;
    }

    private boolean hasBytesAt(byte[] source, int offset, byte[] expected) {
        if (source == null || expected == null || offset < 0 || source.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (source[offset + i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean startsWith(byte[] source, byte[] prefix) {
        if (source == null || prefix == null || source.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (source[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private String resolveExtension(String originalName) {
        if (originalName == null || !originalName.contains(".")) {
            return "";
        }
        return originalName.substring(originalName.lastIndexOf('.')).toLowerCase();
    }

    private BizAppealAttachment sanitizeAttachment(BizAppealAttachment attachment) {
        BizAppealAttachment sanitized = new BizAppealAttachment();
        sanitized.setId(attachment.getId());
        sanitized.setAppealId(attachment.getAppealId());
        sanitized.setFileName(attachment.getFileName());
        sanitized.setFileUrl("/api/appeal/attachment/" + attachment.getId() + "/download");
        sanitized.setFileSize(attachment.getFileSize());
        sanitized.setFileType(attachment.getFileType());
        sanitized.setCreatedTime(attachment.getCreatedTime());
        sanitized.setDeleted(attachment.getDeleted());
        return sanitized;
    }

    private Path getAttachmentDirectory() {
        return Paths.get(uploadPath).toAbsolutePath().normalize();
    }

    private Path resolveStoredAttachmentPath(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            throw new IllegalArgumentException("附件路径为空");
        }
        Path baseDir = getAttachmentDirectory();
        Path path = Paths.get(storedPath);
        Path resolved;
        if (path.isAbsolute()) {
            resolved = path.normalize();
        } else {
            resolved = baseDir.resolve(path).normalize();
        }

        if (!resolved.startsWith(baseDir)) {
            throw new IllegalArgumentException("非法附件路径");
        }
        return resolved;
    }
}
