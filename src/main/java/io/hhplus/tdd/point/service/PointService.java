package io.hhplus.tdd.point.service;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.PointHistory;
import io.hhplus.tdd.point.TransactionType;
import io.hhplus.tdd.point.UserPoint;
import io.hhplus.tdd.point.exception.PointException;
import io.hhplus.tdd.point.repository.PointHistoryRepository;
import io.hhplus.tdd.point.repository.UserPointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
public class PointService {

    private final UserPointRepository userPointTable;
    private final PointHistoryRepository pointHistoryTable;
    private final Lock lock = new ReentrantLock();

    public UserPoint chargePoint(long userId, long amount) {
        UserPoint updatedUserPoint;
        long updateAmount = 0L;

        lock.lock();
        try {
            UserPoint userPoint = userPointTable.selectById(userId);
            updateAmount = userPoint.point()  + amount;

            // 포인트 충전
            updatedUserPoint = userPointTable.insertOrUpdate(userId, updateAmount);

            // 포인트 충전 내역 기록
            pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, System.currentTimeMillis());
            return updatedUserPoint;
        } finally {
            lock.unlock();
        }

    }

    public UserPoint usePoint(long userId, long amount) {
        UserPoint updatedUserPoint;
        long updateAmount = 0;

        lock.lock();
        try {
            UserPoint userPoint = userPointTable.selectById(userId);
            updateAmount = userPoint.point()  -  amount;

            if (updateAmount < amount) {
                throw new PointException("잔액이 부족합니다.");
            }
            // 포인트 사용
            updatedUserPoint = userPointTable.insertOrUpdate(userId, updateAmount);
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
