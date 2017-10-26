package com.gofobao.framework.as.bix;

import com.gofobao.framework.as.bix.impl.CashStatementBizImpl;

import java.util.Date;

/**
 * 提现对账
 *
 * @author Administrator
 */
public interface CashStatementBiz {
    /**
     * 离线提现匹配
     *
     * @param userId   用户编号
     * @param date     对账时间
     * @param cashType 提现记录
     * @return
     */
    boolean offlineStatement(Long userId, Date date, CashStatementBizImpl.CashType cashType) throws Exception;


    /**
     * 实时提现匹配
     *
     * @param userId   用户编号
     * @param date     对账时间
     * @param cashType 提现记录
     * @param force    是否前置对账
     * @return
     */
    boolean onlineStatement(Long userId, Date date, CashStatementBizImpl.CashType cashType, boolean force) throws Exception;

}
