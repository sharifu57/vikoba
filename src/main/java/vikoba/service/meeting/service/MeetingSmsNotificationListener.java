package vikoba.service.meeting.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vikoba.service.meeting.event.MeetingCreatedNotificationEvent;
import vikoba.service.notification.SmsNotificationService;

import java.time.format.DateTimeFormatter;
import java.util.HashSet;

@Component
@RequiredArgsConstructor
@Slf4j
public class MeetingSmsNotificationListener {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final SmsNotificationService smsNotificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyMembers(MeetingCreatedNotificationEvent event) {
        String place = "ONLINE".equalsIgnoreCase(event.meetingMode())
                ? event.meetingLink()
                : event.location();
        String message = "VIKOBA360: Mkutano mpya wa " + event.groupName() + ": " + event.title()
                + ", tarehe " + DATE.format(event.meetingDate())
                + " saa " + TIME.format(event.startTime())
                + (place == null || place.isBlank() ? "." : ", mahali/link: " + place + ".")
                + " Fungua VIKOBA360 kuona agenda na maelezo zaidi.";

        int sent = 0;
        var processedPhones = new HashSet<String>();
        for (var recipient : event.recipients()) {
            if (recipient.phone() == null || recipient.phone().isBlank()
                    || !processedPhones.add(recipient.phone().trim())) {
                continue;
            }
            try {
                if (smsNotificationService.sendToPhone(recipient.phone(), recipient.name(), message)) {
                    sent++;
                }
            } catch (RuntimeException error) {
                log.warn("Unable to send meeting {} notification to {}: {}", event.meetingId(),
                        recipient.phone(), error.getMessage());
            }
        }
        log.info("Meeting {} SMS notification completed: {}/{} recipients accepted for delivery",
                event.meetingId(), sent, processedPhones.size());
    }
}
