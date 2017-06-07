package com.gofobao.framework;

import com.gofobao.framework.api.model.debt_details_query.DebtDetailsQueryResp;
import com.gofobao.framework.borrow.biz.BorrowThirdBiz;
import com.gofobao.framework.borrow.vo.request.VoQueryThirdBorrowList;
import com.gofobao.framework.common.integral.IntegralChangeEntity;
import com.gofobao.framework.common.integral.IntegralChangeEnum;
import com.gofobao.framework.common.rabbitmq.MqConfig;
import com.gofobao.framework.common.rabbitmq.MqHelper;
import com.gofobao.framework.common.rabbitmq.MqQueueEnum;
import com.gofobao.framework.common.rabbitmq.MqTagEnum;
import com.gofobao.framework.helper.DateHelper;
import com.gofobao.framework.helper.project.IntegralChangeHelper;
import com.gofobao.framework.repayment.service.BorrowRepaymentService;
import com.gofobao.framework.repayment.service.impl.LoanServiceImpl;
import com.gofobao.framework.repayment.vo.request.VoLoanListReq;
import com.gofobao.framework.repayment.vo.response.VoViewBudingRes;
import com.gofobao.framework.repayment.vo.response.VoViewRefundRes;
import com.gofobao.framework.repayment.vo.response.VoViewSettleRes;
import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Date;
import java.util.List;

@RunWith(SpringRunner.class)
@SpringBootTest
@Slf4j

public class AplloApplicationTests {

    @Autowired
    MqHelper mqHelper ;


    @Autowired
    private LoanServiceImpl loanService;
    @Autowired
    private BorrowThirdBiz borrowThirdBiz;
    @Autowired
    private IntegralChangeHelper integralChangeHelper;

    @Test
    public void contextLoads() {
    }

    @Test
    public void test(){

        IntegralChangeEntity entity = new IntegralChangeEntity();
        entity.setUserId(901L);
        entity.setValue(1000);
        entity.setType(IntegralChangeEnum.TENDER);
        try {
            integralChangeHelper.integralChange(entity);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
