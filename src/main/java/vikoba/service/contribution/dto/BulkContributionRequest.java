package vikoba.service.contribution.dto;

import lombok.*;
import java.util.List;

/**
 * DTO for bulk contribution upload request
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkContributionRequest {

    /**
     * Group ID for which contributions are being recorded
     */
    private Long groupId;

    /**
     * List of contribution rows from Excel
     */
    private List<BulkContributionRow> contributions;

    /**
     * Notes about the bulk upload
     */
    private String remarks;
}
