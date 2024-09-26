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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.*;

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
        long userId = 1L;
        long initialPoint = 1000L;
        long amountToCharge = 500L;
        UserPoint userPoint = new UserPoint(userId, initialPoint, System.currentTimeMillis());

        given(userPointTable.selectById(userId)).willReturn(userPoint);

        given(userPointTable.insertOrUpdate(userId, initialPoint + amountToCharge))
                .willReturn(new UserPoint(userId, initialPoint + amountToCharge, System.currentTimeMillis()));

        UserPoint updatedUserPoint = pointService.chargePoint(userId, amountToCharge);

        assertThat(updatedUserPoint.point()).isEqualTo(initialPoint + amountToCharge);
    }

    @Test
    @DisplayName("포인트 사용 내역이 기록")
    public void usePoint_ShouldRecordHistory() throws Exception {
        long userId = 1L;
        long initialPoint = 1000L;
        long amountToUse = 500L;
        UserPoint userPoint = new UserPoint(userId, initialPoint, System.currentTimeMillis());

        given(userPointTable.selectById(userId)).willReturn(userPoint);
        given(userPointTable.insertOrUpdate(userId, initialPoint - amountToUse))
                .willReturn(new UserPoint(userId, initialPoint - amountToUse, System.currentTimeMillis()));

        pointService.usePoint(userId, amountToUse);

        verify(pointHistoryTable).insert(eq(userId), eq(amountToUse), eq(TransactionType.USE), anyLong());
    }

    @Test
    @DisplayName("포인트가 부족하면 PointException")
    public void usePoint_ShouldThrowException_WhenInsufficientBalance() throws Exception {
        long userId = 1L;
        long initialPoint = 100L;
        long amountToUse = 500L;
        UserPoint userPoint = new UserPoint(userId, initialPoint, System.currentTimeMillis());

        given(userPointTable.selectById(userId)).willReturn(userPoint);

        assertThrows(PointException.class, () -> pointService.usePoint(userId, amountToUse));
        verify(userPointTable, never()).insertOrUpdate(anyLong(), anyLong());
        verify(pointHistoryTable, never()).insert(anyLong(), anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("포인트 조회 포인트 정보를 반환")
    public void getPoint_ShouldReturnUserPoint() throws Exception {
        long userId = 1L;
        long currentPoint = 1000L;
        UserPoint userPoint = new UserPoint(userId, currentPoint, System.currentTimeMillis());

        given(userPointTable.selectById(userId)).willReturn(userPoint);

        UserPoint result = pointService.getPoint(userId);

        assertThat(result).isEqualTo(userPoint);
    }

    @Test
    @DisplayName("포인트 이력 조회 정보를 반환")
    public void getHistory_ShouldReturnPointHistory() throws Exception {
        long userId = 1L;
        List<PointHistory> historyList = new ArrayList<>();
        historyList.add(new PointHistory(1, userId, 500L, TransactionType.CHARGE, System.currentTimeMillis()));

        given(pointHistoryTable.selectAllByUserId(userId)).willReturn(historyList);

        List<PointHistory> result = pointService.getHistory(userId);

        assertThat(result).isEqualTo(historyList);
    }

    @Test
    @DisplayName("동시성 테스트 - 포인트 충전 및 사용")
    public void concurrent_PointOperations_ShouldBeHandledSequentially() throws Exception {
        long userId = 1L;
        long initialPoint = 1000L;
        UserPoint userPoint = new UserPoint(userId, initialPoint, System.currentTimeMillis());

        given(userPointTable.selectById(userId)).willReturn(userPoint);
        given(userPointTable.insertOrUpdate(eq(userId), anyLong())).willAnswer(invocation -> {
            long updatedPoint = invocation.getArgument(1, Long.class);
            return new UserPoint(userId, updatedPoint, System.currentTimeMillis());
        });

        int numberOfThreads = 10;
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);

        for (int i = 0; i < numberOfThreads; i++) {
            final int index = i;
            executorService.execute(() -> {
                try {
                    if (index % 2 == 0) {
                        pointService.chargePoint(userId, 100);
                    } else {
                        pointService.usePoint(userId, 50);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        verify(userPointTable, times(numberOfThreads)).insertOrUpdate(eq(userId), anyLong());
    }
}
