package vikoba.service.organization.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateMemberRequest {
    private String firstName;
    private String middleName;
    private String lastName;
    private String phone;
    private String email;
    private String nationalId;
    private String address;
    private String occupation;
    private String nextOfKinName;
    private String nextOfKinPhone;
    private String nextOfKinRelationship;
}
