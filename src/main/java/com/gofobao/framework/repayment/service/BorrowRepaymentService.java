package com.gofobao.framework.repayment.service;

import com.gofobao.framework.borrow.entity.Borrow;
import com.gofobao.framework.collection.vo.request.VoCollectionOrderReq;
import com.gofobao.framework.collection.vo.request.VoOrderDetailReq;
import com.gofobao.framework.collection.vo.response.VoViewCollectionOrderListRes;
import com.gofobao.framework.collection.vo.response.VoViewOrderDetailRes;
import com.gofobao.framework.repayment.entity.BorrowRepayment;
import com.gofobao.framework.repayment.vo.request.VoInfoReq;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

/**
 * Created by admin on 2017/6/1.
 */
public interface BorrowRepaymentService {

    /**
     * 还款计划列表
     *
     * @param voCollectionOrderReq
     * @return
     */
    VoViewCollectionOrderListRes repaymentList(VoCollectionOrderReq voCollectionOrderReq);


    /**
     * 还款详情
     *
     * @param voInfoReq
     * @return
     */
    VoViewOrderDetailRes info(VoInfoReq voInfoReq);

    BorrowRepayment save(BorrowRepayment borrowRepayment);

    BorrowRepayment insert(BorrowRepayment borrowRepayment);

    BorrowRepayment updateById(BorrowRepayment borrowRepayment);

    BorrowRepayment findByIdLock(Long id);

    BorrowRepayment findById(Long id);

    List<BorrowRepayment> findList(Specification<BorrowRepayment> specification);

    List<BorrowRepayment> findList(Specification<BorrowRepayment> specification, Sort sort);

    List<BorrowRepayment> findList(Specification<BorrowRepayment> specification, Pageable pageable);

    long count(Specification<BorrowRepayment> specification);

}
