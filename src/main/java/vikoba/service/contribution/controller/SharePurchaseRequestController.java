package vikoba.service.contribution.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import vikoba.service.common.response.ApiResponse;
import vikoba.service.contribution.dto.SharePurchaseRequestResponse;
import vikoba.service.contribution.entity.SharePurchaseRequestStatus;
import vikoba.service.contribution.service.SharePurchaseRequestService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/share-purchase-requests")
public class SharePurchaseRequestController {
    private final SharePurchaseRequestService service;

    @PostMapping(value = "/group/{groupId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<SharePurchaseRequestResponse>> submit(
            @PathVariable Long groupId,
            @RequestParam Long groupMemberId,
            @RequestParam java.math.BigDecimal amount,
            @RequestParam(required = false) Integer quantity,
            @RequestParam String paymentMethod,
            @RequestParam(required = false) String paymentReference,
            @RequestParam(required = false) String proofText,
            @RequestPart(required = false) MultipartFile proofFile) {
        return ResponseEntity.ok(ApiResponse.success("Share purchase proof submitted for review.",
                service.submit(groupId, groupMemberId, amount, quantity, paymentMethod, paymentReference, proofText,
                        proofFile)));
    }

    @GetMapping("/group/{groupId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<SharePurchaseRequestResponse>>> list(
            @PathVariable Long groupId,
            @RequestParam(required = false) SharePurchaseRequestStatus status) {
        return ResponseEntity.ok(ApiResponse.success("Share purchase requests retrieved successfully.",
                service.list(groupId, status)));
    }

    @PostMapping("/group/{groupId}/{requestId}/approve")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<SharePurchaseRequestResponse>> approve(@PathVariable Long groupId,
            @PathVariable Long requestId) {
        return ResponseEntity.ok(ApiResponse.success("Share purchase approved and added to the member.",
                service.approve(groupId, requestId)));
    }

    @PostMapping("/group/{groupId}/{requestId}/reject")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<SharePurchaseRequestResponse>> reject(@PathVariable Long groupId,
            @PathVariable Long requestId, @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(ApiResponse.success("Share purchase request rejected.",
                service.reject(groupId, requestId, reason)));
    }

    @GetMapping("/group/{groupId}/{requestId}/proof")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> proof(@PathVariable Long groupId, @PathVariable Long requestId) {
        byte[] proof = service.proof(groupId, requestId);
        String contentType = service.proofContentType(groupId, requestId);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .contentType(MediaType.parseMediaType(contentType == null ? "application/octet-stream" : contentType))
                .body(proof);
    }
}
