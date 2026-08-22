package vikoba.service.organization.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class GroupProfileSettingsRequest {
    private String name;
    private String phone;
    private String email;
    private String currency;
    private LocalDate startDate;
    private LocalDate endDate;
    private GroupSettingsRequest settings;
}
