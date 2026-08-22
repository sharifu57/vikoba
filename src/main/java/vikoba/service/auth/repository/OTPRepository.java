package vikoba.service.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vikoba.service.auth.entity.OTP;

import java.util.Optional;

public interface OTPRepository extends JpaRepository<OTP, Long> {
    Optional<OTP> findTopByPhoneAndPurposeAndIsUsedFalseAndIsExpiredFalseOrderByIdDesc(
            String phone, String purpose);
}