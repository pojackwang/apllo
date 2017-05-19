package com.gofobao.framework.borrow.service;

import com.gofobao.framework.borrow.vo.request.VoBorrowByIdReq;
import com.gofobao.framework.borrow.vo.request.VoBorrowListReq;
import com.gofobao.framework.borrow.vo.response.VoBorrowByIdRes;
import com.gofobao.framework.borrow.vo.response.VoBorrowTenderUserRes;
import com.gofobao.framework.borrow.vo.response.VoViewBorrowListRes;

import java.util.List;

/**
 * Created by admin on 2017/5/17.
 */
public interface BorrowService {

    List<VoViewBorrowListRes> findAll(VoBorrowListReq voBorrowListReq);



    VoBorrowByIdRes findByBorrowId(VoBorrowByIdReq req);


}
