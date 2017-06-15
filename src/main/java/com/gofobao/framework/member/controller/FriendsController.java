package com.gofobao.framework.member.controller;

import com.gofobao.framework.member.biz.BrokerBounsBiz;
import com.gofobao.framework.member.vo.request.VoFriendsReq;
import com.gofobao.framework.member.vo.response.VoViewFriendsTenderInfoWarpRes;
import com.gofobao.framework.member.vo.response.VoViewInviteAwardStatisticsWarpRes;
import com.gofobao.framework.member.vo.response.VoViewInviteFriendersWarpRes;
import com.gofobao.framework.security.contants.SecurityContants;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Created by admin on 2017/6/7.
 */

@Slf4j
@RequestMapping("/invite")
@Api(description = "我的邀请")
@RestController
public class FriendsController {

    @Autowired
    private BrokerBounsBiz brokerBounsBiz;

    @ApiOperation("邀请好友列表")
    @GetMapping("v2/list")
    public ResponseEntity<VoViewInviteFriendersWarpRes> list(@RequestAttribute(SecurityContants.USERID_KEY) Long userId,
                                                             @ModelAttribute VoFriendsReq voFriendsReq) {
        voFriendsReq.setUserId(userId);
        return brokerBounsBiz.list(voFriendsReq);
    }

    @ApiOperation("邀请好友列表")
    @GetMapping("v2/statistic")
    public ResponseEntity<VoViewInviteAwardStatisticsWarpRes> statistic(@RequestAttribute(SecurityContants.USERID_KEY) Long userId) {
        return brokerBounsBiz.statistic(userId);
    }

    @ApiOperation("邀请好友列表")
    @GetMapping("v2/first/tender/list")
    public ResponseEntity<VoViewFriendsTenderInfoWarpRes> firstTenderList(@RequestAttribute(SecurityContants.USERID_KEY) Long userId,
                                                                          @ModelAttribute VoFriendsReq voFriendsReq) {
        voFriendsReq.setUserId(userId);
        return brokerBounsBiz.firstTender(voFriendsReq);
    }

    @ApiOperation("分享注册邀请码")
    @GetMapping("v2/shareRegister/page")
    public ResponseEntity<Map<String, String>> shareRegister(/*@RequestAttribute(SecurityContants.USERID_KEY) Long userId*/) {
        Long userId=901L;
        return brokerBounsBiz.shareRegister(userId);
    }


}