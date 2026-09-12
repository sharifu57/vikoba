package vikoba.service.contribution.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import vikoba.service.common.response.ApiResponse;
import vikoba.service.contribution.dto.*;
import vikoba.service.contribution.service.ShareService;
import vikoba.service.organization.entity.GroupMember;
import vikoba.service.organization.service.GroupAuthorizationService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/shares")
public class ShareController {
    private final ShareService shareService;
    private final GroupAuthorizationService authorizationService;

    @GetMapping("/group/{groupId}/summary")
    public ResponseEntity<ApiResponse<ShareSummaryResponse>> summary(@PathVariable Long groupId) {
        authorizationService.requireCurrentMembership(groupId);
        return ResponseEntity
                .ok(ApiResponse.success("Share summary retrieved successfully.", shareService.getSummary(groupId)));
    }

    @GetMapping("/group/{groupId}/ownership")
    public ResponseEntity<ApiResponse<List<ShareOwnershipResponse>>> ownership(@PathVariable Long groupId) {
        authorizationService.requireCurrentMembership(groupId);
        return ResponseEntity
                .ok(ApiResponse.success("Share ownership retrieved successfully.", shareService.getOwnership(groupId)));
    }

    @GetMapping("/group/{groupId}/ledger")
    public ResponseEntity<ApiResponse<List<ShareTransactionResponse>>> ledger(@PathVariable Long groupId) {
        authorizationService.requireCurrentMembership(groupId);
        return ResponseEntity
                .ok(ApiResponse.success("Share ledger retrieved successfully.", shareService.getLedger(groupId)));
    }

    @PostMapping("/group/{groupId}/purchase")
    public ResponseEntity<ApiResponse<ShareTransactionResponse>> purchase(
            @PathVariable Long groupId, @RequestBody SharePurchaseRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(
                "Submit a share purchase request with payment proof before shares can be credited."));
    }

    @PostMapping("/group/{groupId}/transfer")
    public ResponseEntity<ApiResponse<ShareTransactionResponse>> transfer(
            @PathVariable Long groupId, @RequestBody ShareTransferRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(
                "Share transfers are not available through the member self-service flow."));
    }

    @PostMapping("/group/{groupId}/redemption")
    public ResponseEntity<ApiResponse<ShareTransactionResponse>> redemption(
            @PathVariable Long groupId, @RequestBody ShareRedemptionRequest request) {
        GroupMember currentMember = authorizationService.requireCurrentMembership(groupId);
        if (!currentMember.getId().equals(request.getGroupMemberId())) {
            throw new AccessDeniedException("You may only redeem shares from your own membership");
        }
        return ResponseEntity.ok(
                ApiResponse.success("Share redemption recorded successfully.", shareService.redeem(groupId, request)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(ApiResponse.error(exception.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> forbidden(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(exception.getMessage()));
    }
}
