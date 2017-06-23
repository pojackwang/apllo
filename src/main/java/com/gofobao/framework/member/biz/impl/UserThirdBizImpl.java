package com.gofobao.framework.member.biz.impl;

import com.gofobao.framework.api.contants.*;
import com.gofobao.framework.api.helper.JixinManager;
import com.gofobao.framework.api.helper.JixinTxCodeEnum;
import com.gofobao.framework.api.model.account_open_plus.AccountOpenPlusRequest;
import com.gofobao.framework.api.model.account_open_plus.AccountOpenPlusResponse;
import com.gofobao.framework.api.model.auto_bid_auth_plus.AutoBidAuthRequest;
import com.gofobao.framework.api.model.auto_bid_auth_plus.AutoBidAuthResponse;
import com.gofobao.framework.api.model.auto_credit_invest_auth.AutoCreditInvestAuthRequest;
import com.gofobao.framework.api.model.auto_credit_invest_auth.AutoCreditInvestAuthResponse;
import com.gofobao.framework.api.model.credit_auth_query.CreditAuthQueryRequest;
import com.gofobao.framework.api.model.credit_auth_query.CreditAuthQueryResponse;
import com.gofobao.framework.api.model.password_reset.PasswordResetRequest;
import com.gofobao.framework.api.model.password_reset.PasswordResetResponse;
import com.gofobao.framework.api.model.password_set.PasswordSetRequest;
import com.gofobao.framework.api.model.password_set.PasswordSetResponse;
import com.gofobao.framework.asset.entity.BankAccount;
import com.gofobao.framework.asset.service.BankAccountService;
import com.gofobao.framework.core.helper.RandomHelper;
import com.gofobao.framework.core.vo.VoBaseResp;
import com.gofobao.framework.helper.BankBinHelper;
import com.gofobao.framework.helper.OKHttpHelper;
import com.gofobao.framework.helper.RedisHelper;
import com.gofobao.framework.helper.ThirdAccountPasswordHelper;
import com.gofobao.framework.member.biz.UserThirdBiz;
import com.gofobao.framework.member.entity.UserThirdAccount;
import com.gofobao.framework.member.entity.Users;
import com.gofobao.framework.member.service.UserService;
import com.gofobao.framework.member.service.UserThirdAccountService;
import com.gofobao.framework.member.vo.request.VoOpenAccountReq;
import com.gofobao.framework.member.vo.response.*;
import com.gofobao.framework.system.entity.DictItem;
import com.gofobao.framework.system.entity.DictValue;
import com.gofobao.framework.system.service.DictItemServcie;
import com.gofobao.framework.system.service.DictValueService;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.reflect.TypeToken;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.util.Base64Utils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Created by Max on 17/5/22.
 */
@Service
@Slf4j
public class UserThirdBizImpl implements UserThirdBiz {

    @Autowired
    UserService userService;

    @Autowired
    BankAccountService bankAccountService;

    @Autowired
    JixinManager jixinManager;

    @Autowired
    RedisHelper redisHelper;

    @Autowired
    UserThirdAccountService userThirdAccountService;

    @Value("${gofobao.javaDomain}")
    private String javaDomain;

    @Value("${gofobao.h5Domain}")
    private String h5Domain;

    @Value("${gofobao.aliyun-bankinfo-url}")
    String aliyunQueryBankUrl;

    @Value("${gofobao.aliyun-bankinfo-appcode}")
    String aliyunQueryAppcode;

    @Autowired
    ThirdAccountPasswordHelper thirdAccountPasswordHelper ;


    @Autowired
    DictValueService dictValueServcie ;

    @Autowired
    DictItemServcie dictItemServcie ;

    @Autowired
    BankBinHelper bankBinHelper ;


    LoadingCache<String, DictValue> bankLimitCache = CacheBuilder
            .newBuilder()
            .expireAfterWrite(60, TimeUnit.MINUTES)
            .maximumSize(1024)
            .build(new CacheLoader<String, DictValue>() {
                @Override
                public DictValue load(String bankName) throws Exception {
                    DictItem dictItem = dictItemServcie.findTopByAliasCodeAndDel("PLATFORM_BANK", 0) ;
                    if(ObjectUtils.isEmpty(dictItem)){
                        return null ;
                    }

                    return dictValueServcie.findTopByItemIdAndValue02(dictItem.getId(), bankName);
                }
            }) ;


