package vikoba.service.fine.dto;

import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;

@Getter
@Builder
public class FineTypeResponse {
    private Long id;
    private String code;
    private String name;
    private BigDecimal defaultAmount;
    private String description;
    private boolean active;
}
