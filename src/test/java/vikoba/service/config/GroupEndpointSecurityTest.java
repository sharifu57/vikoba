package vikoba.service.config;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vikoba.service.accounting.service.AccountingService;
import vikoba.service.common.service.AuditLogService;
import vikoba.service.contribution.controller.ContributionController;
import vikoba.service.contribution.service.ContributionService;
import vikoba.service.contribution.service.PaymentService;
import vikoba.service.dividend.service.DividendService;
import vikoba.service.fine.service.FineService;
import vikoba.service.organization.controller.OrganizationController;
import vikoba.service.organization.service.GroupAuthorizationService;
import vikoba.service.organization.service.OrganizationRegistrationService;
import vikoba.service.organization.service.VikobaService;
import vikoba.service.report.service.GroupReportService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GroupEndpointSecurityTest {
    record Check(Class<?> service, String method, Object[] arguments, String permission) {}

    @TestFactory Stream<DynamicTest> foreignGroupsAreRejectedBeforeReadingOrWritingFinancialData() {
        List<Check> checks = List.of(
                new Check(FineService.class, "list", new Object[]{7L}, null),
                new Check(FineService.class, "types", new Object[]{7L}, null),
                new Check(FineService.class, "create", new Object[]{7L, null}, "FINE_MANAGE"),
                new Check(FineService.class, "update", new Object[]{7L, 9L, null}, "FINE_MANAGE"),
                new Check(FineService.class, "createType", new Object[]{7L, null}, "FINE_MANAGE"),
                new Check(FineService.class, "updateType", new Object[]{7L, 9L, null}, "FINE_MANAGE"),
                new Check(FineService.class, "deleteType", new Object[]{7L, 9L}, "FINE_MANAGE"),
                new Check(DividendService.class, "list", new Object[]{7L, 2026}, null),
                new Check(DividendService.class, "generate", new Object[]{7L, null}, "DIVIDEND_MANAGE"),
                new Check(PaymentService.class, "list", new Object[]{7L}, null),
                new Check(PaymentService.class, "record", new Object[]{7L, null}, null),
                new Check(AccountingService.class, "accounts", new Object[]{7L}, null),
                new Check(AccountingService.class, "ledger", new Object[]{7L}, null),
                new Check(AccountingService.class, "trialBalance", new Object[]{7L}, null),
                new Check(AccountingService.class, "createAccount", new Object[]{7L, null}, null),
                new Check(AccountingService.class, "post", new Object[]{7L, null}, null),
                new Check(AccountingService.class, "ensureDefaultAccountsForGroup", new Object[]{7L}, null),
                new Check(ContributionService.class, "getActiveContributionPeriods", new Object[]{7L}, null),
                new Check(ContributionService.class, "getGroupContributionDetails", new Object[]{7L, null, null}, null),
                new Check(ContributionService.class, "getContributionSummary", new Object[]{7L}, null),
                new Check(ContributionService.class, "processBulkContributionUpload", new Object[]{7L, null}, "CONTRIBUTION_MANAGE"),
                new Check(VikobaService.class, "getGroupWithSettings", new Object[]{7L}, null),
                new Check(AuditLogService.class, "list", new Object[]{7L, 0, 25}, "AUDIT_VIEW"),
                new Check(GroupReportService.class, "generate", new Object[]{7L, null, null}, "REPORT_VIEW"));
        return checks.stream().map(check -> DynamicTest.dynamicTest(
                check.service().getSimpleName() + "." + check.method(), () -> {
                    GroupAuthorizationService auth = mock(GroupAuthorizationService.class);
                    if (check.permission() == null) {
                        doThrow(new AccessDeniedException("foreign group")).when(auth).requireMembership(7L);
                    } else {
                        doThrow(new AccessDeniedException("missing permission")).when(auth)
                                .requirePermission(7L, check.permission());
                    }
                    var constructor = check.service().getConstructors()[0];
                    List<Object> repositories = new ArrayList<>();
                    Object[] dependencies = Stream.of(constructor.getParameterTypes()).map(type -> {
                        if (type == GroupAuthorizationService.class) return auth;
                        Object dependency = mock(type);
                        repositories.add(dependency);
                        return dependency;
                    }).toArray();
                    Object service = constructor.newInstance(dependencies);
                    var method = Stream.of(check.service().getMethods())
                            .filter(item -> item.getName().equals(check.method())).findFirst().orElseThrow();
                    var error = assertThrows(java.lang.reflect.InvocationTargetException.class,
                            () -> method.invoke(service, check.arguments()));
                    assertInstanceOf(AccessDeniedException.class, error.getCause());
                    verifyNoInteractions(repositories.toArray());
                }));
    }

    @Test void deniedContributionReadReturns403() throws Exception {
        var contributions = mock(ContributionService.class);
        when(contributions.getGroupContributionDetails(7L, null, null)).thenThrow(new AccessDeniedException("denied"));
        MockMvcBuilders.standaloneSetup(new ContributionController(contributions)).build()
                .perform(get("/api/contributions/group/7")).andExpect(status().isForbidden());
    }

    @Test void deniedGroupSettingsReadReturns403() throws Exception {
        var groups = mock(VikobaService.class);
        when(groups.getGroupWithSettings(7L)).thenThrow(new AccessDeniedException("denied"));
        MockMvcBuilders.standaloneSetup(new OrganizationController(mock(OrganizationRegistrationService.class), groups))
                .build().perform(get("/api/groups/7")).andExpect(status().isForbidden());
    }
}
