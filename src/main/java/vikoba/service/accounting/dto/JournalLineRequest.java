package vikoba.service.accounting.dto;

import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter @Setter
public class JournalLineRequest { private Long accountId; private BigDecimal debit; private BigDecimal credit; private String description; }
