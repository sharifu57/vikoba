package vikoba.service.organization.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GroupWithSettingsResponse {
    private VikobaGroupCreateResponse group;
    private GroupSettingsRequest settings;
    private boolean settingsConfigured;

    public GroupWithSettingsResponse(VikobaGroupCreateResponse group, GroupSettingsRequest settings) {
        this.group = group;
        this.settings = settings;
        this.settingsConfigured = settings != null;
    }
}
