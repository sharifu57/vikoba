package vikoba.service.accounting.dto;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter
public class JournalEntryRequest { private String reference; private String description; private LocalDateTime transactionDate; private List<JournalLineRequest> lines; }
