package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class PointService {

    @Autowired
    private UserPointTable userPointTable;
    @Autowired
    private PointHistoryTable pointHistoryTable;
    private final Lock lock = new ReentrantLock();

    public UserPoint chargePoint(long userId, long amount) {
        UserPoint updatedUserPoint;
         try {
            lock.lock();

            // 포인트 충전
            updatedUserPoint = userPointTable.insertOrUpdate(userId, getCurrentPoint(userId) + amount);
            // 포인트 충전 내역 기록
            pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, System.currentTimeMillis());
        } finally {
            lock.unlock();
        }

        return updatedUserPoint;
    }

    public UserPoint usePoint(long userId, long amount) {
        UserPoint updatedUserPoint;
        try {
            lock.lock();
            long currentPoint = getCurrentPoint(userId);
            if (currentPoint < amount) {
                throw new IllegalArgumentException("잔액이 부족합니다.");
            }
            // 포인트 사용
            updatedUserPoint = userPointTable.insertOrUpdate(userId, currentPoint - amount);
            // 포인트 사용 내역 기록
            pointHistoryTable.insert(userId, amount, TransactionType.USE, System.currentTimeMillis());

        } finally {
            lock.unlock();
        }
        return updatedUserPoint;
    }

    public UserPoint getPoint(long userId) {
        return userPointTable.selectById(userId);
    }

    public List<PointHistory> getHistory(long userId) {
        return pointHistoryTable.selectAllByUserId(userId);
    }

    private long getCurrentPoint(long userId) {
        return userPointTable.selectById(userId).point();
    }

}
