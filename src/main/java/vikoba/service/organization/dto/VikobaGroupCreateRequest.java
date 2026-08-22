package vikoba.service.organization.dto;

import lombok.Getter;
import lombok.Setter;
import vikoba.service.common.enums.MeetingFrequency;

import java.time.LocalDate;

@Getter
@Setter
public class VikobaGroupCreateRequest {
    private String name;
    private String phone;
    private String email;
    private String description;
    private MeetingFrequency meetingFrequency;
    private String meetingDay;
    private String currency;
    private LocalDate startDate;
    private LocalDate endDate;
    private GroupSettingsRequest settings;
}