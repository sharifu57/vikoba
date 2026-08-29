package vikoba.service.auth.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vikoba.service.auth.dto.AuthLookUpResponse;
import vikoba.service.auth.dto.LoginRequest;
import vikoba.service.auth.dto.RegisterRequest;
import vikoba.service.auth.dto.ResendOtpRequest;
import vikoba.service.auth.dto.UserSessionResponse;
import vikoba.service.auth.dto.UserSessionWithGroupResponse;
import vikoba.service.auth.dto.VerifyOtpRequest;
import vikoba.service.auth.service.AuthService;
import vikoba.service.common.response.AuthResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    @PostMapping("/lookup")
    public ResponseEntity<AuthResponse<AuthLookUpResponse>> lookup(
            @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.lookUp(request));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse<AuthLookUpResponse>> register(
            @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<AuthResponse<UserSessionWithGroupResponse>> verifyOtp(
            @RequestBody VerifyOtpRequest request) {
        AuthResponse<UserSessionWithGroupResponse> response = authService.verifyOtp(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<AuthResponse<Void>> resendOtp(
            @RequestBody ResendOtpRequest request) {
        AuthResponse<Void> response = authService.resendOtp(request);
        return ResponseEntity.ok(response);
    }
}