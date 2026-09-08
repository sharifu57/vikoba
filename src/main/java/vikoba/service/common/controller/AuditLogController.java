package vikoba.service.common.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vikoba.service.common.dto.AuditLogResponse;
import vikoba.service.common.response.ApiResponse;
import vikoba.service.common.service.AuditLogService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/audit-logs")
public class AuditLogController {
    private final AuditLogService auditLogService;

    @GetMapping("/group/{groupId}")
    public ResponseEntity<ApiResponse<Page<AuditLogResponse>>> list(
            @PathVariable Long groupId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(
                ApiResponse.success("Audit logs retrieved successfully.", auditLogService.list(groupId, page, size)));
    }
}
