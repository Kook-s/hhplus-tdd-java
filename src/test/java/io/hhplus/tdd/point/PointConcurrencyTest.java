package io.hhplus.tdd.point;

import io.hhplus.tdd.point.exception.PointException;
import io.hhplus.tdd.point.repository.PointHistoryRepository;
import io.hhplus.tdd.point.repository.UserPointRepository;
import io.hhplus.tdd.point.service.PointService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PointConcurrencyTest {

    @Autowired
    private PointService pointService;

    @Autowired
    private UserPointRepository userPointRepository;

    @Autowired
    private PointHistoryRepository pointHistoryRepository;

    private final long userId = 1L;

    @BeforeEach
    void setUp() {
        // 초기 포인트 설정
        userPointRepository.insertOrUpdate(userId, 0L);
    }

    @Test
    void testConcurrentChargeAndUsePoints() throws InterruptedException, ExecutionException {
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        List<Callable<Void>> tasks = new ArrayList<>();

        // 5개의 chargePoint 작업 생성
        for (int i = 0; i < 5; i++) {
            tasks.add(() -> {
                pointService.chargePoint(userId, 1000);
                return null;
            });
        }

        // 5개의 usePoint 작업 생성
        for (int i = 0; i < 5; i++) {
            tasks.add(() -> {
                try {
                    pointService.usePoint(userId, 500);
                } catch (PointException e) {
                    throw new PointException(e.getMessage());
                }
                return null;
            });
        }

        // 작업 실행
        List<Future<Void>> results = executorService.invokeAll(tasks);

        // 모든 작업이 완료될 때까지 대기
        for (Future<Void> result : results) {
            result.get();
        }

        // 최종 포인트 잔액 확인
        UserPoint finalUserPoint = pointService.getPoint(userId);
        assertThat(finalUserPoint.point()).isBetween(0L, 5000L);

        // ExecutorService 종료
        executorService.shutdown();
    }
}