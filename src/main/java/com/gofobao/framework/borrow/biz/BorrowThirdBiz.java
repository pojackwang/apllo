package com.gofobao.framework.borrow.biz;

import com.gofobao.framework.borrow.vo.request.VoCreateThirdBorrowReq;
import com.gofobao.framework.core.vo.VoBaseResp;
import org.springframework.http.ResponseEntity;

/**
 * Created by Zeke on 2017/6/1.
 */
public interface BorrowThirdBiz {
    ResponseEntity<VoBaseResp> createThirdBorrow(VoCreateThirdBorrowReq voCreateThirdBorrowReq);
}