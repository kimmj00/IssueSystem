package com.example.issuesystem.common.controller;

import com.example.issuesystem.issue.controller.IssueCaseController;
import com.example.issuesystem.issue.service.IssueCaseService;
import com.example.issuesystem.patchhistory.controller.PatchHistoryController;
import com.example.issuesystem.patchhistory.service.PatchHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RouteCompatibilityTest {
    private final IssueCaseService issues = mock(IssueCaseService.class);
    private final PatchHistoryService patches = mock(PatchHistoryService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(
                new IssueCaseController(issues), new PatchHistoryController(patches)).build();
    }

    @Test
    void 패치_목록은_패치_컨트롤러가_처리한다() throws Exception {
        mvc.perform(get("/api/patch-histories"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        verify(patches).getAll();
        verifyNoInteractions(issues);
    }

    @Test
    void 기존_이슈_목록_경로를_유지한다() throws Exception {
        mvc.perform(get("/api/issue-cases"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        verify(issues).getAll();
        verifyNoInteractions(patches);
    }

    @Test
    void 기존_이슈_상세_경로와_경로_인자를_유지한다() throws Exception {
        mvc.perform(get("/api/issue-cases/42"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        verify(issues).get(42L);
        verifyNoInteractions(patches);
    }

    @Test
    void 기존_이슈_검색_경로와_검색_인자를_유지한다() throws Exception {
        mvc.perform(get("/api/issue-cases/search").param("keyword", "agent")
                        .param("infraType", "EMS").param("page", "0").param("size", "7"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        verifyNoInteractions(patches);
    }
}
