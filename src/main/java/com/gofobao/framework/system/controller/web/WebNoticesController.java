package com.gofobao.framework.system.controller.web;

import com.gofobao.framework.core.vo.VoBaseResp;
import com.gofobao.framework.security.contants.SecurityContants;
import com.gofobao.framework.system.biz.NoticesBiz;
import com.gofobao.framework.system.vo.request.VoNoticesReq;
import com.gofobao.framework.system.vo.request.VoNoticesTranReq;
import com.gofobao.framework.system.vo.response.VoViewNoticesInfoWarpRes;
import com.gofobao.framework.system.vo.response.VoViewUserNoticesWarpRes;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Created by admin on 2017/6/28.
 */
@Api(description = "pc:站内信")
@RestController
@RequestMapping("notices/pc/v2")
@SuppressWarnings("all")
public class WebNoticesController {

    @Autowired
    private NoticesBiz noticesBiz;

    @ApiOperation("站内信列表")
    @RequestMapping("/list/{pageIndex}/{pageSize}")
    public ResponseEntity<VoViewUserNoticesWarpRes> pcList(@RequestAttribute(SecurityContants.USERID_KEY) Long userId,
                                                           @PathVariable("pageIndex") Integer pageIndex,
                                                           @PathVariable("pageSize") Integer pageSize) {
        VoNoticesReq voNoticesReq = new VoNoticesReq();
        voNoticesReq.setUserId(userId);
        voNoticesReq.setPageIndex(pageIndex);
        voNoticesReq.setPageSize(pageSize);
        return noticesBiz.list(voNoticesReq);
    }


    @ApiOperation("站内信内容")
    @RequestMapping(path = "v2/info/{noticesId}", method = RequestMethod.GET)
    public ResponseEntity<VoViewNoticesInfoWarpRes> list(@PathVariable Long noticesId,
                                                         @RequestAttribute(SecurityContants.USERID_KEY) Long userId) {
        VoNoticesReq voNoticesReq = new VoNoticesReq();
        voNoticesReq.setUserId(userId);
        voNoticesReq.setId(noticesId);
        return noticesBiz.info(voNoticesReq);
    }

    @ApiOperation("批量删除")
    @RequestMapping("/delete")
    public ResponseEntity<VoBaseResp> delete(@RequestAttribute(SecurityContants.USERID_KEY) Long userId,
                                             VoNoticesTranReq voNoticesTranReq) {
        voNoticesTranReq.setUserId(userId);
        return noticesBiz.delete(voNoticesTranReq);

    }

    @ApiModelProperty("标记已读")
    @RequestMapping("/update")
    public ResponseEntity<VoBaseResp> update(@RequestAttribute(SecurityContants.USERID_KEY) Long userId,
                                             VoNoticesTranReq voNoticesTranReq) {
        voNoticesTranReq.setUserId(userId);
        return noticesBiz.update(voNoticesTranReq);
    }


}
