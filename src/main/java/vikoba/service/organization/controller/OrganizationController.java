package vikoba.service.organization.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vikoba.service.organization.dto.OrganizationRegistrationRequest;
import vikoba.service.organization.dto.OrganizationRegistrationResponse;
import vikoba.service.organization.dto.VikobaGroupCreateRequest;
import vikoba.service.organization.dto.VikobaGroupCreateResponse;
import vikoba.service.organization.service.OrganizationRegistrationService;
import vikoba.service.organization.service.VikobaService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/organizations")
public class OrganizationController {
    private final OrganizationRegistrationService registrationService;
    private final VikobaService vikobaService;

    @PostMapping("/register")
    public ResponseEntity<OrganizationRegistrationResponse> register(
            @RequestBody OrganizationRegistrationRequest request) {
        return ResponseEntity.ok(registrationService.register(request));
    }

    @PostMapping("/groups")
    public ResponseEntity<VikobaGroupCreateResponse> createGroup(
            @RequestBody VikobaGroupCreateRequest request) {
        return ResponseEntity.ok(vikobaService.createGroup(request));
    }
}