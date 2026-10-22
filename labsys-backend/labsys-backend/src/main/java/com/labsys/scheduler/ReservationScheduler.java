package com.labsys.scheduler;

import com.labsys.service.ReservationService;
import com.labsys.service.TicketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReservationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservationScheduler.class);

    private final ReservationService reservationService;
    private final TicketService ticketService;

    public ReservationScheduler(ReservationService reservationService, TicketService ticketService) {
        this.reservationService = reservationService;
        this.ticketService = ticketService;
    }

    /**
     * 定时释放超时未归还的设备。
     * fixedDelay 而不是 fixedRate：上一次跑完再等一段时间，避免上一轮卡住时任务堆积。
     * 整段包 try/catch：定时任务抛异常会让后续调度受影响，而这件事失败了也不该拖垮服务。
     */
    @Scheduled(fixedDelayString = "${labsys.overdue-check-ms:60000}", initialDelay = 10000)
    public void releaseOverdue() {
        try {
            int n = reservationService.releaseOverdue();
            if (n > 0) {
                log.info("超时未归还自动释放：{} 条", n);
            }
        } catch (Exception e) {
            log.error("超时释放任务执行失败", e);
        }
    }

    /** 4-5 超时催办：长时间没人受理/处理的工单打上 overdue 标记，前端把它标红 */
    @Scheduled(fixedDelayString = "${labsys.ticket-check-ms:60000}", initialDelay = 15000)
    public void markOverdueTickets() {
        try {
            int n = ticketService.markOverdue();
            if (n > 0) {
                log.info("工单超时催办：{} 条", n);
            }
        } catch (Exception e) {
            log.error("工单超时任务执行失败", e);
        }
    }
}
