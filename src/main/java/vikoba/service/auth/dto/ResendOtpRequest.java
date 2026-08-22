package vikoba.service.auth.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResendOtpRequest {
    private String phone;
    private String purpose;
}
