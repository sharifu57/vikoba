package vikoba.service.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vikoba.service.organization.dto.GroupSettingsRequest;
import vikoba.service.organization.dto.VikobaGroupCreateResponse;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserGroupResponse {
    private VikobaGroupCreateResponse group;
    private GroupSettingsRequest settings;
    private boolean settingsConfigured;
}
