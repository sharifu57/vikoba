package vikoba.service.contribution.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vikoba.service.common.response.ApiResponse;
import vikoba.service.contribution.dto.*;
import vikoba.service.contribution.service.ContributionService;

import java.util.List;

/**
 * REST Controller for Contribution Management
 * Handles recording single and bulk contributions, viewing contribution history
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/contributions")
public class ContributionController {

    private final ContributionService contributionService;

    /**
     * Record a single member contribution
     * 
     * @param request RecordContributionRequest containing member ID, period, and
     *                amount
     * @return ApiResponse with contribution details
     */
    @PostMapping("/record")
    public ResponseEntity<ApiResponse<MemberContributionResponse>> recordContribution(
            @RequestBody RecordContributionRequest request) {
        try {
            MemberContributionResponse response = contributionService.recordContribution(request);
            return ResponseEntity.ok(
                    ApiResponse.success("Contribution recorded successfully.", response));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(ApiResponse.error(ex.getMessage()));
        }
    }

    /**
     * Upload bulk contributions from Excel file
     * Expected Excel format:
     * - Column A: Member Identifier (ID, Account Number, or Name)
     * - Column B: Contribution Period (e.g., "January 2024" or "2024-01")
     * - Column C: Paid Amount
     * - Column D: Payment Method
     * - Column E: Payment Reference
     * - Column F: Remarks
     * 
     * @param groupId The group ID for which contributions are being recorded
     * @param file    Excel file with contribution data
     * @return ApiResponse with bulk upload result
     */
    @PostMapping("/bulk-upload")
    public ResponseEntity<ApiResponse<BulkContributionResult>> bulkUploadContributions(
            @RequestParam Long groupId,
            @RequestParam("file") MultipartFile file) {
        try {
            BulkContributionResult result = contributionService.processBulkContributionUpload(groupId, file);
            return ResponseEntity.ok(
                    ApiResponse.success("Bulk contribution upload processed.", result));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(ApiResponse.error(ex.getMessage()));
        }
    }

    /**
     * Get all contribution periods for a group
     * Used to populate dropdown when recording contributions
     * 
     * @param groupId The group ID
     * @return ApiResponse with list of active contribution periods
     */
    @GetMapping("/periods")
    public ResponseEntity<ApiResponse<List<ContributionPeriodResponse>>> getContributionPeriods(
            @RequestParam Long groupId) {
        try {
            List<ContributionPeriodResponse> periods = contributionService.getActiveContributionPeriods(groupId);
            return ResponseEntity.ok(
                    ApiResponse.success("Contribution periods retrieved successfully.", periods));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(ApiResponse.error(ex.getMessage()));
        }
    }

    /**
     * Get all contributions for a specific member
     * Shows contribution history
     * 
     * @param groupMemberId The member ID
     * @return ApiResponse with list of member contributions
     */
    @GetMapping("/member/{groupMemberId}")
    public ResponseEntity<ApiResponse<List<ContributionDetailResponse>>> getMemberContributions(
            @PathVariable Long groupMemberId) {
        try {
            List<ContributionDetailResponse> contributions = contributionService
                    .getMemberContributionDetails(groupMemberId);
            return ResponseEntity.ok(
                    ApiResponse.success("Member contributions retrieved successfully.", contributions));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(ApiResponse.error(ex.getMessage()));
        }
    }

    /**
     * Get all contributions for a group (for viewing in a table)
     * 
     * @param groupId  The group ID
     * @param status   Optional status filter (PENDING, PAID, PARTIAL)
     * @param periodId Optional contribution period filter
     * @return ApiResponse with list of all contributions
     */
    @GetMapping("/group/{groupId}")
    public ResponseEntity<ApiResponse<List<ContributionDetailResponse>>> getGroupContributions(
            @PathVariable Long groupId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long periodId) {
        try {
            List<ContributionDetailResponse> contributions = contributionService.getGroupContributionDetails(groupId,
                    status, periodId);
            return ResponseEntity.ok(
                    ApiResponse.success("Group contributions retrieved successfully.", contributions));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(ApiResponse.error(ex.getMessage()));
        }
    }

    /**
     * Update a recorded contribution
     * 
     * @param contributionId The contribution ID to update
     * @param request        Updated contribution details
     * @return ApiResponse with updated contribution
     */
    @PutMapping("/{contributionId}")
    public ResponseEntity<ApiResponse<MemberContributionResponse>> updateContribution(
            @PathVariable Long contributionId,
            @RequestBody RecordContributionRequest request) {
        try {
            MemberContributionResponse response = contributionService.updateContribution(contributionId, request);
            return ResponseEntity.ok(
                    ApiResponse.success("Contribution updated successfully.", response));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(ApiResponse.error(ex.getMessage()));
        }
    }

    /**
     * Download Excel template for bulk contribution upload
     * Template shows the expected format for bulk upload
     * 
     * @return Excel template file
     */
    @GetMapping("/template/download")
    public ResponseEntity<byte[]> downloadBulkUploadTemplate() {
        try {
            byte[] template = contributionService.generateBulkUploadTemplate();
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=contribution_template.xlsx")
                    .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .body(template);
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get contribution summary statistics for a group
     * 
     * @param groupId The group ID
     * @return ApiResponse with summary statistics
     */
    @GetMapping("/group/{groupId}/summary")
    public ResponseEntity<ApiResponse<ContributionSummaryResponse>> getContributionSummary(
            @PathVariable Long groupId) {
        try {
            ContributionSummaryResponse summary = contributionService.getContributionSummary(groupId);
            return ResponseEntity.ok(
                    ApiResponse.success("Contribution summary retrieved successfully.", summary));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(ApiResponse.error(ex.getMessage()));
        }
    }
}
