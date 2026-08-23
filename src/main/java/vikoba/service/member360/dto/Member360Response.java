package vikoba.service.member360.dto;

import lombok.*;
import vikoba.service.contribution.dto.MemberContributionResponse;
import vikoba.service.fine.dto.FineResponse;
import vikoba.service.loan.dto.LoanResponse;
import vikoba.service.meeting.dto.MeetingAttendanceResponse;
import vikoba.service.organization.dto.MemberResponse;
import vikoba.service.social.dto.SocialFundContributionResponse;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Member360Response {

    private MemberResponse member;

    private List<MemberContributionResponse> contributions;

    private List<LoanResponse> loans;

    private List<FineResponse> fines;

    private List<MeetingAttendanceResponse> meetingAttendance;

    private List<SocialFundContributionResponse> socialFundContributions;
}
