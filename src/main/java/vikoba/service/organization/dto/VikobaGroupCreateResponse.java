package vikoba.service.organization.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class VikobaGroupCreateResponse {
    private Long organizationId;
    private Long groupId;
    private String organizationName;
    private String groupName;
    private String groupCode;
    private String currency;
}