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
    public ResponseEntity<ApiResponse<VikobaGroupCreateResponse>> createGroupSetup(
            @RequestBody GroupProfileSettingsRequest request) {
        try {
            ApiResponse<VikobaGroupCreateResponse> response = vikobaService.createGroupWithSettings(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(ApiResponse.error(ex.getMessage()));
        }
    }
}