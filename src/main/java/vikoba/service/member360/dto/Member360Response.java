package vikoba.service.member360.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import vikoba.service.organization.dto.MemberResponse;

import java.util.List;

@Getter
@Setter
public class Member360Response {
    private MemberResponse member;
    private List<?> contributions;
    private List<?> loans;
    private List<?> fines;
    private List<?> meetingAttendance;
    private List<?> socialFundContributions;
}
