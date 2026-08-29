package vikoba.service.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vikoba.service.organization.dto.VikobaGroupCreateResponse;
import vikoba.service.organization.dto.GroupSettingsRequest;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserSessionWithGroupResponse {
    private UserSessionResponse user;
    private List<UserGroupResponse> groups;
}
