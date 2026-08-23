package vikoba.service.auth.service;

import lombok.AllArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import vikoba.service.auth.dto.*;
import vikoba.service.config.JwtService;
import vikoba.service.auth.entity.OTP;
import vikoba.service.auth.entity.User;
import vikoba.service.auth.repository.OTPRepository;
import vikoba.service.auth.repository.UserRepository;
import vikoba.service.common.enums.AuthStatus;
import vikoba.service.common.enums.UserStatus;
import vikoba.service.common.response.AuthResponse;
import vikoba.service.organization.dto.GroupSettingsRequest;
import vikoba.service.organization.dto.VikobaGroupCreateResponse;
import vikoba.service.organization.entity.*;
import vikoba.service.organization.repository.GroupMemberRepository;
import vikoba.service.organization.repository.MemberRepository;
import lombok.extern.slf4j.Slf4j;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

@Slf4j
@Service
@AllArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final OTPRepository otpRepository;
    private final MemberRepository memberRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final JwtService jwtService;
    private final vikoba.service.organization.repository.VikobaGroupRepository vikobaGroupRepository;
    private final vikoba.service.organization.repository.GroupSettingsRepository groupSettingsRepository;
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

        // ============================================================
        // 1. VALIDATE PHONE
        // ============================================================

        if (request.getPhone() == null || request.getPhone().isBlank()) {
            return new AuthResponse<>(
                    false,
                    "Phone number is required.",
                    null
            );
        }

        String phone = request.getPhone().trim();

        if (userRepository.existsByPhone(phone)) {
            return new AuthResponse<>(
                    false,
                    "A user with this phone number already exists.",
                    null
            );
        }

        // ============================================================
        // 2. VALIDATE EMAIL
        // ============================================================

        String email = request.getEmail() == null
                ? null
                : request.getEmail().trim();

        if (email != null
                && !email.isBlank()
                && userRepository.existsByEmail(email)) {

            return new AuthResponse<>(
                    false,
                    "A user with this email already exists.",
                    null
            );
        }

        // ============================================================
        // 3. PREPARE NAME
        // ============================================================

        String fullName = request.getFullName() == null
                ? ""
                : request.getFullName().trim();

        if (fullName.isBlank()) {
            return new AuthResponse<>(
                    false,
                    "Full name is required.",
                    null
            );
        }

        String[] nameParts = fullName.split("\\s+", 2);

        String firstName = nameParts[0];

        String lastName = nameParts.length > 1
                ? nameParts[1]
                : "";

        // ============================================================
        // 4. GENERATE MEMBER NUMBER
        // ============================================================

        String memberNumber = generateMemberNumber();

        // ============================================================
        // 5. CREATE MEMBER
        // ============================================================

        Member member = Member.builder()
                .memberNumber(memberNumber)
                .firstName(firstName)
                .lastName(lastName)
                .phone(phone)
                .email(email)
                .build();

        member = memberRepository.save(member);

        // ============================================================
        // 6. CREATE USER AND LINK MEMBER
        // ============================================================

        User user = User.builder()
                .member(member)
                .username(fullName)
                .email(email)
                .phone(phone)
                .passwordHash(
                        passwordEncoder.encode(
                                UUID.randomUUID().toString()
                        )
                )
                .status(UserStatus.ACTIVE)
                .failedLoginAttempts(0)
                .build();

        user = userRepository.save(user);

        // ============================================================
        // 7. CREATE PHONE VERIFICATION OTP
        // ============================================================

        createOtp(user, "phone_verification");

        // ============================================================
        // 8. RESPONSE
        // ============================================================

        return new AuthResponse<>(
                true,
                "Registration successful. OTP sent for phone verification.",
                new AuthLookUpResponse(AuthStatus.NEW)
        );
    }

    private String generateMemberNumber() {

        String memberNumber;

        do {
            memberNumber = "MBR-" +
                    String.format(
                            "%06d",
                            RANDOM.nextInt(1_000_000)
                    );

        } while (memberRepository.existsByMemberNumber(memberNumber));

        return memberNumber;
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
    public AuthResponse<UserSessionWithGroupResponse> verifyOtp(
            VerifyOtpRequest request
    ) {

        // ============================================================
        // 1. DETERMINE OTP PURPOSE
        // ============================================================

        String purpose =
                request.getPurpose() == null
                        || request.getPurpose().isBlank()
                        ? "login"
                        : request.getPurpose().trim();

        // ============================================================
        // 2. FIND LATEST VALID OTP
        // ============================================================

        Optional<OTP> optionalOtp =
                otpRepository
                        .findTopByPhoneAndPurposeAndIsUsedFalseAndIsExpiredFalseOrderByIdDesc(
                                request.getPhone(),
                                purpose
                        );

        if (optionalOtp.isEmpty()) {

            return new AuthResponse<>(
                    false,
                    "Invalid or expired OTP.",
                    null
            );
        }

        OTP otp = optionalOtp.get();

        LocalDateTime now = LocalDateTime.now();

        // ============================================================
        // 3. CHECK EXPIRATION
        // ============================================================

        if (otp.getExpiresAt() == null
                || now.isAfter(otp.getExpiresAt())) {

            otp.setIsExpired(true);
            otpRepository.save(otp);

            return new AuthResponse<>(
                    false,
                    "Invalid or expired OTP.",
                    null
            );
        }

        // ============================================================
        // 4. CHECK ATTEMPTS
        // ============================================================

        if (otp.getAttempts() >= otp.getMaxAttempts()) {

            otp.setIsExpired(true);
            otpRepository.save(otp);

            return new AuthResponse<>(
                    false,
                    "Too many OTP attempts.",
                    null
            );
        }

        // ============================================================
        // 5. VERIFY CODE
        // ============================================================

        if (!otp.getCode().equals(request.getCode())) {

            otp.setAttempts(
                    otp.getAttempts() + 1
            );

            if (otp.getAttempts() >= otp.getMaxAttempts()) {
                otp.setIsExpired(true);
            }

            otpRepository.save(otp);

            return new AuthResponse<>(
                    false,
                    "Invalid OTP.",
                    null
            );
        }

        // ============================================================
        // 6. FIND USER
        // ============================================================

//        User user =
//                userRepository
//                        .findByPhone(request.getPhone())
//                        .orElse(null);

        User user = userRepository
                .findByPhoneWithMember(request.getPhone())
                .orElse(null);

        if (user == null) {

            return new AuthResponse<>(
                    false,
                    "User account not found.",
                    null
            );
        }

        // ============================================================
        // 7. CHECK USER STATUS
        // ============================================================

        boolean phoneVerification =
                "phone_verification".equalsIgnoreCase(
                        purpose
                );

        if (!phoneVerification
                && !UserStatus.ACTIVE.equals(user.getStatus())) {

            return new AuthResponse<>(
                    false,
                    "Your account has been disabled.",
                    null
            );
        }

        // ============================================================
        // 8. MARK OTP USED
        // ============================================================

        otp.setIsUsed(true);

        otpRepository.save(otp);

        // ============================================================
        // 9. ACTIVATE USER
        // ============================================================

        if (phoneVerification) {
            user.setStatus(UserStatus.ACTIVE);
        }

        user.setLastLoginAt(now);

        userRepository.save(user);

        // ============================================================
        // 10. USER SESSION
        // ============================================================

        UserSessionResponse userSession =
                new UserSessionResponse(
                        user.getId(),
                        user.getUsername(),
                        user.getEmail(),
                        user.getPhone(),
                        user.getStatus(),
                        user.getLastLoginAt()
                );

        // ============================================================
        // 11. FIND MEMBER
        // ============================================================

        List<UserGroupResponse> groups =
                new ArrayList<>();

        Member member = user.getMember();

        if (member != null
                && member.getId() != null) {

            Long memberId = member.getId();

            // ========================================================
            // 12. FIND MEMBERSHIPS
            // ========================================================

            List<GroupMember> memberships =
                    groupMemberRepository
                            .findActiveGroupsByMemberId(memberId);

            // ========================================================
            // 13. BUILD GROUP RESPONSE
            // ========================================================

            for (GroupMember membership : memberships) {

                VikobaGroup group =
                        membership.getGroup();

                if (group == null) {
                    continue;
                }

                // ====================================================
                // ORGANIZATION
                // ====================================================

                Organization organization =
                        group.getOrganization();

                if (organization == null) {
                    continue;
                }

                // ====================================================
                // GROUP RESPONSE
                // ====================================================

                VikobaGroupCreateResponse groupResponse =
                        new VikobaGroupCreateResponse(
                                organization.getId(),
                                group.getId(),
                                organization.getName(),
                                group.getName(),
                                group.getCode(),
                                group.getCurrency(),
                                group.getStartDate(),
                                group.getEndDate()
                        );

                // ====================================================
                // GROUP SETTINGS
                // ====================================================

                GroupSettingsRequest settingsResponse =
                        null;

                Optional<GroupSettings> optionalSettings =
                        groupSettingsRepository
                                .findByGroupId(group.getId());

                if (optionalSettings.isPresent()) {

                    GroupSettings settings =
                            optionalSettings.get();

                    settingsResponse =
                            new GroupSettingsRequest();

                    settingsResponse.setMinimumContribution(
                            settings.getMinimumContribution()
                    );

                    settingsResponse.setMaximumContribution(
                            settings.getMaximumContribution()
                    );

                    settingsResponse.setSharePrice(
                            settings.getSharePrice()
                    );

                    settingsResponse.setMaximumSharesPerMember(
                            settings.getMaximumSharesPerMember()
                    );

                    settingsResponse.setLoanMultiplier(
                            settings.getLoanMultiplier()
                    );

                    settingsResponse.setDefaultInterestRate(
                            settings.getDefaultInterestRate()
                    );

                    settingsResponse.setDefaultLoanDurationMonths(
                            settings.getDefaultLoanDurationMonths()
                    );

                    settingsResponse.setLatePaymentFine(
                            settings.getLatePaymentFine()
                    );
                }

                // ====================================================
                // SETTINGS CONFIGURED
                // ====================================================

                boolean settingsConfigured =
                        settingsResponse != null;

                // ====================================================
                // ADD USER GROUP
                // ====================================================

                groups.add(
                        new UserGroupResponse(
                                groupResponse,
                                settingsResponse,
                                settingsConfigured
                        )
                );
            }
        }

        // ============================================================
        // 14. BUILD SESSION RESPONSE
        // ============================================================

        UserSessionWithGroupResponse combined =
                new UserSessionWithGroupResponse(
                        userSession,
                        groups
                );

        // ============================================================
        // 15. BUILD AUTH RESPONSE
        // ============================================================

        AuthResponse<UserSessionWithGroupResponse> response =
                new AuthResponse<>(
                        true,
                        "Login successful.",
                        combined
                );

        // ============================================================
        // 16. GENERATE ACCESS TOKEN
        // ============================================================

        response.setToken(
                jwtService.generateAccessToken(
                        user.getPhone()
                )
        );

        // ============================================================
        // 17. GENERATE REFRESH TOKEN
        // ============================================================

        response.setRefreshToken(
                jwtService.generateRefreshToken(
                        user.getPhone()
                )
        );

        // ============================================================
        // 18. TOKEN EXPIRATION
        // ============================================================

        response.setExpired(
                String.valueOf(
                        jwtService.getExpirationTime()
                )
        );

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
