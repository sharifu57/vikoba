package vikoba.service.auth.service;

import lombok.AllArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import vikoba.service.config.JwtService;
import vikoba.service.auth.dto.AuthLookUpResponse;
import vikoba.service.auth.dto.LoginRequest;
import vikoba.service.auth.dto.RegisterRequest;
import vikoba.service.auth.dto.ResendOtpRequest;
import vikoba.service.auth.dto.UserSessionResponse;
import vikoba.service.auth.dto.VerifyOtpRequest;
import vikoba.service.auth.entity.OTP;
import vikoba.service.auth.entity.User;
import vikoba.service.auth.repository.OTPRepository;
import vikoba.service.auth.repository.UserRepository;
import vikoba.service.common.enums.AuthStatus;
import vikoba.service.common.enums.UserStatus;
import vikoba.service.common.response.AuthResponse;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Optional;

@Service
@AllArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final OTPRepository otpRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int OTP_EXPIRATION_MINUTES = 5;

    public AuthResponse<AuthLookUpResponse> lookUp(LoginRequest request) {
        Optional<User> optionalUser = userRepository.findByPhone(request.getPhone());

        if (optionalUser.isEmpty()) {

            return new AuthResponse<>(
                    false,
                    "New user registration required.",
                    new AuthLookUpResponse(AuthStatus.NEW));
        }

        User user = optionalUser.get();

        if (user.getStatus().equals(UserStatus.DISABLED)) {
            return new AuthResponse<>(
                    false,
                    "Your account has been disabled.",
                    null);

        }

        createLoginOtp(user);

        return new AuthResponse<>(
                true,
                "OTP sent successfully.",
                new AuthLookUpResponse(null));
    }

    @Transactional
    public AuthResponse<AuthLookUpResponse> register(RegisterRequest request) {
        if (userRepository.existsByPhone(request.getPhone())) {
            return new AuthResponse<>(false, "A user with this phone number already exists.", null);
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            return new AuthResponse<>(false, "A user with this email already exists.", null);
        }

        User user = User.builder()
                .username(request.getFullName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                .status(UserStatus.ACTIVE)
                .failedLoginAttempts(0)
                .build();

        userRepository.save(user);
        createOtp(user, "phone_verification");

        return new AuthResponse<>(
                true,
                "Registration successful. OTP sent for phone verification.",
                new AuthLookUpResponse(AuthStatus.NEW));
    }

    @Transactional
    public AuthResponse<Void> resendOtp(ResendOtpRequest request) {
        String phone = request.getPhone();
        String purpose = request.getPurpose() == null || request.getPurpose().isBlank()
                ? "login"
                : request.getPurpose();

        if (phone == null || phone.isBlank()) {
            return new AuthResponse<>(false, "Phone number is required.", null);
        }

        Optional<User> optionalUser = userRepository.findByPhone(phone);
        if (optionalUser.isEmpty()) {
            return new AuthResponse<>(false, "No user found for this phone number.", null);
        }

        User user = optionalUser.get();
        if (user.getStatus() != null && user.getStatus().equals(UserStatus.DISABLED)) {
            return new AuthResponse<>(false, "Your account has been disabled.", null);
        }

        createOtp(user, purpose);
        return new AuthResponse<>(true, "OTP resent successfully.", null);
    }

    @Transactional
    public AuthResponse<UserSessionResponse> verifyOtp(VerifyOtpRequest request) {
        String purpose = request.getPurpose() == null || request.getPurpose().isBlank()
                ? "login"
                : request.getPurpose();
        Optional<OTP> optionalOtp = otpRepository
                .findTopByPhoneAndPurposeAndIsUsedFalseAndIsExpiredFalseOrderByIdDesc(
                        request.getPhone(), purpose);

        if (optionalOtp.isEmpty()) {
            return new AuthResponse<>(false, "Invalid or expired OTP.", null);
        }

        OTP otp = optionalOtp.get();
        LocalDateTime now = LocalDateTime.now();

        if (otp.getExpiresAt() == null || now.isAfter(otp.getExpiresAt())) {
            otp.setIsExpired(true);
            otpRepository.save(otp);
            return new AuthResponse<>(false, "Invalid or expired OTP.", null);
        }

        if (otp.getAttempts() >= otp.getMaxAttempts()) {
            otp.setIsExpired(true);
            otpRepository.save(otp);
            return new AuthResponse<>(false, "Too many OTP attempts.", null);
        }

        if (!otp.getCode().equals(request.getCode())) {
            otp.setAttempts(otp.getAttempts() + 1);
            if (otp.getAttempts() >= otp.getMaxAttempts()) {
                otp.setIsExpired(true);
            }
            otpRepository.save(otp);
            return new AuthResponse<>(false, "Invalid OTP.", null);
        }

        User user = userRepository.findByPhone(request.getPhone()).orElse(null);
        if (user == null || (!UserStatus.ACTIVE.equals(user.getStatus())
                && !"phone_verification".equals(purpose))) {
            return new AuthResponse<>(false, "Your account has been disabled.", null);
        }

        otp.setIsUsed(true);
        otpRepository.save(otp);
        if ("phone_verification".equals(purpose)) {
            user.setStatus(UserStatus.ACTIVE);
        }
        user.setLastLoginAt(now);
        userRepository.save(user);

        UserSessionResponse userSession = new UserSessionResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPhone(),
                user.getStatus(),
                user.getLastLoginAt());

        AuthResponse<UserSessionResponse> response = new AuthResponse<>(true, "Login successful.", userSession);
        response.setToken(jwtService.generateAccessToken(user.getPhone()));
        response.setRefreshToken(jwtService.generateRefreshToken(user.getPhone()));
        response.setExpired(String.valueOf(jwtService.getExpirationTime()));
        return response;
    }

    private void createLoginOtp(User user) {
        createOtp(user, "login");
    }

    private void createOtp(User user, String purpose) {
        OTP otp = new OTP();
        otp.setPhone(user.getPhone());
        otp.setUser(user);
        otp.setCode(String.format("%06d", RANDOM.nextInt(1_000_000)));
        otp.setPurpose(purpose);
        otp.setAttempts(0);
        otp.setMaxAttempts(5);
        otp.setIsUsed(false);
        otp.setIsExpired(false);
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRATION_MINUTES));
        otpRepository.save(otp);
    }
}
