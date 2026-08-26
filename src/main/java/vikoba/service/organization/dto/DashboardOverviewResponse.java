package vikoba.service.organization.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record DashboardOverviewResponse(
        Summary summary,
        List<TrendPoint> contributionTrend,
        List<TrendPoint> shareTrend,
        List<MeetingItem> nextMeetings,
        Finance finance,
        List<Activity> recentActivities,
        Actions actions) {

    public record Summary(long totalMembers, BigDecimal contributions, int shares, BigDecimal shareCapital,
            BigDecimal outstandingLoans) {
    }

    public record TrendPoint(String month, BigDecimal amount) {
    }

    public record MeetingItem(Long id, String title, LocalDate date, LocalTime startTime, String location,
            String status, String agenda) {
    }

    public record Finance(BigDecimal totalReceived, BigDecimal cashReceived, BigDecimal bankReceived,
            BigDecimal mobileMoneyReceived, BigDecimal socialFundReceived, BigDecimal pendingAmount) {
    }

    public record Activity(Long id, String reference, String memberName, String type, BigDecimal amount,
            String method, String status, LocalDateTime date, String description) {
    }

    public record Actions(long pendingLoanApplications, long membersWithContributionArrears, long unpaidFines,
            long upcomingMeetings) {
    }
}
