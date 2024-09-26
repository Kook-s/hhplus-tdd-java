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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
    @DisplayName("포인트 사용 내역이 기록")
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
    @DisplayName("포인트가 부족하면 PointException")
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

    @Test
    @DisplayName("포인트 조회 포인트 정보를 반환")
    public void getPoint_ShouldReturnUserPoint() throws Exception {
        // given
        long userId = 1L;
        long currentPoint = 1000L;
        UserPoint userPoint = new UserPoint(userId, currentPoint, System.currentTimeMillis());

        given(userPointTable.selectById(userId)).willReturn(userPoint);

        // when
        UserPoint result = pointService.getPoint(userId);

        // then
        assertThat(result).isEqualTo(userPoint);
    }

    @Test
    @DisplayName("포인트 이력 조회  정보를 반환")
    public void getHistory_ShouldReturnPointHistory() throws Exception {
        // given
        long userId = 1L;
        List<PointHistory> historyList = new ArrayList<>();
        historyList.add(new PointHistory(1,userId, 500L, TransactionType.CHARGE, System.currentTimeMillis()));

        // 포인트 히스토리 조회 시 mock 데이터 반환 설정
        given(pointHistoryTable.selectAllByUserId(userId)).willReturn(historyList);

        // when
        List<PointHistory> result = pointService.getHistory(userId);

        // then
        assertThat(result).isEqualTo(historyList);
    }
    @Test
    @DisplayName("동시성 포인트 충전 테스트")
    public void concurrentChargePoint_ShouldHandleConcurrency() throws Exception {
        // given
        long userId = 1L;
        long initialPoint = 1000L;  // 초기 포인트
        long amountToCharge = 100L;  // 각 스레드가 충전할 포인트
        int threadCount = 10;  // 동시 실행할 스레드 수

        UserPoint userPoint = new UserPoint(userId, initialPoint, System.currentTimeMillis());
        given(userPointTable.selectById(userId)).willReturn(userPoint);

        // 포인트 충전 후 mock 설정 (스레드 동시 실행 대비)
        given(userPointTable.insertOrUpdate(eq(userId), anyLong()))
                .willAnswer(invocation -> {
                    Long chargedPoint = invocation.getArgument(1);
                    return new UserPoint(userId, chargedPoint, System.currentTimeMillis());
                });

        // when
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // 동시 실행: 여러 스레드가 동시에 포인트 충전을 수행
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    pointService.chargePoint(userId, amountToCharge);
                } finally {
                    latch.countDown(); // 각 스레드가 작업 완료 후 count down
                }
            });
        }

        latch.await(); // 모든 스레드가 완료될 때까지 대기
        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);

        // then
        // 최종 포인트 검증: 1000 + (100 * 10) = 2000
        verify(userPointTable, times(threadCount)).insertOrUpdate(eq(userId), anyLong());
        UserPoint finalUserPoint = pointService.getPoint(userId);
        assertThat(finalUserPoint.point()).isEqualTo(initialPoint + (amountToCharge * threadCount));
    }

}



