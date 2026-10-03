package vikoba.service.accounting.controller;

import lombok.RequiredArgsConstructor;
import vikoba.service.organization.service.GroupAuthorizationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vikoba.service.accounting.dto.*;
import vikoba.service.accounting.service.AccountingService;
import vikoba.service.common.response.ApiResponse;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/accounting/group/{groupId}")
public class AccountingController {
    private final GroupAuthorizationService authorizationService;
    private final AccountingService accountingService;

    @GetMapping("/accounts")
    public ResponseEntity<ApiResponse<List<AccountResponse>>> accounts(@PathVariable Long groupId) {
        return ResponseEntity
                .ok(ApiResponse.success("Accounts retrieved successfully.", accountingService.accounts(groupId)));
    }

    @PostMapping("/accounts")
    public ResponseEntity<ApiResponse<AccountResponse>> createAccount(@PathVariable Long groupId,
            @RequestBody AccountRequest request) {
        authorizationService.requirePermission(groupId, "CONTRIBUTION_MANAGE");
        return ResponseEntity.ok(ApiResponse.success("Account created successfully.",
                accountingService.createAccount(groupId, request)));
    }

    @PostMapping("/accounts/ensure-defaults")
    public ResponseEntity<ApiResponse<List<AccountResponse>>> ensureDefaultAccounts(@PathVariable Long groupId) {
        authorizationService.requirePermission(groupId, "CONTRIBUTION_MANAGE");
        return ResponseEntity.ok(ApiResponse.success("Default chart of accounts ensured successfully.",
                accountingService.ensureDefaultAccountsForGroup(groupId)));
    }

    @GetMapping("/ledger")
    public ResponseEntity<ApiResponse<List<LedgerLineResponse>>> ledger(@PathVariable Long groupId) {
        return ResponseEntity
                .ok(ApiResponse.success("General ledger retrieved successfully.", accountingService.ledger(groupId)));
    }

    @PostMapping("/journal-entries")
    public ResponseEntity<ApiResponse<LedgerLineResponse>> post(@PathVariable Long groupId,
            @RequestBody JournalEntryRequest request) {
        authorizationService.requirePermission(groupId, "CONTRIBUTION_MANAGE");
        return ResponseEntity.ok(
                ApiResponse.success("Journal entry posted successfully.", accountingService.post(groupId, request)));
    }

    @GetMapping("/trial-balance")
    public ResponseEntity<ApiResponse<TrialBalanceResponse>> trialBalance(@PathVariable Long groupId) {
        return ResponseEntity.ok(
                ApiResponse.success("Trial balance retrieved successfully.", accountingService.trialBalance(groupId)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> validation(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(ApiResponse.error(exception.getMessage()));
    }
}
