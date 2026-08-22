package vikoba.service.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vikoba.service.common.entity.BaseEntity;

import java.time.LocalDateTime;

@Getter
@Setter
@Table(name = "otp")
@Entity
public class OTP extends BaseEntity {
    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 10)
    private String code;

    @Column(nullable = false, length = 30)
    private String purpose = "login";   // login, reset_password, etc.

    @Column(nullable = false)
    private Integer attempts = 0;

    @Column(nullable = false)
    private Integer maxAttempts = 5;

    @Column(nullable = false)
    private Boolean isUsed = false;

    @Column(nullable = false)
    private Boolean isExpired = false;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
}
