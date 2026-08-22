package vikoba.service.organization.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vikoba.service.common.response.ApiResponse;
import vikoba.service.organization.dto.DashboardStatistic;
import vikoba.service.organization.service.DashboardService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardStatistic>> getDashboard(@RequestParam("groupId") Long groupId) {
        try {
            DashboardStatistic stats = dashboardService.getStatisticsForGroup(groupId);
            return ResponseEntity.ok(ApiResponse.success("Dashboard statistics retrieved.", stats));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Unable to fetch dashboard statistics."));
        }
    }
}
