package vikoba.service.report.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vikoba.service.common.response.ApiResponse;
import vikoba.service.report.dto.GroupReportResponse;
import vikoba.service.report.service.GroupReportService;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reports")
public class ReportController {
    private final GroupReportService reportService;

    @GetMapping("/group/{groupId}")
    public ResponseEntity<ApiResponse<GroupReportResponse>> groupReport(
            @PathVariable Long groupId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ResponseEntity.ok(ApiResponse.success("Group report generated successfully.",
                reportService.generate(groupId, start, end)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> validation(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(ApiResponse.error(error.getMessage()));
    }
}
