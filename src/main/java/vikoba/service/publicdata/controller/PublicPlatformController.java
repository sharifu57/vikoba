package vikoba.service.publicdata.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vikoba.service.common.response.ApiResponse;
import vikoba.service.publicdata.dto.PublicPlatformSummaryResponse;
import vikoba.service.publicdata.service.PublicPlatformService;

@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicPlatformController {
    private final PublicPlatformService publicPlatformService;

    @GetMapping("/platform-summary")
    public ResponseEntity<ApiResponse<PublicPlatformSummaryResponse>> summary() {
        return ResponseEntity
                .ok(ApiResponse.success("Public platform summary retrieved.", publicPlatformService.summary()));
    }
}
