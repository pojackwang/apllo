package com.gofobao.framework.scheduler;

import com.github.wenhao.jpa.Specifications;
import com.gofobao.framework.borrow.entity.Borrow;
import com.gofobao.framework.borrow.service.BorrowService;
import com.gofobao.framework.common.data.DataObject;
import com.gofobao.framework.common.data.LtSpecification;
import com.gofobao.framework.helper.DateHelper;
import com.gofobao.framework.repayment.biz.LoanBiz;
import com.gofobao.framework.repayment.biz.RepaymentBiz;
import com.gofobao.framework.repayment.entity.BorrowRepayment;
import com.gofobao.framework.repayment.service.BorrowRepaymentService;
import com.gofobao.framework.repayment.vo.request.VoRepayReq;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by Zeke on 2017/7/10.
 */
@Component
@Slf4j
public class BorrowRepayScanduler {

    @Autowired
    private BorrowRepaymentService borrowRepaymentService;

    @Autowired
    private BorrowService borrowService;

    @Autowired
    private RepaymentBiz repaymentBiz;

    @Autowired
    private LoanBiz loanBiz;

    //@Scheduled(cron = "0 50 23 * * ? ")
    public void process() {
        borrowRepay();
    }

    /**
     * 发送还款短信站内信提醒
     */
    public void sendRepayMassage() {
        //规则：
    }

    //@Transactional(rollbackOn = Exception.class)
    private void borrowRepay() {
        log.info("进入批次还款任务调度");
        Specification<BorrowRepayment> brs = Specifications
                .<BorrowRepayment>and()
                .eq("status", 0)
                .predicate(new LtSpecification("repayAt",
                        new DataObject(DateHelper.beginOfDate(DateHelper.addDays(new Date(),
                                1)))))
                .build();
        List<BorrowRepayment> borrowRepaymentList = null;
        List<Borrow> borrowList = null;
        List<Long> borrowIds = null;
        Specification<Borrow> bs = null;
        Pageable pageable = null;
        int pageIndex = 0;
        int pageSize = 50;
        do {
            borrowIds = new ArrayList<>();
            pageable = new PageRequest(pageIndex++, pageSize, new Sort(Sort.Direction.ASC, "id"));
            borrowRepaymentList = borrowRepaymentService.findList(brs, pageable);
            for (BorrowRepayment borrowRepayment : borrowRepaymentList) {
                borrowIds.add(borrowRepayment.getBorrowId());
            }

            bs = Specifications
                    .<Borrow>and()
                    .in("id", borrowIds.toArray())
                    .build();
            borrowList = borrowService.findList(bs);
            for (BorrowRepayment borrowRepayment : borrowRepaymentList) {
                for (Borrow borrow : borrowList) {
                    if (borrow.getType().intValue() != 1) {
                        continue;
                    }
                    if (String.valueOf(borrowRepayment.getBorrowId()).equals(String.valueOf(borrow.getId()))) {
                        try {
                            VoRepayReq voRepayReq = new VoRepayReq();
                            voRepayReq.setRepaymentId(borrowRepayment.getId());
                            voRepayReq.setUserId(borrowRepayment.getUserId());
                            voRepayReq.setInterestPercent(1d);
                            voRepayReq.setIsUserOpen(false);
                            repaymentBiz.newRepay(voRepayReq);
                        } catch (Exception e) {
                            log.error("borrowRepayScheduler error:", e);
                        }
                    }
                }
            }

        } while (borrowRepaymentList.size() >= pageSize);

    }


    /**
     * 每天早上9点 调度还款当日所需要还款的的官标
     */
    //@Scheduled(cron = "0 00 23 * * ? ")
    // @Transactional(rollbackOn = Exception.class)
    public void todayRepayment() {
        log.info("自动还款调度启动");
        loanBiz.timingRepayment(new Date());
    }
}
