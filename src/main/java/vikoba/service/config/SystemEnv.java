package vikoba.service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SystemEnv {
    @Value("${http.secure.value:-1}")
    private String httpSecureValue;

    @Value("${sms.secret.key}")
    public String smsApiKey;

    @Value("${sms.sender.id}")
    public String senderId;
}
