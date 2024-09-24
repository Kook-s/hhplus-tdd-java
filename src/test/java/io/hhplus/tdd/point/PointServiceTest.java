package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;
@DisplayName("포인트 서비스 로직 테스트")
@SpringBootTest
class PointServiceTest {

    @InjectMocks
    private PointService pointService;

    @Mock
    private UserPointTable userPointTable;

    @Mock
    private PointHistoryTable pointHistoryTable;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("ID로 유저를 조회할 때 존재하는 경우")
    public void selectUserByIdTest() throws Exception{
        //given
        long id = 1;
        UserPoint userPoint = new UserPoint(id, 0, System.currentTimeMillis());
        given(userPointTable.selectById(id)).willReturn(userPoint);

        //when
        UserPoint result = pointService.getPoint(id);

        //then
        assertThat(result.id()).isEqualTo(id);
    }

    @Test
    public void chargePointTest() {
        //given
        long id = 1;
        long amount = 50000;
        long addAmount = 30000;
        UserPoint userPoint = new UserPoint(id, amount, System.currentTimeMillis());

        given(userPointTable.selectById(id)).willReturn(userPoint);
        given(userPointTable.insertOrUpdate(anyLong(), anyLong())).willReturn(new UserPoint(id, userPoint.point()+addAmount, 0));

        //when
        UserPoint result = pointService.chargePoint(id, amount);

        //then
        assertThat(result.id()).isEqualTo(id);
        assertThat(result.point()).isEqualTo(amount+addAmount);
    }

}



