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

    private static final long MAX_BALANCE = 10000L;

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

        given(userPointTable.selectById(eq(userId))).willReturn(userPoint);
        given(userPointTable.insertOrUpdate(eq(userId), eq(initialPoint + amountToCharge)))
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

        given(userPointTable.selectById(eq(userId))).willReturn(userPoint);
        given(userPointTable.insertOrUpdate(eq(userId), eq(initialPoint - amountToUse)))
                .willReturn(new UserPoint(userId, initialPoint - amountToUse, System.currentTimeMillis()));

        pointService.usePoint(userId, amountToUse);

        verify(pointHistoryTable).insert(eq(userId), eq(amountToUse), eq(TransactionType.USE), anyLong());
    }

    @Test
    @DisplayName("포인트가 부족하면 PointException 발생")
    public void usePoint_ShouldThrowException_WhenInsufficientBalance() throws Exception {
        long userId = 1L;
        long initialPoint = 100L;
        long amountToUse = 500L;
        UserPoint userPoint = new UserPoint(userId, initialPoint, System.currentTimeMillis());

        given(userPointTable.selectById(eq(userId))).willReturn(userPoint);

        assertThrows(PointException.class, () -> pointService.usePoint(userId, amountToUse));
        verify(userPointTable, never()).insertOrUpdate(anyLong(), anyLong());
        verify(pointHistoryTable, never()).insert(anyLong(), anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("최대 잔고 초과 시 PointException 발생")
    public void chargePoint_ShouldThrowException_WhenExceedsMaxBalance() throws Exception {
        long userId = 1L;
        long initialPoint = 9500L;  // 초기 잔고
        long amountToCharge = 1000L;  // 충전할 포인트 (총 10500으로 초과됨)
        UserPoint userPoint = new UserPoint(userId, initialPoint, System.currentTimeMillis());

        given(userPointTable.selectById(eq(userId))).willReturn(userPoint);

        // when & then
        PointException exception = assertThrows(PointException.class, () -> pointService.chargePoint(userId, amountToCharge));
        assertThat(exception.getMessage()).isEqualTo("잔고는 최대 " + MAX_BALANCE + " 포인트를 초과할 수 없습니다.");

        // 포인트 업데이트가 호출되지 않아야 함
        verify(userPointTable, never()).insertOrUpdate(anyLong(), anyLong());
        // 내역이 기록되지 않아야 함
        verify(pointHistoryTable, never()).insert(anyLong(), anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("포인트 충전 시 최대 잔고를 초과하지 않으면 정상 처리")
    public void chargePoint_ShouldUpdate_WhenNotExceedsMaxBalance() throws Exception {
        long userId = 1L;
        long initialPoint = 8000L;  // 초기 잔고
        long amountToCharge = 1000L;  // 충전할 포인트
        UserPoint userPoint = new UserPoint(userId, initialPoint, System.currentTimeMillis());

        given(userPointTable.selectById(eq(userId))).willReturn(userPoint);

        UserPoint updatedUserPoint = new UserPoint(userId, initialPoint + amountToCharge, System.currentTimeMillis());
        given(userPointTable.insertOrUpdate(eq(userId), eq(initialPoint + amountToCharge)))
                .willReturn(updatedUserPoint);

        UserPoint result = pointService.chargePoint(userId, amountToCharge);

        assertThat(result).isEqualTo(updatedUserPoint);
        verify(userPointTable).insertOrUpdate(eq(userId), eq(initialPoint + amountToCharge));
        verify(pointHistoryTable).insert(eq(userId), eq(amountToCharge), eq(TransactionType.CHARGE), anyLong());
    }

    @Test
    @DisplayName("포인트 사용 시 잔고가 충분하면 정상 처리")
    public void usePoint_ShouldUpdate_WhenSufficientBalance() throws Exception {
        long userId = 1L;
        long initialPoint = 500L;
        long amountToUse = 300L;
        UserPoint userPoint = new UserPoint(userId, initialPoint, System.currentTimeMillis());

        given(userPointTable.selectById(eq(userId))).willReturn(userPoint);

        UserPoint updatedUserPoint = new UserPoint(userId, initialPoint - amountToUse, System.currentTimeMillis());
        given(userPointTable.insertOrUpdate(eq(userId), eq(initialPoint - amountToUse)))
                .willReturn(updatedUserPoint);

        UserPoint result = pointService.usePoint(userId, amountToUse);

        assertThat(result).isEqualTo(updatedUserPoint);
        verify(userPointTable).insertOrUpdate(eq(userId), eq(initialPoint - amountToUse));
        verify(pointHistoryTable).insert(eq(userId), eq(amountToUse), eq(TransactionType.USE), anyLong());
    }

    @Test
    @DisplayName("동시성 테스트 - 포인트 충전 및 사용")
    public void concurrent_PointOperations_ShouldBeHandledSequentially() throws Exception {
        long userId = 1L;
        long initialPoint = 1000L;
        UserPoint userPoint = new UserPoint(userId, initialPoint, System.currentTimeMillis());

        given(userPointTable.selectById(eq(userId))).willReturn(userPoint);
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
