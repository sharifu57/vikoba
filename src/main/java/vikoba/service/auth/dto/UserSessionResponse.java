package vikoba.service.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vikoba.service.common.enums.UserStatus;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserSessionResponse {
    private Long id;
    private String username;
    private String email;
    private String phone;
    private UserStatus status;
    private LocalDateTime lastLoginAt;
}
