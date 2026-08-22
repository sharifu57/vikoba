package vikoba.service.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import vikoba.service.common.entity.BaseEntity;
import vikoba.service.common.enums.UserStatus;
import vikoba.service.organization.entity.Member;

import java.time.LocalDateTime;

@Entity
@Table(name = "users", indexes = {
                @Index(name = "idx_user_phone", columnList = "phone"),
                @Index(name = "idx_user_email", columnList = "email")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

        @OneToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "member_id", unique = true, foreignKey = @ForeignKey(name = "fk_user_member"))
        private Member member;

        @Column(length = 100)
        private String username;

        @Column(length = 150)
        private String email;

        @Column(length = 30, nullable = false, unique = true)
        private String phone;

        @Column(name = "password_hash", nullable = false)
        private String passwordHash;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 20)
        @Builder.Default
        private UserStatus status = UserStatus.ACTIVE;

        @Column(name = "last_login_at")
        private LocalDateTime lastLoginAt;

        @Column(name = "failed_login_attempts", nullable = false)
        @Builder.Default
        private Integer failedLoginAttempts = 0;

        @Column(name = "locked_until")
        private LocalDateTime lockedUntil;
}
