package vikoba.service.contribution.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vikoba.service.common.response.ApiResponse;
import vikoba.service.contribution.dto.*;
import vikoba.service.contribution.service.ShareService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/shares")
public class ShareController {
    private final ShareService shareService;

    @GetMapping("/group/{groupId}/summary")
    public ResponseEntity<ApiResponse<ShareSummaryResponse>> summary(@PathVariable Long groupId) {
        return ResponseEntity
                .ok(ApiResponse.success("Share summary retrieved successfully.", shareService.getSummary(groupId)));
    }

    @GetMapping("/group/{groupId}/ownership")
    public ResponseEntity<ApiResponse<List<ShareOwnershipResponse>>> ownership(@PathVariable Long groupId) {
        return ResponseEntity
                .ok(ApiResponse.success("Share ownership retrieved successfully.", shareService.getOwnership(groupId)));
    }

    @GetMapping("/group/{groupId}/ledger")
    public ResponseEntity<ApiResponse<List<ShareTransactionResponse>>> ledger(@PathVariable Long groupId) {
        return ResponseEntity
                .ok(ApiResponse.success("Share ledger retrieved successfully.", shareService.getLedger(groupId)));
    }

    @PostMapping("/group/{groupId}/purchase")
    public ResponseEntity<ApiResponse<ShareTransactionResponse>> purchase(
            @PathVariable Long groupId, @RequestBody SharePurchaseRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success("Share purchase recorded successfully.", shareService.purchase(groupId, request)));
    }

    @PostMapping("/group/{groupId}/transfer")
    public ResponseEntity<ApiResponse<ShareTransactionResponse>> transfer(
            @PathVariable Long groupId, @RequestBody ShareTransferRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success("Share transfer recorded successfully.", shareService.transfer(groupId, request)));
    }

    @PostMapping("/group/{groupId}/redemption")
    public ResponseEntity<ApiResponse<ShareTransactionResponse>> redemption(
            @PathVariable Long groupId, @RequestBody ShareRedemptionRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success("Share redemption recorded successfully.", shareService.redeem(groupId, request)));
    }
}
