package io.hhplus.tdd.point.service;

import io.hhplus.tdd.point.PointHistory;
import io.hhplus.tdd.point.TransactionType;
import io.hhplus.tdd.point.UserPoint;
import io.hhplus.tdd.point.exception.PointException;
import io.hhplus.tdd.point.repository.PointHistoryRepository;
import io.hhplus.tdd.point.repository.UserPointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
public class PointService {

    private final UserPointRepository userPointTable;
    private final PointHistoryRepository pointHistoryTable;
    private final Map<Long, Lock> userLocks = new ConcurrentHashMap<>();

    private static final long MAX_BALANCE = 10000L;
    private static final long MINIMUM_AMOUNT = 0L;

    private Lock getUserLock(long userId) {
        return userLocks.computeIfAbsent(userId, k -> new ReentrantLock());
    }

    public UserPoint chargePoint(long userId, long amount) {
        Lock lock = getUserLock(userId);
        lock.lock();
        try {
            UserPoint userPoint = userPointTable.selectById(userId);
            long updateAmount = userPoint.point() + amount;

            // 최대 잔고 초과 여부 확인
            if (updateAmount > MAX_BALANCE) {
                throw new PointException("잔고는 최대 " + MAX_BALANCE + " 포인트를 초과할 수 없습니다.");
            }

            // 포인트 충전
            UserPoint updatedUserPoint = userPointTable.insertOrUpdate(userId, updateAmount);

            // 포인트 충전 내역 기록
            pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, System.currentTimeMillis());
            return updatedUserPoint;
        } finally {
            lock.unlock();
        }
    }

    public UserPoint usePoint(long userId, long amount) {
        Lock lock = getUserLock(userId);
        lock.lock();
        try {
            UserPoint userPoint = userPointTable.selectById(userId);
            long updateAmount = userPoint.point() - amount;

            // 잔고 부족 여부 확인
            if (updateAmount < MINIMUM_AMOUNT) {
                throw new PointException("잔액이 부족합니다.");
            }

            // 포인트 사용
            UserPoint updatedUserPoint = userPointTable.insertOrUpdate(userId, updateAmount);

            // 포인트 사용 내역 기록
            pointHistoryTable.insert(userId, amount, TransactionType.USE, System.currentTimeMillis());
            return updatedUserPoint;
        } finally {
            lock.unlock();
        }
    }

    public UserPoint getPoint(long userId) {
        return userPointTable.selectById(userId);
    }

    public List<PointHistory> getHistory(long userId) {
        return pointHistoryTable.selectAllByUserId(userId);
    }
}
