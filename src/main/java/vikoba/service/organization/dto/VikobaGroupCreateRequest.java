package vikoba.service.organization.dto;

import lombok.Getter;
import lombok.Setter;
import vikoba.service.common.enums.MeetingFrequency;

@Getter
@Setter
public class VikobaGroupCreateRequest {
    private Long organizationId;
    private String name;
    private String phone;
    private String description;
    private MeetingFrequency meetingFrequency;
    private String meetingDay;
    private String currency;
    private GroupSettingsRequest settings;
}