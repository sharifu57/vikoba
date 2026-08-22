package vikoba.service.organization.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrganizationRegistrationRequest {
    private String organizationName;
    private String groupName;
}