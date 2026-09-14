package vikoba.service.loan.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vikoba.service.loan.repository.LoanRepository;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoanOverdueScheduler {
    private final LoanRepository loans;
    private final LoanWorkflowService workflow;

    @Scheduled(cron = "0 15 0 * * *", zone = "Africa/Dar_es_Salaam")
    public void assessDaily() {
        for (Long groupId : loans.findActiveLoanGroupIds()) {
            try {
                workflow.assessOverdue(groupId);
            } catch (RuntimeException error) {
                log.error("Unable to assess overdue loans for group {}", groupId, error);
            }
        }
    }
}