    @Override
    public ResponseEntity<VoPreOpenAccountResp> preOpenAccount(Long userId) {
        //1。 验证用户是否存在
        Users user = userService.findById(userId);
        if (ObjectUtils.isEmpty(user)) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "你访问的账户不存在! ", VoPreOpenAccountResp.class));
        }

        //2. 判断用户是否已经开过存管账户
        UserThirdAccount userThirdAccount = userThirdAccountService.findByUserId(user.getId());
        if (!ObjectUtils.isEmpty(userThirdAccount)) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "你的账户已经开户！", VoPreOpenAccountResp.class));
        }


        // 4.查询银行卡
        List<BankAccount> bankAccountList = bankAccountService.listBankByUserId(userId);
        List<VoBankResp> voBankResps = new ArrayList<>(bankAccountList.size());
        VoBankResp voBankResp = null;
        for (BankAccount bankAccount : bankAccountList) {
            voBankResp = new VoBankResp();
            voBankResps.add(voBankResp);
            voBankResp.setId(bankAccount.getId());
            voBankResp.setBankNo(bankAccount.getAccount());
        }

        VoPreOpenAccountResp voPreOpenAccountResp = VoBaseResp.ok("查询成功", VoPreOpenAccountResp.class);
        voPreOpenAccountResp.setMobile(user.getPhone());
        voPreOpenAccountResp.setIdType("01"); //证件类型：身份证
        voPreOpenAccountResp.setIdNo(ObjectUtils.isEmpty(user.getCardId()) ? "" :  user.getCardId());
        voPreOpenAccountResp.setName(user.getRealname());
        voPreOpenAccountResp.setBankList(voBankResps);
        return ResponseEntity.ok(voPreOpenAccountResp);
    }

    @Override
    public ResponseEntity<VoOpenAccountResp> openAccount(VoOpenAccountReq voOpenAccountReq, Long userId, HttpServletRequest httpServletRequest) {
        // 1.用户用户信息
        Users user = userService.findById(userId);
        if (ObjectUtils.isEmpty(user))
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "你访问的账户不存在", VoOpenAccountResp.class));
        // 2. 判断用户是否已经开过存管账户
        UserThirdAccount userThirdAccount = userThirdAccountService.findByUserId(user.getId());
        if (!ObjectUtils.isEmpty(userThirdAccount))
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "你的账户已经开户！", VoOpenAccountResp.class));

        UserThirdAccount userThirdAccountbyMobile = userThirdAccountService.findByMobile(voOpenAccountReq.getMobile());
        if(!ObjectUtils.isEmpty(userThirdAccountbyMobile)){
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "手机已在存管平台开户, 无需开户！", VoOpenAccountResp.class));
        }

        String bankName = null ;
        // 获取银行卡信息
        try {
            BankBinHelper.BankInfo bankInfo = bankBinHelper.find(voOpenAccountReq.getCardNo());

            if(ObjectUtils.isEmpty(bankInfo)){
                return ResponseEntity
                        .badRequest()
                        .body(VoBaseResp.error(VoBaseResp.ERROR, "查无此银行卡号, 如有问题请联系平台客户!", VoOpenAccountResp.class));
            }

            if(!bankInfo.getCardType().equals("借记卡")){
                return ResponseEntity
                        .badRequest()
                        .body(VoBaseResp.error(VoBaseResp.ERROR, "银行卡类型必须为借记卡!", VoOpenAccountResp.class));
            }

            bankName = bankInfo.getBankName() ;
        } catch (Exception e) {
            log.error("开户查询银行卡异常");
        }

        // 6 判断银行卡
        DictValue dictValue = null;
        try {
            dictValue = bankLimitCache.get(bankName);
        } catch (ExecutionException e) {
            log.error("查询平台支持银行异常", e);
        }
        if( ObjectUtils.isEmpty(dictValue) ){
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, String.format("当前平台不支持%s", bankName), VoOpenAccountResp.class)) ;
        }

        // 7.短信验证码验证
        String srvTxCode = null;
        try {
            srvTxCode = redisHelper.get(String.format("%s_%s", SrvTxCodeContants.ACCOUNT_OPEN_PLUS, voOpenAccountReq.getMobile()), null);
            redisHelper.remove(String.format("%s_%s", SrvTxCodeContants.ACCOUNT_OPEN_PLUS, voOpenAccountReq.getMobile()));
        } catch (Exception e) {
            log.error("UserThirdBizImpl openAccount get redis exception ", e);
        }

        if (StringUtils.isEmpty(srvTxCode)) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "短信验证码已过期，请重新获取", VoOpenAccountResp.class));
        }


        // 8.提交开户
        AccountOpenPlusRequest request = new AccountOpenPlusRequest();
        request.setIdType(IdTypeContant.ID_CARD);
        request.setName(voOpenAccountReq.getName());
        request.setMobile(voOpenAccountReq.getMobile());
        request.setIdNo(voOpenAccountReq.getIdNo());
        request.setAcctUse(AcctUseContant.GUARANTEE_MEMBER_ACCOUNT);
        request.setAcqRes(String.valueOf(user.getId()));
        request.setLastSrvAuthCode(srvTxCode);
        request.setChannel(ChannelContant.getchannel(httpServletRequest));
        request.setSmsCode(voOpenAccountReq.getSmsCode());
        request.setCardNo(voOpenAccountReq.getCardNo());


        AccountOpenPlusResponse response = jixinManager.send(JixinTxCodeEnum.OPEN_ACCOUNT_PLUS, request, AccountOpenPlusResponse.class);
        if ((ObjectUtils.isEmpty(response)) || (!JixinResultContants.SUCCESS.equals(response.getRetCode()))) {
            String msg = ObjectUtils.isEmpty(response) ? "当前网络不稳定，请稍候重试" : response.getRetMsg();
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, msg, VoOpenAccountResp.class));
        }

        // 8.保存银行存管账户到用户中
        String accountId = response.getAccountId();
        UserThirdAccount entity = new UserThirdAccount();
        Date nowDate = new Date();
        entity.setUpdateAt(nowDate);
        entity.setUserId(user.getId());
        entity.setCreateAt(nowDate);
        entity.setCreateId(user.getId());
        entity.setUserId(user.getId());
        entity.setDel(0);
        entity.setMobile(voOpenAccountReq.getMobile());
        entity.setIdType(1);
        entity.setIdNo(voOpenAccountReq.getIdNo());
        entity.setCardNo(voOpenAccountReq.getCardNo());
        entity.setChannel(Integer.parseInt(ChannelContant.getchannel(httpServletRequest)));
        entity.setAcctUse(1);
        entity.setAccountId(accountId);
        entity.setPasswordState(0);
        entity.setCardNoBindState(1);
        entity.setName(voOpenAccountReq.getName());
        entity.setBankLogo(dictValue.getValue03());
        entity.setBankName(bankName);
        Long id = userThirdAccountService.save(entity);

        //  9.保存用户实名信息
        user.setRealname(voOpenAccountReq.getName());
        user.setCardId(voOpenAccountReq.getIdNo());
        user.setUpdatedAt(nowDate);
        userService.save(user);
        VoOpenAccountResp voOpenAccountResp = VoBaseResp.ok("开户成功", VoOpenAccountResp.class);
        voOpenAccountResp.setOpenAccountBankName("江西银行");
        voOpenAccountResp.setAccount(accountId);
        voOpenAccountResp.setName(voOpenAccountReq.getName());
        return ResponseEntity.ok(voOpenAccountResp);
    }

    @Override
    public ResponseEntity<VoHtmlResp> modifyOpenAccPwd(HttpServletRequest httpServletRequest, Long userId) {
        UserThirdAccount userThirdAccount = userThirdAccountService.findByUserId(userId);
        if (ObjectUtils.isEmpty(userThirdAccount)) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "当前账户还未实名", VoHtmlResp.class));
        }

        String html = null;
        // 判断用户是密码初始化还是
        Integer passwordState = userThirdAccount.getPasswordState();
        if (passwordState.equals(0)) { // 初始化密码
            PasswordSetRequest passwordSetRequest = new PasswordSetRequest();
            passwordSetRequest.setMobile(userThirdAccount.getMobile());
            passwordSetRequest.setChannel(ChannelContant.getchannel(httpServletRequest));
            passwordSetRequest.setName(userThirdAccount.getName());
            passwordSetRequest.setAccountId(userThirdAccount.getAccountId());
            passwordSetRequest.setIdType(IdTypeContant.ID_CARD);
            passwordSetRequest.setIdNo(userThirdAccount.getIdNo());
            passwordSetRequest.setAcqRes(String.valueOf(userId));
            passwordSetRequest.setRetUrl(String.format("%s%s/%s", javaDomain, "/pub/password/show", userId));
            passwordSetRequest.setNotifyUrl(String.format("%s%s", javaDomain, "/pub/user/third/modifyOpenAccPwd/callback/1"));
            html = jixinManager.getHtml(JixinTxCodeEnum.PASSWORD_SET, passwordSetRequest);
        } else { // 重置密码
            PasswordResetRequest passwordResetRequest = new PasswordResetRequest();
            passwordResetRequest.setMobile(userThirdAccount.getMobile());
            passwordResetRequest.setChannel(ChannelContant.getchannel(httpServletRequest));
            passwordResetRequest.setName(userThirdAccount.getName());
            passwordResetRequest.setAccountId(userThirdAccount.getAccountId());
            passwordResetRequest.setIdType(IdTypeContant.ID_CARD);
            passwordResetRequest.setIdNo(userThirdAccount.getIdNo());
            passwordResetRequest.setAcqRes(String.valueOf(userId));
            passwordResetRequest.setRetUrl(String.format("%s%s/%s", javaDomain, "/pub/password/show", userId));
            passwordResetRequest.setNotifyUrl(String.format("%s%s", javaDomain, "/pub/user/third/modifyOpenAccPwd/callback/2"));
            html = jixinManager.getHtml(JixinTxCodeEnum.PASSWORD_RESET, passwordResetRequest);
        }

        if (StringUtils.isEmpty(html)) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "服务器开小差了， 请稍候重试", VoHtmlResp.class));
        }


        VoHtmlResp voHtmlResp = VoBaseResp.ok("成功", VoHtmlResp.class);
        try {
            voHtmlResp.setHtml(Base64Utils.encodeToString(html.getBytes("UTF-8")));
        } catch (UnsupportedEncodingException e) {
            log.error("UserThirdBizImpl modifyOpenAccPwd gethtml exceptio", e);
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "服务器开小差了， 请稍候重试", VoHtmlResp.class));
        }

        return ResponseEntity.ok(voHtmlResp);
    }

    @Override
    public ResponseEntity<String> modifyOpenAccPwdCallback(HttpServletRequest request, HttpServletResponse response, Integer type) {
        Long userId = null;

        if (type == 1) {
            PasswordSetResponse passwordSetResponse = jixinManager.callback(request, new TypeToken<PasswordSetResponse>() {
            });

            if (ObjectUtils.isEmpty(passwordSetResponse)) {
                return ResponseEntity.badRequest().body("error");
            }
            if (!JixinResultContants.SUCCESS.equals(passwordSetResponse.getRetCode())) {
                log.error("UserThirdBizImpl.modifyOpenAccPwdCallback: 回调出失败");
                return ResponseEntity
                        .badRequest()
                        .body("error");
            }

            userId = Long.parseLong(passwordSetResponse.getAcqRes());
        } else {
            PasswordResetResponse passwordResetResponse = jixinManager.callback(request, new TypeToken<PasswordResetResponse>() {
            });

            if (ObjectUtils.isEmpty(passwordResetResponse)) {
                return ResponseEntity.badRequest().body("error");
            }

            if (!JixinResultContants.SUCCESS.equals(passwordResetResponse.getRetCode())) {
                log.error("UserThirdBizImpl.modifyOpenAccPwdCallback: 回调出失败");
                return ResponseEntity
                        .badRequest()
                        .body("error");
            }
            userId = Long.parseLong(passwordResetResponse.getAcqRes());
        }


        if (ObjectUtils.isEmpty(userId)) {
            log.error("UserThirdBizImpl modifyOpenAccPwdCallback userId is null");
            return ResponseEntity.badRequest().body("error");
        }

        UserThirdAccount userThirdAccount = userThirdAccountService.findByUserId(userId);
        if (ObjectUtils.isEmpty(userThirdAccount)) {
            log.error("UserThirdBizImpl modifyOpenAccPwdCallback userThirdAccount is null");
            return ResponseEntity.badRequest().body("error");
        }

        if (userThirdAccount.getPasswordState().equals(1)) {
            return ResponseEntity.ok("success");
        }

        userThirdAccount.setPasswordState(1);
        userThirdAccount.setUpdateAt(new Date());
        Long id = userThirdAccountService.save(userThirdAccount);
        if (id == 0) {
            log.error("UserThirdBizImpl modifyOpenAccPwdCallback update userThirdAccount is error");
            return ResponseEntity.badRequest().body("error");
        }

        return ResponseEntity.ok("success");
    }

    @Override
    public ResponseEntity<String> autoTenderCallback(HttpServletRequest request, HttpServletResponse response) {
        AutoBidAuthResponse autoBidAuthResponse = jixinManager.callback(request, new TypeToken<AutoBidAuthResponse>() {
        });

        if(ObjectUtils.isEmpty(autoBidAuthResponse)){
            return ResponseEntity
                    .badRequest()
                    .body("error");
        }

        if(!JixinResultContants.SUCCESS.equals(autoBidAuthResponse.getRetCode())){
            log.error("UserThirdBizImpl.autoTenderCallback: 回调出失败");
            return ResponseEntity
                    .badRequest()
                    .body("error");
        }

        Long userId = Long.parseLong(autoBidAuthResponse.getAcqRes());


        if (ObjectUtils.isEmpty(userId)) {
            log.error("UserThirdBizImpl autoTenderCallback userId is null");
            return ResponseEntity.badRequest().body("error");
        }

        UserThirdAccount userThirdAccount = userThirdAccountService.findByUserId(userId);
        if (ObjectUtils.isEmpty(userThirdAccount)) {
            log.error("UserThirdBizImpl autoTenderCallback userThirdAccount is null");
            return ResponseEntity
                    .badRequest()
                    .body("error");
        }


        if (userThirdAccount.getAutoTenderState() == 1) {
            return ResponseEntity.ok("success");
        }

        userThirdAccount.setAutoTenderState(1);
        userThirdAccount.setAutoTenderTotAmount(999999999L);
        userThirdAccount.setAutoTenderTxAmount(999999999L);
        userThirdAccount.setAutoTenderOrderId(autoBidAuthResponse.getOrderId());
        userThirdAccount.setUpdateAt(new Date());
        Long id = userThirdAccountService.save(userThirdAccount);
        if (id == 0) {
            log.error("UserThirdBizImpl autoTenderCallback update userThirdAccount is error");
            return ResponseEntity
                    .badRequest()
                    .body("error");
        }

        return ResponseEntity.ok("success");
    }

    @Override
    public ResponseEntity<VoHtmlResp> autoTender(HttpServletRequest httpServletRequest, Long userId) {
        UserThirdAccount userThirdAccount = userThirdAccountService.findByUserId(userId);
        if (ObjectUtils.isEmpty(userThirdAccount)) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "请先开通江西银行存管账户！", VoHtmlResp.class));
        }

        if (userThirdAccount.getPasswordState() != 1) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "请先设置江西银行存管账户交易密码！", VoHtmlResp.class));
        }

        AutoBidAuthRequest autoBidAuthRequest = new AutoBidAuthRequest() ;
        autoBidAuthRequest.setAccountId(userThirdAccount.getAccountId());
        autoBidAuthRequest.setOrderId(System.currentTimeMillis() + RandomHelper.generateNumberCode(6));
        autoBidAuthRequest.setTxAmount("999999999") ;
        autoBidAuthRequest.setTotAmount("999999999");
        autoBidAuthRequest.setForgotPwdUrl(thirdAccountPasswordHelper.getThirdAcccountPasswordUrl(httpServletRequest, userId));
        autoBidAuthRequest.setRetUrl(String.format("%s%s%s", javaDomain, "/pub/autoTender/show/", userId));
        autoBidAuthRequest.setNotifyUrl(String.format("%s/%s", javaDomain, "/pub/user/third/autoTender/callback"));
        autoBidAuthRequest.setAcqRes(userId.toString());
        autoBidAuthRequest.setChannel(ChannelContant.getchannel(httpServletRequest));
        String html = null;

        try {
            html = jixinManager.getHtml(JixinTxCodeEnum.AUTO_BID_AUTH, autoBidAuthRequest);
        } catch (Exception e) {
            log.error("UserThirdBizImpl autoTender get redis exception ", e);
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "服务器开小差了， 请稍候重试", VoHtmlResp.class));
        }

        VoHtmlResp resp = VoBaseResp.ok("请求成功", VoHtmlResp.class);
        try {
            resp.setHtml(Base64Utils.encodeToString(html.getBytes("UTF-8")));
        } catch (UnsupportedEncodingException e) {
            log.error("UserThirdBizImpl autoTender gethtml exceptio", e);
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "服务器开小差了， 请稍候重试", VoHtmlResp.class));
        }
        return ResponseEntity.ok(resp);
    }

    @Override
    public ResponseEntity<VoHtmlResp> autoTranfter(HttpServletRequest httpServletRequest, Long userId) {
        UserThirdAccount userThirdAccount = userThirdAccountService.findByUserId(userId);
        if (ObjectUtils.isEmpty(userThirdAccount)) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "请先开通江西银行存管账户！", VoHtmlResp.class));
        }

        if (userThirdAccount.getPasswordState() != 1) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "请先设置江西银行存管账户交易密码！", VoHtmlResp.class));
        }

        if (userThirdAccount.getAutoTenderState() != 1) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "请先签约江西银行自动投标协议！", VoHtmlResp.class));
        }

        AutoCreditInvestAuthRequest autoCreditInvestAuthPlusRequest = new AutoCreditInvestAuthRequest() ;
        autoCreditInvestAuthPlusRequest.setAccountId(userThirdAccount.getAccountId());
        autoCreditInvestAuthPlusRequest.setOrderId(System.currentTimeMillis() + RandomHelper.generateNumberCode(6));
        autoCreditInvestAuthPlusRequest.setForgotPwdUrl(thirdAccountPasswordHelper.getThirdAcccountPasswordUrl(httpServletRequest, userId));
        autoCreditInvestAuthPlusRequest.setRetUrl(String.format("%s%s%s", javaDomain, "/pub/autoTranfer/show/", userId));
        autoCreditInvestAuthPlusRequest.setNotifyUrl(String.format("%s/%s", javaDomain, "/pub/user/third/autoTranfer/callback"));
        autoCreditInvestAuthPlusRequest.setAcqRes(userId.toString());
        autoCreditInvestAuthPlusRequest.setChannel(ChannelContant.getchannel(httpServletRequest));


        String html = null;
        try {
            html = jixinManager.getHtml(JixinTxCodeEnum.AUTO_CREDIT_INVEST_AUTH, autoCreditInvestAuthPlusRequest);
        } catch (Exception e) {
            log.error("UserThirdBizImpl autoTranfter get redis exception ", e);
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "服务器开小差了， 请稍候重试", VoHtmlResp.class));
        }

        VoHtmlResp resp = VoBaseResp.ok("请求成功", VoHtmlResp.class);
        try {
            resp.setHtml(Base64Utils.encodeToString(html.getBytes("UTF-8")));
        } catch (UnsupportedEncodingException e) {
            log.error("UserThirdBizImpl autoTender autoTranfter exception", e);
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "服务器开小差了， 请稍候重试", VoHtmlResp.class));
        }
        return ResponseEntity.ok(resp);
    }

    @Override
    public ResponseEntity<String> autoTranferCallback(HttpServletRequest request, HttpServletResponse response) {
        AutoCreditInvestAuthResponse autoCreditInvestAuthResponse = jixinManager.callback(request, new TypeToken<AutoCreditInvestAuthResponse>() {
        });

        if(ObjectUtils.isEmpty(autoCreditInvestAuthResponse)){
            return ResponseEntity
                    .badRequest()
                    .body("error");
        }

        if(!JixinResultContants.SUCCESS.equals(autoCreditInvestAuthResponse.getRetCode())){
            log.error("UserThirdBizImpl.autoTranferCallback: 回调出失败");
            return ResponseEntity
                    .badRequest()
                    .body("error");
        }

        Long userId = Long.parseLong(autoCreditInvestAuthResponse.getAcqRes());

        if (ObjectUtils.isEmpty(userId)) {
            log.error("UserThirdBizImpl autoTranferCallback userId is null");
            return ResponseEntity.badRequest().body("error");
        }

        UserThirdAccount userThirdAccount = userThirdAccountService.findByUserId(userId);
        if (ObjectUtils.isEmpty(userThirdAccount)) {
            log.error("UserThirdBizImpl autoTranferCallback userThirdAccount is null");
            return ResponseEntity
                    .badRequest()
                    .body("error");
        }


        if (userThirdAccount.getAutoTransferState() == 1) {
            return ResponseEntity.ok("success");
        }

        userThirdAccount.setAutoTransferState(1);
        userThirdAccount.setAutoTransferBondOrderId(autoCreditInvestAuthResponse.getOrderId());
        userThirdAccount.setUpdateAt(new Date());
        Long id = userThirdAccountService.save(userThirdAccount);
        if (id == 0) {
            log.error("UserThirdBizImpl autoTranferCallback update userThirdAccount is error");
            return ResponseEntity
                    .badRequest()
                    .body("error");
        }

        return ResponseEntity.ok("success");
    }

    @Override
    public ResponseEntity<VoSignInfoResp> querySigned(Long userId) {
        UserThirdAccount userThirdAccount = userThirdAccountService.findByUserId(userId);
        if (ObjectUtils.isEmpty(userThirdAccount)) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "请先开通江西银行存管账户！", VoSignInfoResp.class));
        }

        if (userThirdAccount.getPasswordState() != 1) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "请先设置江西银行存管账户交易密码！", VoSignInfoResp.class));
        }


        userThirdAccount = synCreditQuth(userThirdAccount);
        VoSignInfoResp re = VoBaseResp.ok("查询成功", VoSignInfoResp.class);
        re.setAutoTenderState(userThirdAccount.getAutoTenderState().equals(1));
        re.setAutoTransferState(userThirdAccount.getAutoTransferState().equals(1));
        return ResponseEntity.ok(re);
    }

    @Override
    public String showPassword(Long id, Model model) {
        UserThirdAccount userThirdAccount = userThirdAccountService.findByUserId(id);
        model.addAttribute("h5Domain", h5Domain) ;
        if(ObjectUtils.isEmpty(userThirdAccount)){
            return "/password/faile" ;
        }

        if(userThirdAccount.getPasswordState() == 1){
            return "/password/success" ;
        }else{
            return "/password/faile" ;
        }
    }

    @Override
    public void thirdAccountProtocol(Long userId, Model model) {
        Users users = userService.findById(userId);
        String username = users.getUsername();
        if(StringUtils.isEmpty(username)){
            username = users.getPhone() ;
        }
        if(StringUtils.isEmpty(username)){
            username = users.getEmail() ;
        }

        model.addAttribute("customerName", username) ;
        model.addAttribute("playformName",  "深圳市广富宝金融信息服务有限公司") ;
    }

    @Override
    public String showAutoTender(Long id, Model model) {
        UserThirdAccount userThirdAccount = userThirdAccountService.findByUserId(id);
        model.addAttribute("h5Domain", h5Domain) ;
        if(ObjectUtils.isEmpty(userThirdAccount)){
            return "/autoTender/faile" ;
        }

        if(userThirdAccount.getAutoTenderState() == 1){
            return "/autoTender/success" ;
        }else{
            return "/autoTender/faile" ;
        }
    }

    @Override
    public String showAutoTranfer(Long id, Model model) {

        UserThirdAccount userThirdAccount = userThirdAccountService.findByUserId(id);
        model.addAttribute("h5Domain", h5Domain) ;
        if(ObjectUtils.isEmpty(userThirdAccount)){
            return "/autoTranfer/faile" ;
        }

        if(userThirdAccount.getAutoTransferState() == 1){
            return "/autoTranfer/success" ;
        }else{
            return "/autoTranfer/faile" ;
        }
    }

    @Override
    public ResponseEntity<String> publicPasswordModify(HttpServletRequest httpServletRequest, String encode, String channel) {
        Long userId = thirdAccountPasswordHelper.getUserId(encode);
        if(userId == null){
            throw  new RuntimeException("非法请求");
        }
        UserThirdAccount userThirdAccount = userThirdAccountService.findByUserId(userId);
        if(ObjectUtils.isEmpty(userThirdAccount)){
            throw  new RuntimeException("设置密码");
        }
        PasswordResetRequest passwordResetRequest = new PasswordResetRequest() ;
        passwordResetRequest.setMobile(userThirdAccount.getMobile());
        passwordResetRequest.setChannel(channel);
        passwordResetRequest.setName(userThirdAccount.getName());
        passwordResetRequest.setAccountId(userThirdAccount.getAccountId());
        passwordResetRequest.setIdType(IdTypeContant.ID_CARD);
        passwordResetRequest.setIdNo(userThirdAccount.getIdNo());
        passwordResetRequest.setAcqRes(String.valueOf(userId));
        passwordResetRequest.setRetUrl(String.format("%s%s/%s", javaDomain, "/pub/password/show", userId));
        passwordResetRequest.setNotifyUrl(String.format("%s%s", javaDomain, "/pub/user/third/modifyOpenAccPwd/callback/2"));
        String html = jixinManager.getHtml(JixinTxCodeEnum.PASSWORD_RESET, passwordResetRequest) ;
        return ResponseEntity.ok(html);
    }

    @Override
    public UserThirdAccount synCreditQuth(UserThirdAccount userThirdAccount) {
        if(userThirdAccount.getAutoTenderState() == 0){
            CreditAuthQueryRequest creditAuthQueryRequest = new CreditAuthQueryRequest() ;
            creditAuthQueryRequest.setAccountId(userThirdAccount.getAccountId()) ;
            creditAuthQueryRequest.setType("1") ;
            creditAuthQueryRequest.setChannel(ChannelContant.APP) ;
            CreditAuthQueryResponse creditAuthQueryResponse = jixinManager
                    .send(JixinTxCodeEnum.CREDIT_AUTH_QUERY, creditAuthQueryRequest, CreditAuthQueryResponse.class);

            if( (!ObjectUtils.isEmpty(creditAuthQueryResponse)) && (creditAuthQueryResponse.getRetCode().equalsIgnoreCase(JixinResultContants.SUCCESS)) ) {
                if(creditAuthQueryResponse.getState().equalsIgnoreCase("1")){
                    // 同步信息
                    UserThirdAccount dbEntity = userThirdAccountService.findByUserId(userThirdAccount.getUserId());
                    dbEntity.setUpdateAt(new Date());
                    dbEntity.setAutoTenderState(1);
                    dbEntity.setAutoTenderOrderId(creditAuthQueryResponse.getOrderId());
                    dbEntity.setAutoTenderTotAmount(999999999L);
                    dbEntity.setAutoTenderTxAmount(999999999L );
                    userThirdAccountService.save(dbEntity) ;
                }
            }
        }

        if(userThirdAccount.getAutoTransferState() == 0){
            CreditAuthQueryRequest creditAuthQueryRequest = new CreditAuthQueryRequest() ;
            creditAuthQueryRequest.setAccountId(userThirdAccount.getAccountId()) ;
            creditAuthQueryRequest.setType("2") ;
            creditAuthQueryRequest.setChannel(ChannelContant.APP) ;
            CreditAuthQueryResponse creditAuthQueryResponse = jixinManager
                    .send(JixinTxCodeEnum.CREDIT_AUTH_QUERY, creditAuthQueryRequest, CreditAuthQueryResponse.class);
            if( (!ObjectUtils.isEmpty(creditAuthQueryResponse)) && (creditAuthQueryResponse.getRetCode().equalsIgnoreCase(JixinResultContants.SUCCESS))){
                if(creditAuthQueryResponse.getState().equalsIgnoreCase("1")){
                    // 同步信息
                    UserThirdAccount dbEntity = userThirdAccountService.findByUserId(userThirdAccount.getUserId());
                    dbEntity.setUpdateAt(new Date());
                    dbEntity.setAutoTransferState(1);
                    dbEntity.setAutoTransferBondOrderId(creditAuthQueryResponse.getOrderId());
                    userThirdAccountService.save(dbEntity) ;
                }
            }
        }

        return userThirdAccountService.findByUserId(userThirdAccount.getUserId()) ;
    }
}
