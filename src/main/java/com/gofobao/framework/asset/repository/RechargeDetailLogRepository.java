package com.gofobao.framework.asset.repository;

import com.gofobao.framework.asset.entity.RechargeDetailLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Date;
import java.util.List;

/**
 * Created by Max on 17/6/7.
 */
public interface RechargeDetailLogRepository extends JpaRepository<RechargeDetailLog, Long> {
    RechargeDetailLog findTopBySeqNoAndDel(String seqNo, int del);

    RechargeDetailLog findTopByIdAndDel(Long rechargeId, int del);

    List<RechargeDetailLog> findByUserIdAndDel(Long userId, int del, Pageable pageable);

    List<RechargeDetailLog> findByUserIdAndDelAndCreateTimeBetweenAndState(long userId, int del, Date startTime, Date endTime, int state);
}