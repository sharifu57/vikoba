package vikoba.service.contribution.dto;

import lombok.*;
import java.math.BigDecimal;

/**
 * DTO for contribution summary statistics
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContributionSummaryResponse {

    /**
     * Total expected contributions for the group
     */
    private BigDecimal totalExpected;

    /**
     * Total contributions received/paid
     */
    private BigDecimal totalPaid;

    /**
     * Total outstanding balance
     */
    private BigDecimal totalBalance;

    /**
     * Number of members with complete contributions
     */
    private Integer membersCompleted;

    /**
     * Number of members with partial contributions
     */
    private Integer membersPartial;

    /**
     * Number of members with no contributions
     */
    private Integer membersPending;

    /**
     * Collection rate as percentage
     */
    private Double collectionRate;

    /**
     * Total number of members in the group
     */
    private Integer totalMembers;
}
