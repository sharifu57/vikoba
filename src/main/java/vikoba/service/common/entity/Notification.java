package vikoba.service.common.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.*;
import vikoba.service.auth.entity.User;
import vikoba.service.common.enums.NotificationType;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "notifications",
        indexes = {
                @Index(name = "idx_notification_user", columnList = "user_id"),
                @Index(name = "idx_notification_read", columnList = "is_read")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification extends BaseEntity{
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationType type;

    @Column(name = "reference_type", length = 100)
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean read = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    /** Delivery channel, for example IN_APP or SMS. */
    @Column(length = 20)
    @Builder.Default
    private String channel = "IN_APP";

    /** PENDING, SENT or FAILED. */
    @Column(name = "delivery_status", length = 20)
    @Builder.Default
    private String deliveryStatus = "SENT";

    @Column(name = "recipient_phone", length = 30)
    private String recipientPhone;

    @Column(name = "provider_response", columnDefinition = "TEXT")
    private String providerResponse;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;
}
