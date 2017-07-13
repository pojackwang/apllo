package com.gofobao.framework.repayment.biz;

import com.gofobao.framework.api.model.batch_bail_repay.BailRepay;
import com.gofobao.framework.api.model.batch_repay.Repay;
import com.gofobao.framework.borrow.entity.Borrow;
import com.gofobao.framework.core.vo.VoBaseResp;
import com.gofobao.framework.repayment.vo.request.VoBatchBailRepayReq;
import com.gofobao.framework.repayment.vo.request.VoBatchRepayBailReq;
import com.gofobao.framework.repayment.vo.request.VoThirdBatchLendRepay;
import com.gofobao.framework.repayment.vo.request.VoThirdBatchRepay;
import org.springframework.http.ResponseEntity;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * Created by Zeke on 2017/6/8.
 */
public interface BorrowRepaymentThirdBiz {
    /**
     * 即信批次还款
     *
     * @return
     */
    ResponseEntity<String> thirdBatchRepayCheckCall(HttpServletRequest request, HttpServletResponse response);

    /**
     * 即信批次还款
     *
     * @return
     */
    ResponseEntity<String> thirdBatchRepayRunCall(HttpServletRequest request, HttpServletResponse response);

    /**
     * 即信批次放款  （满标后调用）
     *
     * @param voThirdBatchLendRepay
     * @return
     */
    ResponseEntity<VoBaseResp> thirdBatchLendRepay(VoThirdBatchLendRepay voThirdBatchLendRepay) throws Exception;

    /**
     * 即信批次放款  （满标后调用）
     *
     * @return
     */
    ResponseEntity<String> thirdBatchLendRepayCheckCall(HttpServletRequest request, HttpServletResponse response);

    /**
     * 即信批次放款  （满标后调用）
     *
     * @return
     */
    ResponseEntity<String> thirdBatchLendRepayRunCall(HttpServletRequest request, HttpServletResponse response);

    /**
     * 批次担保账户代偿参数检查回调
     */
    ResponseEntity<String> thirdBatchBailRepayCheckCall(HttpServletRequest request, HttpServletResponse response);

    /**
     * 批次担保账户代偿业务处理回调
     */
    ResponseEntity<String> thirdBatchBailRepayRunCall(HttpServletRequest request, HttpServletResponse response);

    /**
     * 批次融资人还担保账户垫款参数检查回调
     *
     * @param request
     * @param response
     */
    ResponseEntity<String> thirdBatchRepayBailCheckCall(HttpServletRequest request, HttpServletResponse response);

    /**
     * 批次融资人还担保账户垫款业务处理回调
     *
     * @param request
     * @param response
     */
    ResponseEntity<String> thirdBatchRepayBailRunCall(HttpServletRequest request, HttpServletResponse response);

    /**
     * 获取存管 收到还款 数据集合
     *
     * @param borrow
     * @param order
     * @param interestPercent
     * @param borrowAccountId 借款方即信存管账户id
     * @param lateDays
     * @param lateInterest
     * @return
     * @throws Exception
     */
    void receivedRepay(List<Repay> repayList, Borrow borrow, String borrowAccountId, int order, double interestPercent, int lateDays, int lateInterest) throws Exception;

    /**
     * 获取即信还款集合
     *
     * @param voThirdBatchRepay
     * @return
     * @throws Exception
     */
    List<Repay> getRepayList(VoThirdBatchRepay voThirdBatchRepay) throws Exception;

    /**
     * 获取担保人代偿集合
     *
     * @param voBatchBailRepayReq
     * @return
     * @throws Exception
     */
    List<BailRepay> getBailRepayList(VoBatchBailRepayReq voBatchBailRepayReq) throws Exception;
}
