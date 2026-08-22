package vikoba.service.auth.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerifyOtpRequest {
    private String phone;
    private String code;
    private String purpose;
}