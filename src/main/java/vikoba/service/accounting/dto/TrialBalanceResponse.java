package vikoba.service.accounting.dto;

import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;
import java.util.List;

@Getter @Builder
public class TrialBalanceResponse { private List<AccountResponse> accounts; private BigDecimal totalDebit; private BigDecimal totalCredit; }
