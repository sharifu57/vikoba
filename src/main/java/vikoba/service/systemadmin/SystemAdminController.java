package vikoba.service.systemadmin;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vikoba.service.common.response.ApiResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/system-admin")
public class SystemAdminController {
    @ExceptionHandler(IllegalArgumentException.class)
    public org.springframework.http.ResponseEntity<ApiResponse<Void>> invalid(IllegalArgumentException error) {
        return org.springframework.http.ResponseEntity.badRequest().body(ApiResponse.error(error.getMessage()));
    }
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<ApiResponse<Void>> conflict(org.springframework.web.server.ResponseStatusException error) {
        return org.springframework.http.ResponseEntity.status(error.getStatusCode()).body(ApiResponse.error(error.getReason()));
    }
    private final SystemAdminAccess access;
    private final SystemAdminService service;
    public record Access(boolean superAdmin) {}
    public record ChairRequest(Long membershipId, Long expectedChairId, String reason) {}
    public record DirectoryPage<T>(java.util.List<T> content, long totalElements, int totalPages, int number) {
        static <T> DirectoryPage<T> from(org.springframework.data.domain.Page<T> page) {
            return new DirectoryPage<>(page.getContent(), page.getTotalElements(), page.getTotalPages(), page.getNumber());
        }
    }
    @GetMapping("/access")
    public ApiResponse<Access> access() { return ApiResponse.success("System access", new Access(access.isSuperAdmin())); }
    @GetMapping("/overview")
    public ApiResponse<?> overview() { return ApiResponse.success("System overview", service.overview()); }
    @GetMapping("/groups")
    public ApiResponse<?> groups(@RequestParam(defaultValue = "") String search,
                                @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success("Groups", DirectoryPage.from(service.groups(search, page, size)));
    }
    @GetMapping("/members")
    public ApiResponse<?> members(@RequestParam(required = false) Long groupId,
                                 @RequestParam(defaultValue = "") String search,
                                 @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success("Members", DirectoryPage.from(service.members(groupId, search, page, size)));
    }
    @GetMapping("/audit")
    public ApiResponse<?> audit(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success("Audit history", DirectoryPage.from(service.audit(page, size)));
    }
    @PutMapping("/groups/{groupId}/chair")
    public ApiResponse<?> chair(@PathVariable Long groupId, @RequestBody ChairRequest request) {
        return ApiResponse.success("Chairperson updated", service.changeChair(groupId,
                request.membershipId(), request.expectedChairId(), request.reason()));
    }
}
