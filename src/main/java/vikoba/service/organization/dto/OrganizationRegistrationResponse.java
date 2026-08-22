package vikoba.service.organization.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OrganizationRegistrationResponse {
    private Long organizationId;
    private Long groupId;
    private Long memberId;
    private String organizationCode;
    private String groupCode;
    private String role;
}