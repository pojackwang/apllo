package com.gofobao.framework.award.controller;

import com.gofobao.framework.award.biz.VirtualBiz;
import com.gofobao.framework.award.vo.response.VoViewVirtualBorrowResWarpRes;
import com.gofobao.framework.award.vo.response.VoViewVirtualStatisticsWarpRes;
import com.gofobao.framework.award.vo.response.VoViewVirtualTenderResWarpRes;
import com.gofobao.framework.security.contants.SecurityContants;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Created by admin on 2017/6/8.
 */

@RestController
@RequestMapping(value = "/virtual")
@Api(description = "体验金")
public class VirtualController {

    @Autowired
    private VirtualBiz virtualBiz;

    @PostMapping(value = "/v2/statistics")
    public ResponseEntity<VoViewVirtualStatisticsWarpRes> query(@RequestAttribute(SecurityContants.USERID_KEY) Long userId) {
        return virtualBiz.query(userId);
    }

    @PostMapping(value = "/v2/userTenderList")
    public ResponseEntity<VoViewVirtualTenderResWarpRes> userTenderList(@RequestAttribute(SecurityContants.USERID_KEY) Long userId) {
        return virtualBiz.userTenderList(userId);
    }


    @PostMapping(value = "/v2/list")
    public ResponseEntity<VoViewVirtualBorrowResWarpRes> list(@RequestAttribute(SecurityContants.USERID_KEY) Long userId) {
        return virtualBiz.list();
    }


}
