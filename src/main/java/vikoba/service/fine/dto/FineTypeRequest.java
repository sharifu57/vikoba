package vikoba.service.fine.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class FineTypeRequest {
    private String code;
    private String name;
    private BigDecimal defaultAmount;
    private String description;
    private Boolean active;
}
