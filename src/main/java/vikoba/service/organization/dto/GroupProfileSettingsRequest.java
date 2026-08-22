package vikoba.service.organization.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GroupProfileSettingsRequest {
    private String name;
    private String phone;
    private String email;
    private String currency;
    private GroupSettingsRequest settings;
}
