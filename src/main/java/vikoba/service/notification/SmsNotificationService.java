package vikoba.service.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
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
    private final PlatformTransactionManager transactionManager;
    private final WhatsAppOtpService whatsAppOtpService;

    public boolean send(String customerPhone, String message) {
        if (customerPhone == null || customerPhone.trim().isEmpty()) {
            log.warn("SMS not sent: phone number is empty");
            return false;
        }

        if (message == null || message.trim().isEmpty()) {
            log.warn("SMS not sent: message is empty");
            return false;
        }

        Notification notification = inNewTransaction(() -> {
            // Resolve the user inside the notification transaction. During
            // registration the new user is still uncommitted in the caller's
            // transaction and therefore must not be attached to this row.
            User user = userRepository.findByPhone(customerPhone).orElse(null);
            return notificationRepository.save(Notification.builder()
                    .user(user)
                    .title("Vikoba notification")
                    .message(isVerificationMessage(message) ? "Verification code requested; code not retained." : message)
                    .type(NotificationType.INFO)
                    .channel("SMS")
                    .deliveryStatus("PENDING")
                    .recipientPhone(customerPhone)
                    .build());
        });

        try {
            SmsJob job = new SmsJob(notification.getId(), customerPhone, message, 0);
            String payload = objectMapper.writeValueAsString(job);

            kafkaTemplate.send(TOPIC, notification.getId().toString(), payload).whenComplete((result, throwable) -> {
                        if (throwable != null) {
                            boolean fallbackSent = fallbackOtpToWhatsApp(notification.getId(), customerPhone, message);
                            if (!fallbackSent) {
                                updateDelivery(notification.getId(), "FAILED", isVerificationMessage(message)
                                        ? "Unable to enqueue verification SMS"
                                        : "Unable to enqueue SMS: " + throwable.getMessage());
                            }
                            if (isVerificationMessage(message)) {
                                log.warn("Unable to enqueue verification SMS notification {}", notification.getId());
                            } else {
                                log.warn("Unable to enqueue SMS for {}: {}", customerPhone, throwable.getMessage(), throwable);
                            }
                        } else {
                            log.info("Queued SMS notification {} for {}", notification.getId(), customerPhone);
                        }
                    });

            return true;
        } catch (Exception e) {
            boolean fallbackSent = fallbackOtpToWhatsApp(notification.getId(), customerPhone, message);
            if (!fallbackSent) {
                updateDelivery(notification.getId(), "FAILED", isVerificationMessage(message)
                        ? "Unable to enqueue verification SMS" : "Unable to enqueue SMS: " + e.getMessage());
            }
            if (isVerificationMessage(message)) {
                log.warn("Unable to enqueue verification SMS notification {}", notification.getId());
            } else {
                log.warn("Unable to enqueue SMS for {}: {}", customerPhone, e.getMessage(), e);
            }
            return fallbackSent;
        }
    }

    /** Dispatch authentication SMS immediately so OTP delivery does not depend on Kafka availability. */
    public boolean sendOtp(String customerPhone, String message) {
        if (customerPhone == null || customerPhone.isBlank() || !isVerificationMessage(message)) {
            log.warn("Verification SMS not sent: invalid phone or message");
            return false;
        }

        Notification notification = inNewTransaction(() -> {
            User user = userRepository.findByPhone(customerPhone).orElse(null);
            return notificationRepository.save(Notification.builder()
                    .user(user)
                    .title("Vikoba notification")
                    .message("Verification code requested; code not retained.")
                    .type(NotificationType.INFO)
                    .channel("SMS")
                    .deliveryStatus("PENDING")
                    .recipientPhone(customerPhone)
                    .build());
        });

        log.info("Dispatching verification SMS directly to the SMS provider");
        deliver(new SmsJob(notification.getId(), customerPhone, message, 0));
        String status = inNewTransaction(() -> notificationRepository.findById(notification.getId())
                .map(Notification::getDeliveryStatus).orElse("MISSING"));
        if ("SENT".equals(status)) {
            log.info("SMS provider accepted verification notification {}", notification.getId());
        } else if ("RETRYING".equals(status)) {
            log.warn("SMS provider did not accept verification message yet; retry is queued");
        } else {
            log.error("SMS and WhatsApp delivery failed for verification notification {}", notification.getId());
        }
        return "SENT".equals(status) || "RETRYING".equals(status);
    }

    @KafkaListener(topics = TOPIC, groupId = "${spring.kafka.consumer.group-id:vikoba360-sms}")
    public void consume(String payload) {
        try {
            SmsJob job = objectMapper.readValue(payload, SmsJob.class);
            deliver(job);
        } catch (Exception e) {
            log.error("Invalid SMS queue message ({})", e.getClass().getSimpleName());
        }
    }

    @KafkaListener(topics = RETRY_TOPIC, groupId = "${spring.kafka.consumer.group-id:vikoba360-sms-retry}")
    public void consumeRetry(String payload) {
        try {
            SmsJob job = objectMapper.readValue(payload, SmsJob.class);
            deliver(job);
        } catch (Exception e) {
            log.error("Invalid SMS retry message ({})", e.getClass().getSimpleName());
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
            log.error("Invalid SMS DLQ message ({})", e.getClass().getSimpleName());
        }
    }

    private void deliver(SmsJob job) {
        Notification notification = notificationRepository.findById(job.notificationId()).orElse(null);
        if (notification == null) {
            return;
        }

        String senderIdentity = systemSettingService.get("sms.sender.id", dbEnv.senderId);
        if (dbEnv.smsApiKey == null || dbEnv.smsApiKey.isBlank()) {
            log.warn("SMS OTP dispatch skipped: SMS API key is not configured");
            if (!fallbackOtpToWhatsApp(job.notificationId(), job.phone(), job.message())) {
                notification.setDeliveryStatus("FAILED");
                notification.setProviderResponse("SMS provider secret is not configured");
                notificationRepository.save(notification);
            }
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

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    systemSettingService.get("sms.dispatch.url", dbEnv.smsUrl),
                    HttpMethod.POST,
                    request,
                    new ParameterizedTypeReference<>() {});

            if (response.getStatusCode().is2xxSuccessful()) {
                notification.setDeliveryStatus("SENT");
                notification.setProviderResponse(isVerificationMessage(job.message())
                        ? "Verification SMS accepted by provider." : String.valueOf(response.getBody()));
                notification.setSentAt(LocalDateTime.now());
                notificationRepository.save(notification);
                if (isVerificationMessage(job.message())) {
                    log.info("SMS provider accepted verification notification {}", job.notificationId());
                }
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
        if (isVerificationMessage(job.message())) {
            log.warn("SMS OTP provider dispatch failed ({})", safeFailureSummary(e));
        }

        if (job.attempt() < MAX_RETRIES) {
            notification.setDeliveryStatus("RETRYING");
            notification.setProviderResponse(
                    "Retrying SMS delivery (attempt " + nextAttempt + "/" + MAX_RETRIES + "): " + failureReason(job, e));
            notificationRepository.save(notification);

            try {
                kafkaTemplate.send(RETRY_TOPIC, job.notificationId().toString(), objectMapper.writeValueAsString(
                        new SmsJob(job.notificationId(), job.phone(), job.message(), nextAttempt)))
                        .whenComplete((result, throwable) -> {
                            if (throwable != null) {
                                if (fallbackOtpToWhatsApp(job.notificationId(), job.phone(), job.message())) {
                                    return;
                                }
                                if (isVerificationMessage(job.message())) {
                                    updateDelivery(job.notificationId(), "FAILED", "Unable to enqueue verification SMS retry");
                                    log.warn("Failed to enqueue verification SMS retry for notification {}", job.notificationId());
                                } else {
                                    updateDelivery(job.notificationId(), "FAILED", "SMS retry queue failed");
                                    log.warn("Failed to enqueue SMS retry for notification {} to {}", job.notificationId(),
                                            job.phone(), throwable);
                                }
                            }
                        });
            } catch (Exception queueException) {
                if (fallbackOtpToWhatsApp(job.notificationId(), job.phone(), job.message())) {
                    return;
                }
                notification.setDeliveryStatus("FAILED");
                notification.setProviderResponse(isVerificationMessage(job.message())
                        ? "Verification SMS retry queue failed" : "Retry queue failed: " + queueException.getMessage());
                notificationRepository.save(notification);
                if (isVerificationMessage(job.message())) {
                    log.warn("Verification SMS retry queue failure for notification {}", job.notificationId());
                } else {
                    log.warn("Retry queue failure for notification {} to {}: {}", job.notificationId(), job.phone(),
                            queueException.getMessage(), queueException);
                }
            }
            return;
        }

        if (fallbackOtpToWhatsApp(job.notificationId(), job.phone(), job.message())) {
            log.info("SMS delivery failed; WhatsApp fallback accepted for notification {}", job.notificationId());
            return;
        }

        notification.setDeliveryStatus("FAILED");
        notification.setProviderResponse("SMS delivery failed after " + MAX_RETRIES + " retries: " + failureReason(job, e));
        notificationRepository.save(notification);

        try {
            SmsJob failedJob = isVerificationMessage(job.message())
                    ? new SmsJob(job.notificationId(), job.phone(), "Verification code removed.", job.attempt()) : job;
            kafkaTemplate.send(DLQ_TOPIC, job.notificationId().toString(), objectMapper.writeValueAsString(failedJob))
                    .whenComplete((result, throwable) -> {
                        if (throwable != null) {
                            log.warn("Failed to send SMS job {} to DLQ", job.notificationId(), throwable);
                        }
                    });
        } catch (Exception queueException) {
            log.warn("Unable to enqueue DLQ SMS for notification {}: {}", job.notificationId(),
                    queueException.getMessage(), queueException);
        }

        if (isVerificationMessage(job.message())) {
            log.warn("Verification SMS delivery failed for notification {} after {} retries", job.notificationId(), MAX_RETRIES);
        } else {
            log.warn("Failed to send SMS to {} after {} retries: {}", job.phone(), MAX_RETRIES, e.getMessage(), e);
        }
    }

    private boolean isVerificationMessage(String message) {
        return message != null && message.startsWith("VIKOBA360 verification code: ");
    }

    private boolean fallbackOtpToWhatsApp(Long notificationId, String phone, String message) {
        if (!isVerificationMessage(message)) {
            return false;
        }
        String prefix = "VIKOBA360 verification code: ";
        String code = message.substring(prefix.length()).split("\\R", 2)[0].trim();
        if (code.isBlank() || !whatsAppOtpService.send(phone, code)) {
            return false;
        }
        inNewTransaction(() -> {
            notificationRepository.findById(notificationId).ifPresent(notification -> {
                notification.setChannel("WHATSAPP");
                notification.setDeliveryStatus("SENT");
                notification.setProviderResponse("SMS failed; WhatsApp accepted the verification message.");
                notification.setSentAt(LocalDateTime.now());
                notificationRepository.save(notification);
            });
            return null;
        });
        return true;
    }

    private void updateDelivery(Long notificationId, String status, String providerResponse) {
        inNewTransaction(() -> {
            notificationRepository.findById(notificationId).ifPresent(notification -> {
                notification.setDeliveryStatus(status);
                notification.setProviderResponse(providerResponse);
                notificationRepository.save(notification);
            });
            return null;
        });
    }

    private <T> T inNewTransaction(java.util.function.Supplier<T> operation) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction.execute(status -> operation.get());
    }

    private String failureReason(SmsJob job, Exception exception) {
        return isVerificationMessage(job.message()) ? "SMS provider request failed" : exception.getMessage();
    }

    private String safeFailureSummary(Exception exception) {
        if (exception instanceof org.springframework.web.client.RestClientResponseException responseException) {
            return "HTTP " + responseException.getStatusCode().value();
        }
        return exception.getClass().getSimpleName();
    }

    private record SmsJob(Long notificationId, String phone, String message, int attempt) {
    }
}
