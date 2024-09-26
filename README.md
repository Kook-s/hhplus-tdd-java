## TDD HomeWork

#### 요구 사항

- PATCH  `/point/{id}/charge` : 포인트를 충전한다.
- PATCH `/point/{id}/use` : 포인트를 사용한다.
- GET `/point/{id}` : 포인트를 조회한다.
- GET `/point/{id}/histories` : 포인트 내역을 조회한다.
- 잔고가 부족할 경우, 포인트 사용은 실패하여야 합니다.
- 동시에 여러 건의 포인트 충전, 이용 요청이 들어올 경우 순차적으로 처리되어야 합니다.
  <br>


 #### `Step 1`

- ✅ 포인트 충전, 사용에 대한 정책 추가 (잔고 부족, 최대 잔고 등)
-  동시에 여러 요청이 들어오더라도 순서대로 (혹은 한번에 하나의 요청씩만) 제어될 수 있도록 리팩토링
-  동시성 제어에 대한 통합 테스트 작성
  

  #### `Step 2`

- 동시성 제어 방식에 대한 분석 및 보고서 작성 ( **README.md** )

 ### 1. 동시성 (Concurrency)

- #### 동시성: 기존 코드처럼 여러 스레드가 동시에 작업을 수행하려고 할 때, 서로의 작업에 간섭하여 문제가 발생할 수 있습니다.

- 기존 코드에서 동시성 문제는 여러 사용자가 동시에 포인트를 충전하거나 사용할 때 발생할 수 있습니다. 예를 들어, 두 명의 사용자가 동시에 포인트를 사용하려고 할 때, 두 요청이 동시에 실행되면서 동일한 포인트를 두 번 차감하는 문제가 발생할 수 있습니- 다. 이러한 상황을 방지하기 위해 동시성 제어가 필요합니다.

```java
public UserPoint usePoint(long userId, long amount) {
    // 동시성 문제 발생 가능
    UserPoint userPoint = userPointTable.selectById(userId);
    long updateAmount = userPoint.point() - amount;

    if (updateAmount < 0) {
        throw new PointException("잔액이 부족합니다.");
    }

    return userPointTable.insertOrUpdate(userId, updateAmount);
}
```
- 위의 코드는 여러 스레드가 동시에 usePoint를 호출하면, 동일한 userPoint를 가져와 동일한 포인트에서 각각 차감하여 부정확한 결과를 초래할 수 있습니다.
  

### 2. 순차성 (Sequentiality)

- #### 순차성: 개선된 코드처럼 사용자별로 Lock을 사용하여, 동일 사용자의 요청이 순차적으로 처리되도록 하여 데이터의 일관성을 유지합니다.

- 개선된 코드에서는 사용자별로 Lock을 걸어 한 번에 한 사용자의 요청만 순차적으로 처리되도록 하였습니다. 이렇게 함으로써 같은 사용자의 여러 요청이 서로 순차적으로 처리되어 포인트 데이터가 올바르게 업데이트됩니다.

```java
public UserPoint usePoint(long userId, long amount) {
    Lock lock = getUserLock(userId); // 사용자별 Lock 획득
    lock.lock();
    try {
        // 순차적으로 포인트 사용 처리
        UserPoint userPoint = userPointTable.selectById(userId);
        long updateAmount = userPoint.point() - amount;

        if (updateAmount < 0) {
            throw new PointException("잔액이 부족합니다.");
        }

        return userPointTable.insertOrUpdate(userId, updateAmount);
    } finally {
        lock.unlock(); // 작업 완료 후 Lock 해제
    }
}
```
- 위의 코드에서는 lock.lock()과 lock.unlock()을 통해, 한 사용자의 usePoint 요청이 처리될 때 다른 요청이 동시에 실행되지 않도록 보장합니다. 따라서 여러 요청이 들어와도 각 사용자의 포인트 수정이 순차적으로 이루어집니다.

 
 #### 커밋태그

| 태그       | 설명                                 |
|:---------|:----------------------------------------|
| feat     | 새로운 기능 추가                          |
| fix      | 버그 수정                                 |
| refactor | 코드 리팩토링                             |
| comment  | 주석 추가(코드 변경 X) 혹은 오타 수정      |
| docs     | README와 같은 문서 수정     |
| merge    | merge                                      |
| rename   | 파일, 폴더 수정, 삭제 혹은 이동            |
| report   | 해당 주차별 과제 등록 및 풀이               |
| chore    | 설정 변경                                   |
