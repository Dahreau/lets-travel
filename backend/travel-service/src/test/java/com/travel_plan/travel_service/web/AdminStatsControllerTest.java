package com.travel_plan.travel_service.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.travel_plan.travel_service.exception.ApiExceptionHandler;
import com.travel_plan.travel_service.service.AdminStatsService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminStatsControllerTest {

    private AdminStatsService adminStatsService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        adminStatsService = mock(AdminStatsService.class);
        AdminStatsController controller = new AdminStatsController(adminStatsService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void managerRankingsReturnsServiceResult() throws Exception {
        UUID managerId = UUID.randomUUID();
        when(adminStatsService.managerRankings())
                .thenReturn(List.of(new AdminManagerRankingResponse(managerId, 2, 5, BigDecimal.valueOf(450), 4.2, 1, 43.0)));

        mockMvc.perform(get("/api/travels/admin/manager-rankings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].managerId").value(managerId.toString()))
                .andExpect(jsonPath("$[0].performanceScore").value(43.0));
    }

    @Test
    void travelRankingsReturnsServiceResult() throws Exception {
        UUID travelId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        when(adminStatsService.travelRankings())
                .thenReturn(List.of(
                        new AdminTravelRankingResponse(travelId, "Trip", managerId, 3, BigDecimal.valueOf(300), 4.5)));

        mockMvc.perform(get("/api/travels/admin/travel-rankings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].travelId").value(travelId.toString()))
                .andExpect(jsonPath("$[0].revenue").value(300));
    }

    @Test
    void monthlyRevenueReturnsServiceResult() throws Exception {
        when(adminStatsService.monthlyRevenue())
                .thenReturn(List.of(new AdminMonthlyRevenueResponse("2026-08", BigDecimal.valueOf(500))));

        mockMvc.perform(get("/api/travels/admin/monthly-revenue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].month").value("2026-08"))
                .andExpect(jsonPath("$[0].revenue").value(500));
    }
}
