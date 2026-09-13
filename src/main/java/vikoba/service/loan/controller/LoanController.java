package vikoba.service.loan.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vikoba.service.common.response.ApiResponse;
import vikoba.service.loan.dto.*;
import vikoba.service.loan.service.LoanWorkflowService;
import vikoba.service.organization.dto.ShareApprovalStepConfig;
import java.util.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/loans/group/{groupId}")
public class LoanController {
    private final LoanWorkflowService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<LoanResponse>>> list(@PathVariable Long groupId) {
        return ResponseEntity.ok(ApiResponse.success("Loans retrieved successfully.", service.list(groupId)));
    }

    @GetMapping("/products")
    public ResponseEntity<ApiResponse<List<LoanProductResponse>>> products(@PathVariable Long groupId) {
        return ResponseEntity
                .ok(ApiResponse.success("Loan products retrieved successfully.", service.products(groupId)));
    }

    @GetMapping("/application-context")
    public ResponseEntity<ApiResponse<LoanApplicationContext>> applicationContext(@PathVariable Long groupId) {
        return ResponseEntity.ok(ApiResponse.success("Loan eligibility retrieved.", service.applicationContext(groupId)));
    }

    @GetMapping("/guarantees/mine")
    public ResponseEntity<ApiResponse<List<LoanGuaranteeRequest>>> guarantees(@PathVariable Long groupId) {
        return ResponseEntity.ok(ApiResponse.success("Guarantee requests retrieved.", service.guaranteeInbox(groupId)));
    }

    @PostMapping("/guarantees/{guaranteeId}/accept")
    public ResponseEntity<ApiResponse<LoanResponse>> acceptGuarantee(@PathVariable Long groupId, @PathVariable Long guaranteeId) {
        return ResponseEntity.ok(ApiResponse.success("Guarantee accepted.", service.decideGuarantee(groupId, guaranteeId, true)));
    }

    @PostMapping("/guarantees/{guaranteeId}/reject")
    public ResponseEntity<ApiResponse<LoanResponse>> rejectGuarantee(@PathVariable Long groupId, @PathVariable Long guaranteeId) {
        return ResponseEntity.ok(ApiResponse.success("Guarantee declined.", service.decideGuarantee(groupId, guaranteeId, false)));
    }

    @PostMapping("/{loanId}/guarantors/{guaranteeId}/replace")
    public ResponseEntity<ApiResponse<LoanResponse>> replaceGuarantee(@PathVariable Long groupId, @PathVariable Long loanId,
            @PathVariable Long guaranteeId, @RequestBody Map<String, Long> request) {
        return ResponseEntity.ok(ApiResponse.success("Replacement guarantor invited.",
                service.replaceGuarantor(groupId, loanId, guaranteeId, request.get("replacementId"))));
    }

    @PostMapping("/applications")
    public ResponseEntity<ApiResponse<LoanResponse>> apply(@PathVariable Long groupId, @RequestBody LoanRequest r) {
        return ResponseEntity
                .ok(ApiResponse.success("Loan application submitted successfully.", service.apply(groupId, r)));
    }

    @GetMapping("/approval-config")
    public ResponseEntity<ApiResponse<List<ShareApprovalStepConfig>>> approvalConfig(@PathVariable Long groupId) {
        return ResponseEntity.ok(ApiResponse.success("Loan approval workflow retrieved.", service.approvalConfig(groupId)));
    }

    @PutMapping("/approval-config")
    public ResponseEntity<ApiResponse<List<ShareApprovalStepConfig>>> configureApproval(@PathVariable Long groupId,
            @RequestBody List<ShareApprovalStepConfig> steps) {
        return ResponseEntity.ok(ApiResponse.success("Loan approval workflow saved.", service.configureApproval(groupId, steps)));
    }

    @PostMapping("/{loanId}/return")
    public ResponseEntity<ApiResponse<LoanResponse>> returnForReview(@PathVariable Long groupId, @PathVariable Long loanId,
            @RequestBody LoanDecisionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Loan returned to previous reviewer.", service.returnForReview(groupId, loanId, request)));
    }

    @PostMapping("/{loanId}/cancel")
    public ResponseEntity<ApiResponse<LoanResponse>> cancel(@PathVariable Long groupId, @PathVariable Long loanId,
            @RequestBody LoanDecisionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Loan application cancelled.", service.cancel(groupId, loanId, request)));
    }

    @PostMapping("/{loanId}/approve")
    public ResponseEntity<ApiResponse<LoanResponse>> approve(@PathVariable Long groupId, @PathVariable Long loanId) {
        return ResponseEntity.ok(ApiResponse.success("Loan approved successfully.", service.approve(groupId, loanId)));
    }

    @PostMapping("/{loanId}/reject")
    public ResponseEntity<ApiResponse<LoanResponse>> reject(@PathVariable Long groupId, @PathVariable Long loanId,
            @RequestBody LoanDecisionRequest r) {
        return ResponseEntity
                .ok(ApiResponse.success("Loan rejected successfully.", service.reject(groupId, loanId, r)));
    }

    @PostMapping("/{loanId}/disburse")
    public ResponseEntity<ApiResponse<LoanResponse>> disburse(@PathVariable Long groupId, @PathVariable Long loanId) {
        return ResponseEntity
                .ok(ApiResponse.success("Loan disbursed and schedule generated.", service.disburse(groupId, loanId)));
    }

    @GetMapping("/{loanId}/schedule")
    public ResponseEntity<ApiResponse<List<LoanInstallmentResponse>>> schedule(@PathVariable Long groupId,
            @PathVariable Long loanId) {
        return ResponseEntity.ok(
                ApiResponse.success("Repayment schedule retrieved successfully.", service.schedule(groupId, loanId)));
    }

    @PostMapping("/{loanId}/repayments")
    public ResponseEntity<ApiResponse<LoanResponse>> repay(@PathVariable Long groupId, @PathVariable Long loanId,
            @RequestBody LoanRepaymentRequest r) {
        return ResponseEntity
                .ok(ApiResponse.success("Loan repayment recorded successfully.", service.repay(groupId, loanId, r)));
    }

    @PostMapping("/assess-overdue")
    public ResponseEntity<ApiResponse<Integer>> overdue(@PathVariable Long groupId) {
        return ResponseEntity.ok(ApiResponse.success("Overdue installments assessed.", service.assessOverdue(groupId)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> invalid(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
    }
}
