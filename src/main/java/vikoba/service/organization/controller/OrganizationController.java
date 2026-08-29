package vikoba.service.organization.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vikoba.service.common.response.ApiResponse;
import vikoba.service.organization.dto.OrganizationRegistrationRequest;
import vikoba.service.organization.dto.OrganizationRegistrationResponse;
import vikoba.service.organization.dto.GroupProfileSettingsRequest;
import vikoba.service.organization.dto.GroupWithSettingsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import vikoba.service.organization.dto.VikobaGroupCreateRequest;
import vikoba.service.organization.dto.VikobaGroupCreateResponse;
import vikoba.service.organization.service.OrganizationRegistrationService;
import vikoba.service.organization.service.VikobaService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class OrganizationController {
    private final OrganizationRegistrationService registrationService;
    private final VikobaService vikobaService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<OrganizationRegistrationResponse>> register(
            @RequestBody OrganizationRegistrationRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success("Organization registered successfully.", registrationService.register(request)));
    }

    @PostMapping("/groups")
    public ResponseEntity<ApiResponse<VikobaGroupCreateResponse>> createGroup(
            @RequestBody VikobaGroupCreateRequest request) {
        try {
            VikobaGroupCreateResponse response = vikobaService.createGroup(request);
            return ResponseEntity.ok(ApiResponse.success("Group created successfully.", response));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PostMapping("/groups/setup")
    public ResponseEntity<ApiResponse<GroupWithSettingsResponse>> createGroupSetup(
            @RequestBody GroupProfileSettingsRequest request) {
        try {
            ApiResponse<GroupWithSettingsResponse> response = vikobaService.createGroupWithSettings(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/groups/{id}")
    public ResponseEntity<ApiResponse<GroupWithSettingsResponse>> getGroup(
            @org.springframework.web.bind.annotation.PathVariable Long id) {
        try {
            GroupWithSettingsResponse resp = vikobaService.getGroupWithSettings(id);
            return ResponseEntity.ok(ApiResponse.success("Group details retrieved.", resp));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/groups")
    public ResponseEntity<ApiResponse<java.util.List<GroupWithSettingsResponse>>> listGroups() {
        try {
            java.util.List<GroupWithSettingsResponse> resp = vikobaService.listGroupsForCurrentUser();
            return ResponseEntity.ok(ApiResponse.success("Groups retrieved.", resp));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(ApiResponse.error(ex.getMessage()));
        }
    }
}