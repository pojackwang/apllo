package com.gofobao.framework.scheduler;

import com.github.wenhao.jpa.Specifications;
import com.gofobao.framework.common.rabbitmq.MqConfig;
import com.gofobao.framework.common.rabbitmq.MqHelper;
import com.gofobao.framework.common.rabbitmq.MqQueueEnum;
import com.gofobao.framework.common.rabbitmq.MqTagEnum;
import com.gofobao.framework.helper.DateHelper;
import com.gofobao.framework.helper.StringHelper;
import com.gofobao.framework.system.contants.ThirdBatchLogContants;
import com.gofobao.framework.system.entity.ThirdBatchLog;
import com.gofobao.framework.system.service.ThirdBatchLogService;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * Created by Zeke on 2017/7/24.
 */
@Slf4j
@Component
public class DealThirdBatchScheduler {

    final Gson GSON = new GsonBuilder().create();

    @Autowired
    MqHelper mqHelper;

    @Autowired
    private ThirdBatchLogService thirdBatchLogService;

    @Scheduled(cron = "0 30 8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23 * * ? ")
    public void process() {
        //1.查询未处理 参数校验成功的批次 gfb_third_batch_log
        Specification<ThirdBatchLog> tbls = Specifications
                .<ThirdBatchLog>and()
                .eq("state", 1)
                .notIn("type", ThirdBatchLogContants.BATCH_CREDIT_END)
                .build();
        List<ThirdBatchLog> thirdBatchLogList = null;
        int pageSize = 50;
        do {
            thirdBatchLogList = thirdBatchLogService.findList(tbls);
            MqConfig mqConfig = new MqConfig();
            mqConfig.setQueue(MqQueueEnum.RABBITMQ_THIRD_BATCH);
            mqConfig.setTag(MqTagEnum.BATCH_DEAL);
            ImmutableMap<String, String> body = ImmutableMap
                    .of(MqConfig.SOURCE_ID, StringHelper.toString(84),
                            MqConfig.BATCH_NO, StringHelper.toString(114537),
                            MqConfig.MSG_TIME, DateHelper.dateToString(new Date())
                    );

            mqConfig.setMsg(body);
            try {
                log.info(String.format("DealThirdBatchScheduler process send mq %s", GSON.toJson(body)));
                mqHelper.convertAndSend(mqConfig);
            } catch (Throwable e) {
                log.error("DealThirdBatchScheduler process send mq exception", e);
            }
        } while (thirdBatchLogList.size() >= pageSize);

    }
}
