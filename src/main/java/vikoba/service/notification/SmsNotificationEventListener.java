package vikoba.service.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class SmsNotificationEventListener {
    private final SmsNotificationService smsNotificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void send(SmsNotificationRequestedEvent event) {
        if (!smsNotificationService.sendToPhone(event.phone(), event.recipientName(), event.message())) {
            log.warn("SMS notification was not accepted for {}", event.phone());
        }
    }
}
