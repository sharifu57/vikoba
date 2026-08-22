package vikoba.service.organization.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vikoba.service.common.enums.GroupRole;
import vikoba.service.common.enums.MembershipStatus;
import vikoba.service.common.enums.MembershipType;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberResponse {
    private Long id;
    private Long groupId;
    private Long memberId;
    private String membershipNumber;
    private String memberNumber;
    private String firstName;
    private String middleName;
    private String lastName;
    private String fullName;
    private String phone;
    private String email;
    private String nationalId;
    private String address;
    private String occupation;
    private String nextOfKinName;
    private String nextOfKinPhone;
    private String nextOfKinRelationship;
    private MembershipType membershipType;
    private MembershipStatus membershipStatus;
    private GroupRole role;
    private LocalDate joinedDate;
    private LocalDate createdAt;
}
