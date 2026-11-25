package vikoba.service.notification;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import vikoba.service.auth.entity.User;
import vikoba.service.auth.repository.UserRepository;
import vikoba.service.notification.WhatsAppOtpService;
import vikoba.service.common.entity.Notification;
import vikoba.service.common.repository.NotificationRepository;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class SmsSecretSecurityTest {
    @Mock UserRepository users;
    @Mock NotificationRepository notifications;
    @Mock KafkaTemplate<String, String> kafka;
    @Mock PlatformTransactionManager transactionManager;
    @Mock WhatsAppOtpService whatsAppOtpService;
    @Spy ObjectMapper mapper = new ObjectMapper();
    @InjectMocks SmsNotificationService service;

    @BeforeEach void setUp() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenAnswer(invocation -> new SimpleTransactionStatus());
    }

    @Test void otpNotificationIsRedactedWhileDeliveryJobRetainsNecessaryCode() {
        when(users.findByPhone("255700000001")).thenReturn(Optional.of(User.builder().build()));
        when(notifications.save(any())).thenAnswer(invocation -> {
            Notification saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        assertTrue(service.send("255700000001", "VIKOBA360 verification code: 123456\nDo not share."));
        var notification = org.mockito.ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(notification.capture());
        assertFalse(notification.getValue().getMessage().contains("123456"));
        verify(kafka).send(eq("vikoba.sms"), eq("1"), contains("123456"));
    }

    @Test void failedQueueDoesNotPersistAnExceptionContainingTheVerificationCode() {
        when(users.findByPhone("255700000001")).thenReturn(Optional.of(User.builder().build()));
        when(notifications.save(any())).thenAnswer(invocation -> {
            Notification saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        Notification persisted = Notification.builder().deliveryStatus("PENDING").build();
        persisted.setId(1L);
        when(notifications.findById(1L)).thenReturn(Optional.of(persisted));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(
                CompletableFuture.failedFuture(new IllegalStateException("payload: 123456")));
        assertTrue(service.send("255700000001", "VIKOBA360 verification code: 123456\nDo not share."));
        var notification = org.mockito.ArgumentCaptor.forClass(Notification.class);
        verify(notifications, times(2)).save(notification.capture());
        assertEquals("FAILED", notification.getValue().getDeliveryStatus());
        assertFalse(notification.getValue().getProviderResponse().contains("123456"));
    }
}
