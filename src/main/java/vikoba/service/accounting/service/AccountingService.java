package vikoba.service.accounting.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vikoba.service.accounting.dto.*;
import vikoba.service.accounting.entity.Account;
import vikoba.service.accounting.entity.FinancialTransaction;
import vikoba.service.accounting.entity.TransactionLine;
import vikoba.service.accounting.repository.AccountRepository;
import vikoba.service.accounting.repository.FinancialTransactionRepository;
import vikoba.service.accounting.repository.TransactionLineRepository;
import vikoba.service.common.enums.AccountType;
import vikoba.service.common.enums.FinancialTransactionStatus;
import vikoba.service.organization.entity.VikobaGroup;
import vikoba.service.organization.repository.VikobaGroupRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountingService {
    private final AccountRepository accountRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final TransactionLineRepository lineRepository;
    private final VikobaGroupRepository groupRepository;

    @Transactional
    public List<AccountResponse> accounts(Long groupId) {
        ensureDefaultAccounts(groupId);
        return balances(groupId);
    }

    @Transactional
    public AccountResponse createAccount(Long groupId, AccountRequest request) {
        VikobaGroup group = requireGroup(groupId);
        String code = required(request.getCode(), "account code");
        if (accountRepository.existsByGroupIdAndCode(groupId, code))
            throw new IllegalArgumentException("An account with this code already exists.");
        try {
            Account account = accountRepository.save(Account.builder().group(group).code(code)
                    .name(required(request.getName(), "account name")).type(parseType(request.getType()))
                    .active(request.getActive() == null || request.getActive()).build());
            return toAccount(account, BigDecimal.ZERO, BigDecimal.ZERO);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("An account with this code already exists.", e);
        }
    }

    @Transactional
    public List<LedgerLineResponse> ledger(Long groupId) {
        ensureDefaultAccounts(groupId);
        java.util.Map<Long, BigDecimal> balances = new java.util.HashMap<>();
        return lineRepository.findLedgerByGroupId(groupId).stream().map(line -> {
            BigDecimal next = balances.getOrDefault(line.getAccount().getId(), BigDecimal.ZERO).add(line.getDebit())
                    .subtract(line.getCredit());
            balances.put(line.getAccount().getId(), next);
            return LedgerLineResponse.builder().id(line.getId()).transactionId(line.getTransaction().getId())
                    .transactionDate(line.getTransaction().getTransactionDate())
                    .reference(line.getTransaction().getReference())
                    .description(line.getDescription() == null ? line.getTransaction().getDescription()
                            : line.getDescription())
                    .accountId(line.getAccount().getId()).accountCode(line.getAccount().getCode())
                    .accountName(line.getAccount().getName()).debit(line.getDebit()).credit(line.getCredit())
                    .balance(next).build();
        }).toList();
    }

    @Transactional
    public LedgerLineResponse post(Long groupId, JournalEntryRequest request) {
        VikobaGroup group = requireGroup(groupId);
        if (request.getLines() == null || request.getLines().size() < 2)
            throw new IllegalArgumentException("A journal entry must contain at least two lines.");
        BigDecimal debits = request.getLines().stream().map(line -> amount(line.getDebit())).reduce(BigDecimal.ZERO,
                BigDecimal::add);
        BigDecimal credits = request.getLines().stream().map(line -> amount(line.getCredit())).reduce(BigDecimal.ZERO,
                BigDecimal::add);
        if (debits.signum() <= 0 || debits.compareTo(credits) != 0)
            throw new IllegalArgumentException("Journal debits and credits must be equal and greater than zero.");
        String reference = blank(request.getReference());
        if (reference == null)
            reference = "JRN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        if (transactionRepository.existsByReference(reference))
            throw new IllegalArgumentException("This journal reference already exists.");
        FinancialTransaction transaction = transactionRepository.save(FinancialTransaction.builder().group(group)
                .reference(reference).description(required(request.getDescription(), "description"))
                .transactionDate(
                        request.getTransactionDate() == null ? LocalDateTime.now() : request.getTransactionDate())
                .status(FinancialTransactionStatus.POSTED).build());
        List<TransactionLine> saved = request.getLines().stream().map(item -> {
            Account account = accountRepository.findByIdAndGroupId(item.getAccountId(), groupId)
                    .orElseThrow(() -> new IllegalArgumentException("Journal account not found in this group."));
            BigDecimal debit = amount(item.getDebit()), credit = amount(item.getCredit());
            if ((debit.signum() > 0 && credit.signum() > 0) || (debit.signum() == 0 && credit.signum() == 0))
                throw new IllegalArgumentException("Each journal line must have either a debit or a credit.");
            return TransactionLine.builder().transaction(transaction).account(account).debit(debit).credit(credit)
                    .description(blank(item.getDescription())).build();
        }).map(lineRepository::save).toList();
        TransactionLine first = saved.get(0);
        return LedgerLineResponse.builder().id(first.getId()).transactionId(transaction.getId())
                .transactionDate(transaction.getTransactionDate()).reference(reference)
                .description(first.getDescription() == null ? transaction.getDescription() : first.getDescription())
                .accountId(first.getAccount().getId()).accountCode(first.getAccount().getCode())
                .accountName(first.getAccount().getName()).debit(first.getDebit()).credit(first.getCredit())
                .balance(first.getDebit().subtract(first.getCredit())).build();
    }

    @Transactional
    public TrialBalanceResponse trialBalance(Long groupId) {
        ensureDefaultAccounts(groupId);
        List<AccountResponse> accounts = balances(groupId);
        return TrialBalanceResponse.builder().accounts(accounts)
                .totalDebit(accounts.stream().map(AccountResponse::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add))
                .totalCredit(accounts.stream().map(AccountResponse::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add))
                .build();
    }

    @Transactional
    public List<AccountResponse> ensureDefaultAccountsForGroup(Long groupId) {
        ensureDefaultAccounts(groupId);
        return balances(groupId);
    }

    private List<AccountResponse> balances(Long groupId) {
        java.util.Map<Long, BigDecimal[]> totals = new java.util.HashMap<>();
        lineRepository.findLedgerByGroupId(groupId).forEach(line -> {
            BigDecimal[] pair = totals.computeIfAbsent(line.getAccount().getId(),
                    ignored -> new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO });
            pair[0] = pair[0].add(line.getDebit());
            pair[1] = pair[1].add(line.getCredit());
        });
        return accountRepository.findByGroupIdOrderByCodeAsc(groupId).stream().map(account -> {
            BigDecimal[] pair = totals.getOrDefault(account.getId(),
                    new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO });
            return toAccount(account, pair[0], pair[1]);
        }).toList();
    }

    private AccountResponse toAccount(Account account, BigDecimal debit, BigDecimal credit) {
        return AccountResponse.builder().id(account.getId()).code(account.getCode()).name(account.getName())
                .type(account.getType().name()).active(account.isActive()).debit(debit).credit(credit)
                .balance(debit.subtract(credit)).build();
    }

    private void ensureDefaultAccounts(Long groupId) {
        VikobaGroup group = requireGroup(groupId);
        List<Object[]> defaults = List.of(new Object[] { "1000", "Cash in Hand", AccountType.ASSET },
                new Object[] { "1010", "Bank Account", AccountType.ASSET },
                new Object[] { "1020", "Mobile Money", AccountType.ASSET },
                new Object[] { "1100", "Loan Receivables", AccountType.ASSET },
                new Object[] { "2000", "Member Savings", AccountType.LIABILITY },
                new Object[] { "3000", "Share Capital", AccountType.EQUITY },
                new Object[] { "4000", "Interest Income", AccountType.INCOME },
                new Object[] { "5000", "Operating Expenses", AccountType.EXPENSE });
        for (Object[] item : defaults) {
            String code = (String) item[0];
            if (accountRepository.existsByGroupIdAndCode(groupId, code)) {
                continue;
            }

            try {
                accountRepository.save(Account.builder().group(group).code(code).name((String) item[1])
                        .type((AccountType) item[2]).active(true).build());
            } catch (DataIntegrityViolationException e) {
                log.info("Default account {} already exists for group {}. Skipping duplicate insert.", code, groupId);
            }
        }
    }

    private VikobaGroup requireGroup(Long id) {
        if (id == null)
            throw new IllegalArgumentException("groupId is required.");
        return groupRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Group not found."));
    }

    private AccountType parseType(String value) {
        try {
            return AccountType.valueOf(required(value, "account type").toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid account type.");
        }
    }

    private BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String required(String value, String field) {
        String result = blank(value);
        if (result == null)
            throw new IllegalArgumentException(field + " is required.");
        return result;
    }

    private String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
