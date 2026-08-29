package vikoba.service.contribution.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vikoba.service.common.response.ApiResponse;
import vikoba.service.contribution.dto.PaymentResponse;
import vikoba.service.contribution.dto.RecordPaymentRequest;
import vikoba.service.contribution.service.PaymentService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentService paymentService;

    @GetMapping("/group/{groupId}")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> list(@PathVariable Long groupId) {
        return ResponseEntity.ok(ApiResponse.success("Payments retrieved successfully.", paymentService.list(groupId)));
    }

    @PostMapping("/group/{groupId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> record(
            @PathVariable Long groupId, @RequestBody RecordPaymentRequest request) {
        return ResponseEntity
                .ok(ApiResponse.success("Payment recorded successfully.", paymentService.record(groupId, request)));
    }
}
