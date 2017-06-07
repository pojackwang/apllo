package com.gofobao.framework.tender.biz;

import com.gofobao.framework.core.vo.VoBaseResp;
import com.gofobao.framework.tender.vo.request.VoCreateTenderReq;
import com.gofobao.framework.tender.vo.request.VoTransferTenderReq;
import com.gofobao.framework.tender.vo.response.VoBorrowTenderUserWarpListRes;
import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * Created by Zeke on 2017/5/31.
 */
public interface TenderBiz {
    /**
     * 创建投标
     * @param voCreateTenderReq
     * @return
     * @throws Exception
     */
    Map<String,Object> createTender(VoCreateTenderReq voCreateTenderReq) throws Exception;

    /**
     * 投标
     * @param voCreateTenderReq
     * @return
     */
    ResponseEntity<VoBaseResp> tender(VoCreateTenderReq voCreateTenderReq);

    /**
     * 债权转让
     * @param voTransferTenderReq
     * @return
     */
    ResponseEntity<VoBaseResp> transferTender(VoTransferTenderReq voTransferTenderReq);


    /**
     * 投标用户
     * @param borrowId
     * @return
     */
    ResponseEntity<VoBorrowTenderUserWarpListRes> findBorrowTenderUser(Long borrowId);


}
