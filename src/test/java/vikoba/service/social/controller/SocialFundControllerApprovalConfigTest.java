package vikoba.service.social.controller;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import vikoba.service.common.enums.GroupRole;
import vikoba.service.organization.dto.ShareApprovalStepConfig;
import vikoba.service.social.service.SocialFundService;

class SocialFundControllerApprovalConfigTest {

    private MockMvc mockMvc;
    private SocialFundService socialFundService;

    @BeforeEach
    void setUp() {
        socialFundService = mock(SocialFundService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new SocialFundController(socialFundService)).build();
    }

    @Test
    void approvalConfigEndpointReturnsWorkflowSettings() throws Exception {
        when(socialFundService.approvalConfig(5L)).thenReturn(List.of(
                new ShareApprovalStepConfig(GroupRole.ACCOUNTANT, "Accountant review"),
                new ShareApprovalStepConfig(GroupRole.GROUP_CHAIRMAN, "Chair approval")));

        mockMvc.perform(get("/api/social-fund/group/5/approval-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.data[0].role").value("ACCOUNTANT"))
                .andExpect(jsonPath("$.data[0].label").value("Accountant review"));
    }

    @Test
    void configureApprovalEndpointAcceptsWorkflowSettings() throws Exception {
        when(socialFundService.configureApproval(eq(5L), anyList())).thenReturn(List.of(
                new ShareApprovalStepConfig(GroupRole.ACCOUNTANT, "Accountant review")));

        mockMvc.perform(put("/api/social-fund/group/5/approval-config")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[{\"role\":\"ACCOUNTANT\",\"label\":\"Accountant review\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.message").value("Jamii approval workflow saved."));
    }
}
