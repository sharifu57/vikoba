package vikoba.service.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vikoba.service.common.enums.AuthStatus;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AuthLookUpResponse {
    private AuthStatus status;
}
