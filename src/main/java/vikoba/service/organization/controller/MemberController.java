package vikoba.service.organization.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vikoba.service.common.response.ApiResponse;
import vikoba.service.organization.dto.AddMemberRequest;
import vikoba.service.organization.dto.MemberResponse;
import vikoba.service.organization.dto.MemberRoleOptionResponse;
import vikoba.service.organization.service.MemberService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class MemberController {
    private final MemberService memberService;

    @PostMapping("/members")
    public ResponseEntity<ApiResponse<MemberResponse>> addMember(@RequestBody AddMemberRequest request) {
        try {
            return ResponseEntity.ok(memberService.addMemberToGroup(request));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/members/roles")
    public ResponseEntity<ApiResponse<List<MemberRoleOptionResponse>>> getMemberRoles() {
        return ResponseEntity
                .ok(ApiResponse.success("Member roles retrieved successfully.", memberService.getMemberRoles()));
    }

    @GetMapping("/members/group/{groupId}")
    public ResponseEntity<ApiResponse<List<MemberResponse>>> getMembersByGroup(@PathVariable Long groupId) {
        try {
            return ResponseEntity.ok(
                    ApiResponse.success("Members retrieved successfully.", memberService.getMembersByGroup(groupId)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(ApiResponse.error(ex.getMessage()));
        }
    }
}
