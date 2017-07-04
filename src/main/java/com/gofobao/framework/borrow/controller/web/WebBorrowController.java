package com.gofobao.framework.borrow.controller.web;

import com.gofobao.framework.borrow.biz.BorrowBiz;
import com.gofobao.framework.borrow.biz.BorrowThirdBiz;
import com.gofobao.framework.borrow.vo.request.VoBorrowListReq;
import com.gofobao.framework.borrow.vo.request.VoPcCancelThirdBorrow;
import com.gofobao.framework.borrow.vo.request.VoRegisterOfficialBorrow;
import com.gofobao.framework.borrow.vo.request.VoRepayAllReq;
import com.gofobao.framework.borrow.vo.response.BorrowInfoRes;
import com.gofobao.framework.borrow.vo.response.VoPcBorrowListWarpRes;
import com.gofobao.framework.borrow.vo.response.VoViewBorrowStatisticsWarpRes;
import com.gofobao.framework.borrow.vo.response.VoViewVoBorrowDescWarpRes;
import com.gofobao.framework.core.vo.VoBaseResp;
import com.gofobao.framework.helper.ThymeleafHelper;
import com.gofobao.framework.member.vo.response.VoHtmlResp;
import com.gofobao.framework.repayment.biz.RepaymentBiz;
import com.gofobao.framework.repayment.vo.response.VoViewRepayCollectionLogWarpRes;
import com.gofobao.framework.security.helper.JwtTokenHelper;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.Map;

/**
 * Created by Max on 17/5/16.
 */

@RequestMapping("")
@RestController
@Slf4j
@Api(description = "pc:首页标接口")
@SuppressWarnings("all")
public class WebBorrowController {

    @Autowired
    private BorrowBiz borrowBiz;
    @Autowired
    private ThymeleafHelper thymeleafHelper;
    @Autowired
    private BorrowThirdBiz borrowThirdBiz;

    @Autowired
    private RepaymentBiz repaymentBiz;

    @Autowired
    private JwtTokenHelper jwtTokenHelper;
    @Value("${jwt.header}")
    private String tokenHeader;

    @Value("${jwt.prefix}")
    private String prefix;


    @ApiOperation(value = "pc:首页标列表; type:-1：全部 0：车贷标；1：净值标；2：秒标；4：渠道标 ; 5流转标")
    @GetMapping("pub/pc/borrow/v2/list/{type}/{pageIndex}/{pageSize}")
    public ResponseEntity<VoPcBorrowListWarpRes> pcList(@PathVariable Integer pageIndex,
                                                        @PathVariable Integer pageSize,
                                                        @PathVariable Integer type) {
        VoBorrowListReq voBorrowListReq = new VoBorrowListReq();
        voBorrowListReq.setPageIndex(pageIndex);
        voBorrowListReq.setPageSize(pageSize);
        voBorrowListReq.setType(type);
        return borrowBiz.pcFindAll(voBorrowListReq);
    }


    @ApiOperation("标信息")
    @GetMapping("pub/pc/borrow/v2/info/{borrowId}")
    public ResponseEntity<BorrowInfoRes> pcgetByBorrowId(@PathVariable Long borrowId) {
        return borrowBiz.info(borrowId);
    }


    @ApiOperation("pc：标简介")
    @GetMapping("pub/pc/borrow/v2/desc/{borrowId}")
    public ResponseEntity<VoViewVoBorrowDescWarpRes> pcDesc(@PathVariable Long borrowId) {
        return borrowBiz.desc(borrowId);
    }


    @ApiOperation(value = "pc:标合同")
    @GetMapping(value = "pub/pc/borrow/v2/borrowProtocol/{borrowId}")
    public ResponseEntity<String> pcTakeRatesDesc(HttpServletRequest request, @PathVariable Long borrowId) {
        Long userId = 0L;
        String authToken = request.getHeader(this.tokenHeader);
        if (!StringUtils.isEmpty(authToken) && (authToken.contains(prefix))) {
            authToken = authToken.substring(7);
        }
        String username = jwtTokenHelper.getUsernameFromToken(authToken);
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            userId = jwtTokenHelper.getUserIdFromToken(authToken);
        }
        String content = "";
        try {
            Map<String, Object> paramMaps = borrowBiz.pcContract(borrowId, userId);
            content = thymeleafHelper.build("borrowProtcol", paramMaps);
        } catch (Exception e) {
            log.info(" WebBorrowController -> pcTakeRatesDesc  fail", e);
            content = thymeleafHelper.build("load_error", null);
        }
        return ResponseEntity.ok(content);
    }

    @RequestMapping(value = "pub/pc/borrow/v2/repayment/logs/{borrowId}", method = RequestMethod.GET)
    @ApiOperation("还款记录")
    public ResponseEntity<VoViewRepayCollectionLogWarpRes> info(@PathVariable("borrowId") Long borrowId) {
        return repaymentBiz.logs(borrowId);
    }


    @ApiOperation(value = "pc：招标中统计")
    @GetMapping(value = "pub/pc/borrow/v2/statistics")
    public ResponseEntity<VoViewBorrowStatisticsWarpRes> pcStatistics() {
        return borrowBiz.statistics();
    }

    /**
     * pc取消借款
     *
     * @param voPcCancelThirdBorrow
     * @return
     */
    @PostMapping("/pub/pc/borrow/cancelBorrow")
    @ApiOperation("pc取消借款")
    public ResponseEntity<VoBaseResp> pcCancelBorrow(@Valid @ModelAttribute VoPcCancelThirdBorrow voPcCancelThirdBorrow) throws Exception {
        return borrowBiz.pcCancelBorrow(voPcCancelThirdBorrow);
    }

    @PostMapping("/pub/pc/borrow/repayAll")
    @ApiOperation("提前还款")
    public ResponseEntity<VoBaseResp> pcRepayAll(@Valid @ModelAttribute VoRepayAllReq voRepayAllReq) {
        return borrowThirdBiz.thirdBatchRepayAll(voRepayAllReq);
    }

    /**
     * 登记官方借款（车贷标、渠道标）
     *
     * @param voRegisterOfficialBorrow
     * @return
     */
    @PostMapping("/pub/pc/borrow/official/register")
    @ApiOperation("登记官方借款（车贷标、渠道标）")
    public ResponseEntity<VoHtmlResp> registerOfficialBorrow(HttpServletRequest request, @ModelAttribute @Valid VoRegisterOfficialBorrow voRegisterOfficialBorrow) {
        return borrowBiz.registerOfficialBorrow(voRegisterOfficialBorrow, request);
    }
}
