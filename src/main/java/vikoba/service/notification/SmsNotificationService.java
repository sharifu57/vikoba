package vikoba.service.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import vikoba.service.auth.entity.User;
import vikoba.service.auth.repository.UserRepository;
import vikoba.service.common.entity.Notification;
import vikoba.service.common.enums.NotificationType;
import vikoba.service.common.repository.NotificationRepository;
import vikoba.service.common.service.SystemSettingService;
import vikoba.service.config.SystemEnv;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmsNotificationService {
    private static final String TOPIC = "vikoba.sms";
    private static final String RETRY_TOPIC = "vikoba.sms.retry";
    private static final String DLQ_TOPIC = "vikoba.sms.dlq";
    private static final int MAX_RETRIES = 3;

    private final RestTemplate restTemplate;
    private final SystemEnv dbEnv;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SystemSettingService systemSettingService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public boolean send(String customerPhone, String message) {
        if (customerPhone == null || customerPhone.trim().isEmpty()) {
            log.warn("SMS not sent: phone number is empty");
            return false;
        }

        if (message == null || message.trim().isEmpty()) {
            log.warn("SMS not sent: message is empty");
            return false;
        }

        User user = userRepository.findByPhone(customerPhone).orElse(null);
        if (user == null) {
            log.warn("SMS not persisted/sent because no user exists for phone {}", customerPhone);
            return false;
        }

        Notification notification = notificationRepository.save(Notification.builder()
                .user(user)
                .title("Vikoba notification")
                .message(message)
                .type(NotificationType.INFO)
                .channel("SMS")
                .deliveryStatus("PENDING")
                .recipientPhone(customerPhone)
                .build());

        try {
            SmsJob job = new SmsJob(notification.getId(), customerPhone, message, 0);
            String payload = objectMapper.writeValueAsString(job);

            kafkaTemplate.send(TOPIC, notification.getId().toString(), payload)
                    .whenComplete((result, throwable) -> {
                        if (throwable != null) {
                            notification.setDeliveryStatus("FAILED");
                            notification.setProviderResponse("Unable to enqueue SMS: " + throwable.getMessage());
                            notificationRepository.save(notification);
                            log.warn("Unable to enqueue SMS for {}: {}", customerPhone, throwable.getMessage(),
                                    throwable);
                        } else {
                            log.info("Queued SMS notification {} for {}", notification.getId(), customerPhone);
                        }
                    });

            return true;
        } catch (Exception e) {
            notification.setDeliveryStatus("FAILED");
            notification.setProviderResponse("Unable to enqueue SMS: " + e.getMessage());
            notificationRepository.save(notification);
            log.warn("Unable to enqueue SMS for {}: {}", customerPhone, e.getMessage(), e);
            return false;
        }
    }

    @KafkaListener(topics = TOPIC, groupId = "${spring.kafka.consumer.group-id:vikoba360-sms}")
    public void consume(String payload) {
        try {
            SmsJob job = objectMapper.readValue(payload, SmsJob.class);
            deliver(job);
        } catch (Exception e) {
            log.error("Invalid SMS queue message", e);
        }
    }

    @KafkaListener(topics = RETRY_TOPIC, groupId = "${spring.kafka.consumer.group-id:vikoba360-sms-retry}")
    public void consumeRetry(String payload) {
        try {
            SmsJob job = objectMapper.readValue(payload, SmsJob.class);
            deliver(job);
        } catch (Exception e) {
            log.error("Invalid SMS retry message", e);
        }
    }

    @KafkaListener(topics = DLQ_TOPIC, groupId = "${spring.kafka.consumer.group-id:vikoba360-sms-dlq}")
    public void consumeDlq(String payload) {
        try {
            SmsJob job = objectMapper.readValue(payload, SmsJob.class);
            Notification notification = notificationRepository.findById(job.notificationId()).orElse(null);
            if (notification != null) {
                notification.setDeliveryStatus("FAILED");
                notification.setProviderResponse("SMS delivery exhausted after " + MAX_RETRIES + " retries");
                notificationRepository.save(notification);
            }
            log.warn("SMS delivery moved to DLQ after retries for notification {} to {}", job.notificationId(),
                    job.phone());
        } catch (Exception e) {
            log.error("Invalid SMS DLQ message", e);
        }
    }

    private void deliver(SmsJob job) {
        Notification notification = notificationRepository.findById(job.notificationId()).orElse(null);
        if (notification == null) {
            return;
        }

        String senderIdentity = systemSettingService.get("sms.sender.id", dbEnv.senderId);
        if (dbEnv.smsApiKey == null || dbEnv.smsApiKey.isBlank()) {
            notification.setDeliveryStatus("FAILED");
            notification.setProviderResponse("SMS provider secret is not configured");
            notificationRepository.save(notification);
            return;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("message", job.message());
            payload.put("senderIdentity", senderIdentity);
            payload.put("callbackUrl", "");
            payload.put("recipients", List.of(Map.of(
                    "phoneNumber", job.phone(),
                    "name", "")));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("apiKey", dbEnv.smsApiKey);
            headers.set("senderIdentity", senderIdentity);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    systemSettingService.get("sms.dispatch.url", dbEnv.smsUrl),
                    HttpMethod.POST,
                    request,
                    Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                notification.setDeliveryStatus("SENT");
                notification.setProviderResponse(String.valueOf(response.getBody()));
                notification.setSentAt(LocalDateTime.now());
                notificationRepository.save(notification);
                return;
            }

            throw new IllegalStateException(
                    "Provider returned status " + response.getStatusCode() + ": " + response.getBody());
        } catch (Exception e) {
            handleDeliveryFailure(job, notification, e);
        }
    }

    private void handleDeliveryFailure(SmsJob job, Notification notification, Exception e) {
        int nextAttempt = job.attempt() + 1;

        if (job.attempt() < MAX_RETRIES) {
            notification.setDeliveryStatus("RETRYING");
            notification.setProviderResponse(
                    "Retrying SMS delivery (attempt " + nextAttempt + "/" + MAX_RETRIES + "): " + e.getMessage());
            notificationRepository.save(notification);

            try {
                kafkaTemplate.send(RETRY_TOPIC, job.notificationId().toString(), objectMapper.writeValueAsString(
                        new SmsJob(job.notificationId(), job.phone(), job.message(), nextAttempt)))
                        .whenComplete((result, throwable) -> {
                            if (throwable != null) {
                                log.warn("Failed to enqueue SMS retry for notification {} to {}", job.notificationId(),
                                        job.phone(), throwable);
                            }
                        });
            } catch (Exception queueException) {
                notification.setDeliveryStatus("FAILED");
                notification.setProviderResponse("Retry queue failed: " + queueException.getMessage());
                notificationRepository.save(notification);
                log.warn("Retry queue failure for notification {} to {}: {}", job.notificationId(), job.phone(),
                        queueException.getMessage(), queueException);
            }
            return;
        }

        notification.setDeliveryStatus("FAILED");
        notification.setProviderResponse("SMS delivery failed after " + MAX_RETRIES + " retries: " + e.getMessage());
        notificationRepository.save(notification);

        try {
            kafkaTemplate.send(DLQ_TOPIC, job.notificationId().toString(), objectMapper.writeValueAsString(job))
                    .whenComplete((result, throwable) -> {
                        if (throwable != null) {
                            log.warn("Failed to send SMS job {} to DLQ", job.notificationId(), throwable);
                        }
                    });
        } catch (Exception queueException) {
            log.warn("Unable to enqueue DLQ SMS for notification {}: {}", job.notificationId(),
                    queueException.getMessage(), queueException);
        }

        log.warn("Failed to send SMS to {} after {} retries: {}", job.phone(), MAX_RETRIES, e.getMessage(), e);
    }

    private record SmsJob(Long notificationId, String phone, String message, int attempt) {
    }
}
