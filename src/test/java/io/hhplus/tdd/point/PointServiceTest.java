package io.hhplus.tdd.point;


import io.hhplus.tdd.point.repository.PointHistoryRepository;
import io.hhplus.tdd.point.repository.UserPointRepository;
import io.hhplus.tdd.point.service.PointService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.BDDMockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PointServiceTest {

    @InjectMocks
    private PointService pointService;

    @Mock
    private UserPointRepository userPointTable;

    @Mock
    private PointHistoryRepository pointHistoryTable;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("포인트 업데이트 확인 테스트")
    public void chargePoint_ShouldUpdateUserPoint() throws Exception {
        // given
        long userId = 1L;
        long initialPoint = 1000L;  // 초기 포인트
        long amountToCharge = 500L;  // 충전할 포인트
        UserPoint userPoint = new UserPoint(userId, initialPoint, System.currentTimeMillis());

        given(userPointTable.selectById(userId)).willReturn(userPoint);

        given(userPointTable.insertOrUpdate(userId, initialPoint + amountToCharge))
                .willReturn(new UserPoint(userId, initialPoint + amountToCharge, System.currentTimeMillis()));

        // when
        UserPoint updatedUserPoint = pointService.chargePoint(userId, amountToCharge);

        // then
        assertThat(updatedUserPoint.point()).isEqualTo(initialPoint + amountToCharge);
    }


}



