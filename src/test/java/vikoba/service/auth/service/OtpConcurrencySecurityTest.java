package vikoba.service.auth.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import vikoba.service.auth.dto.VerifyOtpRequest;
import vikoba.service.auth.entity.OTP;
import vikoba.service.auth.entity.User;
import vikoba.service.auth.repository.OTPRepository;
import vikoba.service.auth.repository.UserRepository;
import static org.junit.jupiter.api.Assertions.*;

/** Runs against the test H2 database, exercising the actual transaction/row locks. */
@SpringBootTest
class OtpConcurrencySecurityTest {
    @Autowired AuthService auth;
    @Autowired UserRepository users;
    @Autowired OTPRepository otps;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;

    private String fixture() {
        String phone = "test" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            User user = users.saveAndFlush(User.builder().phone(phone).username("OTP test")
                    .passwordHash("unused-test-password-hash").build());
            OTP otp = new OTP();
            otp.setPhone(phone);
            otp.setUser(user);
            otp.setCode(passwords.encode("123456"));
            otp.setPurpose("login");
            otp.setExpiresAt(LocalDateTime.now().plusMinutes(5));
            otps.saveAndFlush(otp);
        });
        return phone;
    }

    private List<Boolean> verifyConcurrently(String phone, String code, int count) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(count)) {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    VerifyOtpRequest request = new VerifyOtpRequest();
                    request.setPhone(phone);
                    request.setPurpose("login");
                    request.setCode(code);
                    return auth.verifyOtp(request).isStatus();
                }));
            }
            start.countDown();
            List<Boolean> results = new ArrayList<>();
            for (var future : futures) results.add(future.get(20, TimeUnit.SECONDS));
            return results;
        }
    }

    @Test void simultaneousCorrectCodesIssueExactlyOneSession() throws Exception {
        String phone = fixture();
        var results = verifyConcurrently(phone, "123456", 4);
        assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
        assertTrue(otps.findTopByPhoneOrderByIdDesc(phone).orElseThrow().getIsUsed());
    }

    @Test void simultaneousGuessesCannotLoseAttemptsOrBypassLockout() throws Exception {
        String phone = fixture();
        var results = verifyConcurrently(phone, "000000", 8);
        assertTrue(results.stream().noneMatch(Boolean::booleanValue));
        User user = users.findByPhone(phone).orElseThrow();
        assertEquals(5, user.getFailedLoginAttempts());
        assertTrue(user.getLockedUntil().isAfter(LocalDateTime.now()));
        assertEquals(5, otps.findTopByPhoneOrderByIdDesc(phone).orElseThrow().getAttempts());
    }
}
