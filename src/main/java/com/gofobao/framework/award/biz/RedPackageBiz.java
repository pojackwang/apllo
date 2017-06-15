package com.gofobao.framework.award.biz;

import com.gofobao.framework.award.vo.request.VoOpenRedPackageReq;
import com.gofobao.framework.award.vo.request.VoRedPackageReq;
import com.gofobao.framework.award.vo.response.VoViewOpenRedPackageWarpRes;
import com.gofobao.framework.award.vo.response.VoViewRedPackageWarpRes;
import org.springframework.http.ResponseEntity;

/**
 * Created by admin on 2017/6/7.
 */
public interface RedPackageBiz {

    /**
     *红包列表
     * @param voRedPackageReq
     * @return
     */
    ResponseEntity<VoViewRedPackageWarpRes> list(VoRedPackageReq voRedPackageReq);

    /**
     * 拆红包
     * @param voOpenRedPackageReq
     * @return
     */
    ResponseEntity<VoViewOpenRedPackageWarpRes> openRedPackage(VoOpenRedPackageReq voOpenRedPackageReq);
}