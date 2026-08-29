package vikoba.service.contribution.dto;

import lombok.*;
import java.util.List;

/**
 * DTO for bulk contribution upload result
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkContributionResult {

    /**
     * Total number of rows processed
     */
    private Integer totalRows;

    /**
     * Number of successful contributions recorded
     */
    private Integer successCount;

    /**
     * Number of failed rows
     */
    private Integer failureCount;

    /**
     * Summary message
     */
    private String summary;

    /**
     * List of failed rows with error messages
     */
    private List<BulkContributionRow> failedRows;

    /**
     * Overall status (SUCCESS, PARTIAL, FAILED)
     */
    private String status;
}
