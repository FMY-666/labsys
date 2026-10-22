package com.labsys.service;

import com.labsys.entity.UserCredit;
import com.labsys.mapper.UserCreditMapper;
import com.labsys.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;

/**
 * 处罚规则的单元测试。
 * 这里不连数据库：用一个 Map 冒充 sys_user_credit 表，把 mapper 的四个方法接上去。
 * 测的是 CreditService 里的规则本身 —— 第几次违规会怎么样、3 次按时归还怎么抵消。
 */
@ExtendWith(MockitoExtension.class)
class CreditServiceTest {

    private static final Long UID = 2L;

    @Mock
    private UserCreditMapper creditMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private com.labsys.mapper.ReservationMapper reservationMapper;

    /** 冒充数据库：userId -> 那一行 */
    private final Map<Long, UserCredit> table = new HashMap<>();

    private CreditService service;

    @BeforeEach
    void setUp() {
        service = new CreditService(creditMapper, userMapper, reservationMapper);

        lenient().when(creditMapper.selectById(any()))
                .thenAnswer(inv -> copy(table.get((Long) inv.getArgument(0))));
        // 必须写成 any(UserCredit.class)：MyBatis-Plus 3.5.17 同时有 insert(T) 和 insert(Collection<T>)，
        // 裸 any() 会让编译器不知道匹配哪个重载
        lenient().when(creditMapper.insert(any(UserCredit.class))).thenAnswer(inv -> {
            UserCredit c = inv.getArgument(0);
            table.put(c.getUserId(), copy(c));
            return 1;
        });
        lenient().when(creditMapper.updateById(any(UserCredit.class))).thenAnswer(inv -> {
            UserCredit c = inv.getArgument(0);
            table.put(c.getUserId(), copy(c));
            return 1;
        });
        lenient().when(creditMapper.clearBanUntil(anyLong())).thenAnswer(inv -> {
            UserCredit c = table.get((Long) inv.getArgument(0));
            if (c != null) {
                c.setBannedUntil(null);
            }
            return 1;
        });
    }

    /** 模拟一次数据库往返：返回副本，避免测试里的对象和“库里”的对象是同一个引用 */
    private UserCredit copy(UserCredit src) {
        if (src == null) {
            return null;
        }
        UserCredit c = new UserCredit();
        c.setUserId(src.getUserId());
        c.setViolations(src.getViolations());
        c.setOnTimeStreak(src.getOnTimeStreak());
        c.setBannedUntil(src.getBannedUntil());
        c.setPermanentBan(src.getPermanentBan());
        c.setUpdatedAt(src.getUpdatedAt());
        return c;
    }

    private UserCredit db() {
        return copy(table.get(UID));
    }

    @Test
    @DisplayName("没违规的人是 100 分、可约 14 小时、不封禁")
    void freshUserIsNormal() {
        UserCredit c = service.getOrCreate(UID);

        assertEquals(100, service.score(c));
        assertEquals(14, service.maxHours(c));
        assertFalse(service.blocked(c));
        assertEquals("NORMAL", service.view(c).get("level"));
    }

    @Test
    @DisplayName("第 1 次违规只是警告：扣到 90 分，但不限制预约")
    void firstViolationIsOnlyWarning() {
        service.applyOverdue(UID);

        UserCredit c = db();
        assertEquals(1, c.getViolations());
        assertNull(c.getBannedUntil());
        assertEquals(0, c.getPermanentBan());
        assertEquals(90, service.score(c));
        assertEquals(14, service.maxHours(c));
        assertFalse(service.blocked(c));
        assertEquals("WARN", service.view(c).get("level"));
    }

    @Test
    @DisplayName("第 2 次违规禁约 24 小时，可约时长降到 3 小时")
    void secondViolationFreezesForOneDay() {
        service.applyOverdue(UID);
        service.applyOverdue(UID);

        UserCredit c = db();
        assertEquals(2, c.getViolations());
        assertNotNull(c.getBannedUntil());
        long minutes = java.time.Duration.between(LocalDateTime.now(), c.getBannedUntil()).toMinutes();
        assertTrue(minutes > 23 * 60 && minutes <= 24 * 60, "禁约时长应接近 24 小时，实际 " + minutes + " 分钟");
        assertEquals(3, service.maxHours(c));
        assertTrue(service.blocked(c));
        assertEquals("FROZEN", service.view(c).get("level"));
    }

    @Test
    @DisplayName("第 4 次违规封禁，并且把禁约时间清空（封禁不再靠时间到期解除）")
    void fourthViolationIsPermanent() {
        for (int i = 0; i < 4; i++) {
            service.applyOverdue(UID);
        }

        UserCredit c = db();
        assertEquals(4, c.getViolations());
        assertEquals(1, c.getPermanentBan());
        assertNull(c.getBannedUntil());
        assertEquals(60, service.score(c));
        assertTrue(service.blocked(c));
        assertEquals("BANNED", service.view(c).get("level"));
    }

    @Test
    @DisplayName("按时归还满 3 次抵掉 1 次违规，计数条归零")
    void threeOnTimeReturnsOffsetOneViolation() {
        service.applyOverdue(UID);

        assertFalse(service.applyOnTime(UID), "第 1 次按时归还只是开始攒");
        assertFalse(service.applyOnTime(UID), "第 2 次继续攒");
        assertTrue(service.applyOnTime(UID), "第 3 次应该触发抵消");

        UserCredit c = db();
        assertEquals(0, c.getViolations());
        assertEquals(0, c.getOnTimeStreak());
        assertEquals(100, service.score(c));
    }

    @Test
    @DisplayName("没有违规时按时归还只在攒次数，不会把违规减成负数")
    void onTimeReturnNeverMakesViolationsNegative() {
        for (int i = 0; i < 6; i++) {
            service.applyOnTime(UID);
        }

        UserCredit c = db();
        assertEquals(0, c.getViolations());
        assertEquals(0, c.getOnTimeStreak());
    }

    @Test
    @DisplayName("违规再多，分数也不会是负数")
    void scoreNeverGoesBelowZero() {
        for (int i = 0; i < 12; i++) {
            service.applyOverdue(UID);
        }

        assertEquals(0, service.score(db()));
    }

    @Test
    @DisplayName("管理员解禁：违规清零、封禁解除、分数回到 100")
    void resetClearsEverything() {
        for (int i = 0; i < 4; i++) {
            service.applyOverdue(UID);
        }
        service.reset(UID);

        UserCredit c = db();
        assertEquals(0, c.getViolations());
        assertEquals(0, c.getPermanentBan());
        assertNull(c.getBannedUntil());
        assertEquals(100, service.score(c));
        assertFalse(service.blocked(c));
    }
}
