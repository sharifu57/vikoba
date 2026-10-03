package vikoba.service.auth.service;

import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import vikoba.service.auth.dto.ResendOtpRequest;
import vikoba.service.auth.dto.VerifyOtpRequest;
import vikoba.service.auth.entity.OTP;
import vikoba.service.auth.entity.User;
import vikoba.service.auth.repository.*;
import vikoba.service.common.enums.UserStatus;
import vikoba.service.config.JwtService;
import vikoba.service.notification.SmsNotificationService;
import vikoba.service.organization.repository.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthSecurityTest {
    @Mock UserRepository users;
    @Mock OTPRepository otps;
    @Mock MemberRepository members;
    @Mock GroupMemberRepository memberships;
    @Mock JwtService jwt;
    @Mock GroupSettingsRepository settings;
    @Mock PasswordEncoder passwords;
    @Mock SmsNotificationService sms;
    @Mock MemberRoleRepository memberRoles;
    @Mock RoleRepository roles;
    @Mock MemberPermissionRepository permissions;
    @InjectMocks AuthService service;
    User user;
    VerifyOtpRequest verify;
    ResendOtpRequest resend;

    @BeforeEach void setUp() {
        user = User.builder().phone("255700000001").username("Test").status(UserStatus.ACTIVE).build();
        verify = new VerifyOtpRequest();
        verify.setPhone(user.getPhone());
        verify.setCode("123456");
        verify.setPurpose("login");
        resend = new ResendOtpRequest();
        resend.setPhone(user.getPhone());
        resend.setPurpose("login");
    }

    private OTP code() {
        OTP otp = new OTP();
        otp.setCode("stored-otp-hash");
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        return otp;
    }

    private void stubOtp(OTP otp) {
        when(users.findByPhoneForUpdate(user.getPhone())).thenReturn(Optional.of(user));
        when(otps.findTopByPhoneAndPurposeAndIsUsedFalseAndIsExpiredFalseOrderByIdDesc(user.getPhone(), "login"))
                .thenReturn(Optional.of(otp));
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"DISABLED", "SUSPENDED", "LOCKED"})
    void inactiveAccountsCannotVerifyOrReactivateUsingPhoneVerification(UserStatus status) {
        user.setStatus(status);
        verify.setPurpose("phone_verification");
        when(users.findByPhoneForUpdate(user.getPhone())).thenReturn(Optional.of(user));
        assertFalse(service.verifyOtp(verify).isStatus());
        assertEquals(status, user.getStatus());
        verifyNoInteractions(otps, jwt);
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"DISABLED", "SUSPENDED", "LOCKED"})
    void inactiveAccountsCannotRefresh(UserStatus status) {
        user.setStatus(status);
        when(jwt.isRefreshTokenValid("refresh")).thenReturn(true);
        when(jwt.isRefreshToken("refresh")).thenReturn(true);
        when(jwt.extractUsername("refresh")).thenReturn(user.getPhone());
        when(users.findByPhone(user.getPhone())).thenReturn(Optional.of(user));
        assertFalse(service.refresh("refresh").isStatus());
        verify(jwt, never()).generateAccessToken(anyString());
    }

    @Test void resendingTooSoonReturns429WithoutIssuingOrSendingCode() {
        when(users.findByPhoneForUpdate(user.getPhone())).thenReturn(Optional.of(user));
        when(otps.findTopByPhoneOrderByIdDesc(user.getPhone())).thenReturn(Optional.of(code()));
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.resendOtp(resend));
        assertEquals(429, error.getStatusCode().value());
        verify(otps, never()).save(any());
        verifyNoInteractions(sms);
    }

    @Test void repeatedLookupContinuesWithExistingCodeWithoutSendingSms() {
        stubOtp(code());
        var login = new vikoba.service.auth.dto.LoginRequest();
        login.setPhone(user.getPhone());
        assertTrue(service.lookUp(login).isStatus());
        verify(otps, never()).save(any());
        verify(otps, never()).expireUnusedByPhone(anyString());
        verifyNoInteractions(sms, jwt);
    }

    @Test void consumedCodeDoesNotBlockNewLogin() {
        when(users.findByPhoneForUpdate(user.getPhone())).thenReturn(Optional.of(user));
        OTP previous = code(); previous.setIsUsed(true);
        when(otps.findTopByPhoneOrderByIdDesc(user.getPhone())).thenReturn(Optional.of(previous));
        var login = new vikoba.service.auth.dto.LoginRequest(); login.setPhone(user.getPhone());
        assertTrue(service.lookUp(login).isStatus());
        verify(otps).save(any());
        verify(sms).send(eq(user.getPhone()), anyString());
    }

    @Test void databaseCreationTimestampCannotExtendSmsCooldown() {
        when(users.findByPhoneForUpdate(user.getPhone())).thenReturn(Optional.of(user));
        OTP previous = code();
        previous.setCreatedAt(LocalDateTime.now().plusHours(3));
        previous.setExpiresAt(LocalDateTime.now().plusMinutes(3));
        when(otps.findTopByPhoneOrderByIdDesc(user.getPhone())).thenReturn(Optional.of(previous));
        assertTrue(service.resendOtp(resend).isStatus());
        verify(sms).send(eq(user.getPhone()), anyString());
    }

    @Test void resendingInvalidatesOldCodesAcrossPurposes() {
        when(users.findByPhoneForUpdate(user.getPhone())).thenReturn(Optional.of(user));
        OTP old = code();
        old.setCreatedAt(LocalDateTime.now().minusMinutes(2));
        old.setExpiresAt(LocalDateTime.now().plusMinutes(3));
        when(otps.findTopByPhoneOrderByIdDesc(user.getPhone())).thenReturn(Optional.of(old));
        assertTrue(service.resendOtp(resend).isStatus());
        var ordered = inOrder(otps, sms);
        ordered.verify(otps).findTopByPhoneOrderByIdDesc(user.getPhone());
        ordered.verify(otps).expireUnusedByPhone(user.getPhone());
        ordered.verify(otps).save(any(OTP.class));
        ordered.verify(sms).send(eq(user.getPhone()), anyString());
    }

    @Test void arbitraryOtpPurposesAreRejected() {
        resend.setPurpose("reset_password");
        assertThrows(ResponseStatusException.class, () -> service.resendOtp(resend));
        verifyNoInteractions(users, otps, sms);
    }

    @Test void fifthWrongCodeLocksAccountEvenWhenPerCodeCounterWasReset() {
        user.setFailedLoginAttempts(4);
        OTP otp = code();
        stubOtp(otp);
        verify.setCode("000000");
        assertFalse(service.verifyOtp(verify).isStatus());
        assertEquals(5, user.getFailedLoginAttempts());
        assertTrue(user.getLockedUntil().isAfter(LocalDateTime.now().plusMinutes(14)));
        verify(users).save(user);
        verifyNoInteractions(jwt);
    }

    @Test void successfulVerificationConsumesCodeAndClearsFailureCounter() {
        user.setFailedLoginAttempts(2);
        OTP otp = code();
        stubOtp(otp);
        when(passwords.matches("123456", "stored-otp-hash")).thenReturn(true);
        assertTrue(service.verifyOtp(verify).isStatus());
        assertTrue(otp.getIsUsed());
        assertEquals(0, user.getFailedLoginAttempts());
        verify(otps).save(otp);
        verify(jwt).generateAccessToken(user.getPhone());
    }

    @Test void temporarilyLockedAccountCannotRequestAnotherCode() {
        user.setLockedUntil(LocalDateTime.now().plusMinutes(15));
        when(users.findByPhoneForUpdate(user.getPhone())).thenReturn(Optional.of(user));
        assertFalse(service.resendOtp(resend).isStatus());
        verifyNoInteractions(otps, sms);
    }

    @Test void issuingCodeStoresOnlyItsHashAndSendsTheOriginalToSms() {
        when(users.findByPhoneForUpdate(user.getPhone())).thenReturn(Optional.of(user));
        when(passwords.encode(anyString())).thenReturn("stored-otp-hash");
        assertTrue(service.resendOtp(resend).isStatus());
        var saved = org.mockito.ArgumentCaptor.forClass(OTP.class);
        verify(otps).save(saved.capture());
        assertEquals("stored-otp-hash", saved.getValue().getCode());
        verify(sms).send(eq(user.getPhone()), argThat(message ->
                message.matches("(?s)VIKOBA360 verification code: [0-9]{6}.*")
                && !message.contains("stored-otp-hash")));
    }
}
