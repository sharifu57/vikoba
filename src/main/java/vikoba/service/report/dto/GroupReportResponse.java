package vikoba.service.report.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class GroupReportResponse {
    private Long groupId;
    private String groupName;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private Summary summary;
    private List<MonthlyTotal> monthlyTotals;
    private List<MemberBalance> memberBalances;
    private List<ActivityRow> recentTransactions;

    @Getter
    @Builder
    public static class Summary {
        private long members;
        private BigDecimal contributions;
        private BigDecimal shareCapital;
        private BigDecimal loanOutstanding;
        private BigDecimal finesOutstanding;
        private BigDecimal income;
        private BigDecimal expenses;
        private BigDecimal netIncome;
        private long activeLoans;
        private long unpaidFines;
        private long meetings;
    }

    @Getter
    @Builder
    public static class MonthlyTotal {
        private String month;
        private BigDecimal contributions;
        private BigDecimal payments;
        private BigDecimal expenses;
    }

    @Getter
    @Setter
    @Builder
    public static class MemberBalance {
        private Long groupMemberId;
        private String memberName;
        private String membershipNumber;
        private BigDecimal contributions;
        private BigDecimal fines;
        private BigDecimal loanBalance;
    }

    @Getter
    @Builder
    public static class ActivityRow {
        private String date;
        private String reference;
        private String memberName;
        private String category;
        private BigDecimal amount;
        private String status;
    }
}
