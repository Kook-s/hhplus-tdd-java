package io.hhplus.tdd.point;

import io.hhplus.tdd.point.service.PointService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.CharacterEncodingFilter;

import java.util.Arrays;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PointController.class)
class PointControllerTest {

    static final long USER_ID = 1L;
    static final long CHARGE_AMOUNT = 1_000L;
    static final long USE_AMOUNT = 200L;

    private UserPoint userPoint;
    private List<PointHistory> pointHistories;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PointService pointService;

    @Autowired
    WebApplicationContext ctx;

    MockMvc mvc;

    @BeforeEach
    public void setup() {
        this.mvc = MockMvcBuilders.webAppContextSetup(ctx)
                .addFilter(new CharacterEncodingFilter("UTF-8", true))
                .alwaysDo(print())
                .build();
    }

    @Test
    public void testGetPoint() throws Exception {
        //given
        UserPoint userPoint = new UserPoint(USER_ID, 0L, 0L);
        given(pointService.getPoint(USER_ID)).willReturn(userPoint);

        //when
        //then
        mvc.perform(get("/point/{id}", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID))
                .andExpect(jsonPath("$.point").value(0L))
                .andExpect(jsonPath("$.updateMillis").value(0L));
        verify(pointService).getPoint(USER_ID);
    }


    @Test
    @DisplayName("특정 유저의 포인트 충전")
    public void testChargePoint() throws Exception {
        //given
        UserPoint updatedUserPoint = new UserPoint(USER_ID, 1_000L, System.currentTimeMillis());
        given(pointService.chargePoint(USER_ID, CHARGE_AMOUNT)).willReturn(updatedUserPoint);

        //when
        //then
        mvc.perform(patch("/point/{id}/charge", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.valueOf(CHARGE_AMOUNT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID))
                .andExpect(jsonPath("$.point").value(1_000L));
        verify(pointService).chargePoint(USER_ID, CHARGE_AMOUNT);
    }

    @Test
    @DisplayName("특정 유저의 포인트 사용")
    public void testUsePoint() throws Exception {
        //given
        UserPoint updatedUserPoint = new UserPoint(USER_ID, 800L, System.currentTimeMillis());
        given(pointService.usePoint(USER_ID, USE_AMOUNT)).willReturn(updatedUserPoint);

        //when
        //then
        mvc.perform(patch("/point/{id}/use", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.valueOf(USE_AMOUNT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID))
                .andExpect(jsonPath("$.point").value(800L));
        verify(pointService).usePoint(USER_ID, USE_AMOUNT);
    }

    @Test
    @DisplayName("특정 유저의 포인트 충전/이용 내역 조회")
    public void testGetPointHistory() throws Exception {
        //given
        PointHistory history1 = new PointHistory(1L, USER_ID, 500L, TransactionType.CHARGE, System.currentTimeMillis());
        PointHistory history2 = new PointHistory(2L, USER_ID, 200L, TransactionType.USE, System.currentTimeMillis());
        List<PointHistory> pointHistories = Arrays.asList(history1, history2);

        given(pointService.getHistory(USER_ID)).willReturn(pointHistories);

        //when
        //then
        mvc.perform(get("/point/{id}/histories", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].amount").value(500L))
                .andExpect(jsonPath("$[0].type").value("CHARGE"))
                .andExpect(jsonPath("$[1].id").value(2L))
                .andExpect(jsonPath("$[1].amount").value(200L))
                .andExpect(jsonPath("$[1].type").value("USE"));
        verify(pointService).getHistory(USER_ID);
    }
}
