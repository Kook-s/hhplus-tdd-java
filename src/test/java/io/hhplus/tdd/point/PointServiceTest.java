package io.hhplus.tdd.point;


import io.hhplus.tdd.point.exception.PointException;
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

    @Test
    @DisplayName("포인트 사용 시 사용 내역이 기록된다")
    public void usePoint_ShouldRecordHistory() throws Exception {
        // given
        long userId = 1L;
        long initialPoint = 1000L;  // 초기 포인트
        long amountToUse = 500L;    // 사용할 포인트
        UserPoint userPoint = new UserPoint(userId, initialPoint, System.currentTimeMillis());

        // 사용자 포인트 조회 시 mock 데이터 반환 설정
        given(userPointTable.selectById(userId)).willReturn(userPoint);

        // 포인트 업데이트 mock 설정
        given(userPointTable.insertOrUpdate(userId, initialPoint - amountToUse))
                .willReturn(new UserPoint(userId, initialPoint - amountToUse, System.currentTimeMillis()));

        // when
        pointService.usePoint(userId, amountToUse);

        // then
        // 포인트 사용 내역이 기록되었는지 검증
        verify(pointHistoryTable).insert(eq(userId), eq(amountToUse), eq(TransactionType.USE), anyLong());
    }
    @Test
    @DisplayName("포인트가 부족하면 tException")
    public void usePoint_ShouldThrowException_WhenInsufficientBalance() throws Exception {
        // given
        long userId = 1L;
        long initialPoint = 100L;  // 초기 포인트
        long amountToUse = 500L;   // 사용할 포인트 (초기 포인트보다 큼)
        UserPoint userPoint = new UserPoint(userId, initialPoint, System.currentTimeMillis());

        // 사용자 포인트 조회 시 mock 데이터 반환 설정
        given(userPointTable.selectById(userId)).willReturn(userPoint);

        // when & then
        assertThrows(PointException.class, () -> pointService.usePoint(userId, amountToUse));
        verify(userPointTable, never()).insertOrUpdate(anyLong(), anyLong()); // 포인트가 부족하므로 업데이트가 호출되지 않아야 함
        verify(pointHistoryTable, never()).insert(anyLong(), anyLong(), any(), anyLong()); // 이력도 남지 않아야 함
    }



}



