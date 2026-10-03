package vikoba.service.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vikoba.service.auth.entity.OTP;

import java.util.Optional;

public interface OTPRepository extends JpaRepository<OTP, Long> {
    Optional<OTP> findTopByPhoneOrderByIdDesc(String phone);

    @Modifying
    @Query("UPDATE OTP o SET o.isExpired = true WHERE o.phone = :phone AND o.isUsed = false AND o.isExpired = false")
    void expireUnusedByPhone(@Param("phone") String phone);

    Optional<OTP> findTopByPhoneAndPurposeAndIsUsedFalseAndIsExpiredFalseOrderByIdDesc(
            String phone, String purpose);
}
