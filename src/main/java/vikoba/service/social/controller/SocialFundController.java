package vikoba.service.social.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vikoba.service.common.response.ApiResponse;
import vikoba.service.social.dto.*;
import vikoba.service.social.entity.SocialFundType;
import vikoba.service.social.service.SocialFundService;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/social-fund")
public class SocialFundController {
    private final SocialFundService service;

    @GetMapping("/group/{groupId}/types")
    public ResponseEntity<ApiResponse<List<SocialFundType>>> types(@PathVariable Long groupId) {
        return ResponseEntity.ok(ApiResponse.success("Jamii fund types retrieved.", service.types(groupId)));
    }

    @GetMapping("/group/{groupId}/requests")
    public ResponseEntity<ApiResponse<List<SocialFundRequestResponse>>> requests(@PathVariable Long groupId) {
        return ResponseEntity.ok(ApiResponse.success("Jamii requests retrieved.", service.requests(groupId)));
    }

    @GetMapping("/group/{groupId}/contributions")
    public ResponseEntity<ApiResponse<List<SocialFundContributionResponse>>> contributions(@PathVariable Long groupId) {
        return ResponseEntity.ok(ApiResponse.success("Jamii contributions retrieved.", service.contributions(groupId)));
    }

    @GetMapping("/group/{groupId}/summary")
    public ResponseEntity<ApiResponse<SocialFundSummaryResponse>> summary(@PathVariable Long groupId) {
        return ResponseEntity.ok(ApiResponse.success("Jamii summary retrieved.", service.summary(groupId)));
    }

    @PostMapping("/group/{groupId}/requests")
    public ResponseEntity<ApiResponse<SocialFundRequestResponse>> request(@PathVariable Long groupId,
            @RequestBody SocialFundRequestInput input) {
        return ResponseEntity.ok(ApiResponse.success("Jamii request recorded.", service.request(groupId, input)));
    }

    @PostMapping("/group/{groupId}/requests/{requestId}/approve")
    public ResponseEntity<ApiResponse<SocialFundRequestResponse>> approve(@PathVariable Long groupId,
            @PathVariable Long requestId, @RequestParam BigDecimal amount) {
        return ResponseEntity
                .ok(ApiResponse.success("Jamii request approved.", service.approve(groupId, requestId, amount)));
    }

    @PostMapping("/group/{groupId}/requests/{requestId}/reject")
    public ResponseEntity<ApiResponse<SocialFundRequestResponse>> reject(@PathVariable Long groupId,
            @PathVariable Long requestId) {
        return ResponseEntity.ok(ApiResponse.success("Jamii request rejected.", service.reject(groupId, requestId)));
    }

    @PostMapping("/group/{groupId}/requests/{requestId}/pay")
    public ResponseEntity<ApiResponse<SocialFundRequestResponse>> pay(@PathVariable Long groupId,
            @PathVariable Long requestId) {
        return ResponseEntity.ok(ApiResponse.success("Jamii request paid.", service.pay(groupId, requestId)));
    }
}
