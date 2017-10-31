package com.gofobao.framework.tender.biz.impl;

import com.github.wenhao.jpa.Specifications;
import com.gofobao.framework.api.contants.ChannelContant;
import com.gofobao.framework.api.contants.FrzFlagContant;
import com.gofobao.framework.api.contants.JixinResultContants;
import com.gofobao.framework.api.helper.JixinManager;
import com.gofobao.framework.api.helper.JixinTxCodeEnum;
import com.gofobao.framework.api.model.balance_query.BalanceQueryRequest;
import com.gofobao.framework.api.model.balance_query.BalanceQueryResponse;
import com.gofobao.framework.api.model.bid_cancel.BidCancelReq;
import com.gofobao.framework.api.model.bid_cancel.BidCancelResp;
import com.gofobao.framework.asset.entity.Asset;
import com.gofobao.framework.asset.service.AssetService;
import com.gofobao.framework.borrow.biz.BorrowBiz;
import com.gofobao.framework.borrow.entity.Borrow;
import com.gofobao.framework.borrow.service.BorrowService;
import com.gofobao.framework.borrow.vo.request.VoCancelBorrow;
import com.gofobao.framework.borrow.vo.response.VoBorrowTenderUserRes;
import com.gofobao.framework.common.assets.AssetChange;
import com.gofobao.framework.common.assets.AssetChangeProvider;
import com.gofobao.framework.common.assets.AssetChangeTypeEnum;
import com.gofobao.framework.common.constans.TypeTokenContants;
import com.gofobao.framework.common.rabbitmq.MqConfig;
import com.gofobao.framework.common.rabbitmq.MqHelper;
import com.gofobao.framework.common.rabbitmq.MqQueueEnum;
import com.gofobao.framework.common.rabbitmq.MqTagEnum;
import com.gofobao.framework.core.helper.PasswordHelper;
import com.gofobao.framework.core.vo.VoBaseResp;
import com.gofobao.framework.helper.*;
import com.gofobao.framework.helper.project.JixinTenderRecordHelper;
import com.gofobao.framework.helper.project.SecurityHelper;
import com.gofobao.framework.lend.entity.Lend;
import com.gofobao.framework.lend.service.LendService;
import com.gofobao.framework.marketing.constans.MarketingTypeContants;
import com.gofobao.framework.marketing.entity.MarketingData;
import com.gofobao.framework.member.entity.UserCache;
import com.gofobao.framework.member.entity.UserThirdAccount;
import com.gofobao.framework.member.entity.Users;
import com.gofobao.framework.member.service.UserCacheService;
import com.gofobao.framework.member.service.UserService;
import com.gofobao.framework.member.service.UserThirdAccountService;
import com.gofobao.framework.repayment.entity.BorrowRepayment;
import com.gofobao.framework.repayment.service.BorrowRepaymentService;
import com.gofobao.framework.tender.biz.TenderBiz;
import com.gofobao.framework.tender.biz.TenderThirdBiz;
import com.gofobao.framework.tender.entity.Tender;
import com.gofobao.framework.tender.service.TenderService;
import com.gofobao.framework.tender.vo.VoSaveThirdTender;
import com.gofobao.framework.tender.vo.request.*;
import com.gofobao.framework.tender.vo.response.VoBorrowTenderUserWarpListRes;
import com.gofobao.framework.windmill.borrow.biz.WindmillTenderBiz;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multiset;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import java.util.*;

/**
 * Created by Zeke on 2017/5/31.
 */
@Service
@Slf4j
public class TenderBizImpl implements TenderBiz {

    static final Gson GSON = new Gson();

    @Autowired
    private UserService userService;
    @Autowired
    private TenderService tenderService;
    @Autowired
    private UserCacheService userCacheService;
    @Autowired
    private AssetService assetService;
    @Autowired
    private MqHelper mqHelper;
    @Autowired
    private BorrowBiz borrowBiz;
    @Autowired
    private BorrowService borrowService;
    @Autowired
    private UserThirdAccountService userThirdAccountService;
    @Autowired
    private JixinManager jixinManager;
    @Autowired
    private TenderThirdBiz tenderThirdBiz;
    @Autowired
    AssetChangeProvider assetChangeProvider;
    @Autowired
    private WindmillTenderBiz windmillTenderBiz;
    @Autowired
    private LendService lendService;
    @Autowired
    private JixinTenderRecordHelper jixinTenderRecordHelper;
    @Autowired
    private BorrowRepaymentService borrowRepaymentService;


    /**
     * 新版投标
     *
     * @param voCreateTenderReq
     * @return
     * @throws Exception
     */
    public ResponseEntity<VoBaseResp> createTender(VoCreateTenderReq voCreateTenderReq) throws Exception {
        Gson gson = new Gson();
        log.info(String.format("马上投资: 起步: %s", gson.toJson(voCreateTenderReq)));
        Borrow borrow = borrowService.findByIdLock(voCreateTenderReq.getBorrowId());
        Preconditions.checkNotNull(borrow, "投标: 标的信息为空!");
        if (!ObjectUtils.isEmpty(borrow.getLendId()) && borrow.getLendId() > 0) {
            Lend lend = lendService.findByIdLock(borrow.getLendId());
            // 对待有草出借,只能是出草人投
            if (voCreateTenderReq.getUserId().intValue() != lend.getUserId().intValue()) {
                return ResponseEntity
                        .badRequest()
                        .body(VoBaseResp.error(VoBaseResp.ERROR, "非常抱歉, 当前标的为有草出借标的, 只有出草人才能投!"));
            }
        }

        Users user = userService.findByIdLock(voCreateTenderReq.getUserId());
        Preconditions.checkNotNull(user, "投标: 用户信息为空!");

        UserThirdAccount userThirdAccount = userThirdAccountService.findByUserId(voCreateTenderReq.getUserId());
        if (ObjectUtils.isEmpty(userThirdAccount)) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR_CREDIT, "当前用户未开户！", VoBaseResp.class));
        }

        if (userThirdAccount.getAutoTenderState() != 1) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR_CREDIT, "请先签订自动投标协议！", VoBaseResp.class));
        }

        Asset asset = assetService.findByUserIdLock(voCreateTenderReq.getUserId());
        Preconditions.checkNotNull(asset, "投标: 资金记录为空!");

        UserCache userCache = userCacheService.findByUserIdLock(voCreateTenderReq.getUserId());
        Preconditions.checkNotNull(userCache, "投标: 用户缓存信息为空!");

        Multiset<String> extendMessage = HashMultiset.create();
        boolean state = verifyBorrowInfo4Borrow(borrow, user, voCreateTenderReq, extendMessage);  // 标的判断
        if (!state) {
            log.error("标判断不通过");
            Set<String> errorSet = extendMessage.elementSet();
            Iterator<String> iterator = errorSet.iterator();
            return ResponseEntity.badRequest().body(VoBaseResp.error(VoBaseResp.ERROR, iterator.next()));
        }

        state = verifyUserInfo4Borrow(user, borrow, asset, voCreateTenderReq, extendMessage); // 借款用户资产判断
        Set<String> errorSet = extendMessage.elementSet();
        Iterator<String> iterator = errorSet.iterator();
        if (!state) {
            log.error("标的判断资产不通过");
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, iterator.next()));
        }

        Date nowDate = new Date();
        long validateMoney = Long.parseLong(iterator.next());
        Tender borrowTender = createBorrowTenderRecord(voCreateTenderReq, user, nowDate, validateMoney);    // 生成投标记录
        borrowTender = registerJixinTenderRecord(borrow, borrowTender);  // 投标的存管报备
        if (ObjectUtils.isEmpty(borrowTender)) {
            log.error("标的报备失败");
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "非常抱歉, 自动投标存管申报失败"));
        }

        // 扣除用户投标金额
        updateAssetByTender(borrow, borrowTender);
        borrow.setMoneyYes(borrow.getMoneyYes() + validateMoney);
        borrow.setTenderCount((borrow.getTenderCount() + 1));
        borrow.setId(borrow.getId());
        borrow.setUpdatedAt(nowDate);
        borrowService.save(borrow);  // 更改标的信息

        if (borrow.getMoneyYes() >= borrow.getMoney()) {   // 对于投标金额等于招标金额触发复审
            log.info("标的满标");
            //更新满标时间
            if (ObjectUtils.isEmpty(borrow.getSuccessAt())) {
                borrow.setSuccessAt(nowDate);
                borrowService.save(borrow);
            }

            //判断是否是理财计划借款
            if (borrow.getIsFinance()) {
                //复审
                MqConfig mqConfig = new MqConfig();
                mqConfig.setQueue(MqQueueEnum.RABBITMQ_BORROW);
                mqConfig.setTag(MqTagEnum.AGAIN_VERIFY_FINANCE);
                mqConfig.setSendTime(DateHelper.addSeconds(nowDate, 1));
                ImmutableMap<String, String> body = ImmutableMap
                        .of(MqConfig.MSG_BORROW_ID, StringHelper.toString(borrow.getId()), MqConfig.MSG_TIME, DateHelper.dateToString(new Date()));
                mqConfig.setMsg(body);
                log.info(String.format("tenderBizImpl tender send mq %s", GSON.toJson(body)));
                mqHelper.convertAndSend(mqConfig);
            } else {

                //复审
                MqConfig mqConfig = new MqConfig();
                mqConfig.setQueue(MqQueueEnum.RABBITMQ_BORROW);
                mqConfig.setTag(MqTagEnum.AGAIN_VERIFY);
                mqConfig.setSendTime(DateHelper.addSeconds(nowDate, 1));
                ImmutableMap<String, String> body = ImmutableMap
                        .of(MqConfig.MSG_BORROW_ID, StringHelper.toString(borrow.getId()), MqConfig.MSG_TIME, DateHelper.dateToString(new Date()));
                mqConfig.setMsg(body);
                log.info(String.format("tenderBizImpl tender send mq %s", GSON.toJson(body)));
                mqHelper.convertAndSend(mqConfig);

            }
        }

        //如果当前用户是风车理财用户
        if (!StringUtils.isEmpty(user.getWindmillId())) {
            try {
                windmillTenderBiz.tenderNotify(borrowTender);
            } catch (Exception e) {
                log.error("推送风车理财异常", e);
            }
        }

        try {
            // 触发新手标活动派发
            if (borrow.getIsNovice() && userCache.isNovice() && (!borrow.getIsFinance())) {
                MarketingData marketingData = new MarketingData();
                marketingData.setTransTime(DateHelper.dateToString(new Date()));
                marketingData.setUserId(borrowTender.getUserId().toString());
                marketingData.setSourceId(borrowTender.getId().toString());
                marketingData.setMarketingType(MarketingTypeContants.TENDER);
                try {
                    String json = gson.toJson(marketingData);
                    Map<String, String> data = gson.fromJson(json, TypeTokenContants.MAP_ALL_STRING_TOKEN);
                    MqConfig mqConfig = new MqConfig();
                    mqConfig.setMsg(data);
                    mqConfig.setTag(MqTagEnum.MARKETING_TENDER);
                    mqConfig.setQueue(MqQueueEnum.RABBITMQ_MARKETING);
                    mqConfig.setSendTime(DateHelper.addSeconds(nowDate, 30));
                    mqHelper.convertAndSend(mqConfig);
                    log.info(String.format("投资营销节点触发: %s", new Gson().toJson(marketingData)));
                } catch (Throwable e) {
                    log.error(String.format("投资营销节点触发异常：%s", new Gson().toJson(marketingData)), e);
                }
            }
        } catch (Exception e) {
            log.error("触发派发失败新手红包失败", e);
        }
        return ResponseEntity.ok(VoBaseResp.ok("投资成功"));
    }

    /**
     * 用户投标冻结
     *
     * @param borrow
     * @param borrowTender
     * @throws Exception
     */
    private void updateAssetByTender(Borrow borrow, Tender borrowTender) throws Exception {
        AssetChange assetChange = new AssetChange();
        assetChange.setForUserId(borrowTender.getUserId());
        assetChange.setUserId(borrowTender.getUserId());
        assetChange.setType(AssetChangeTypeEnum.freeze);
        assetChange.setRemark(String.format("成功投资标的[%s]冻结资金%s元", borrow.getName(), StringHelper.formatDouble(borrowTender.getValidMoney() / 100D, true)));
        assetChange.setSeqNo(assetChangeProvider.getSeqNo());
        assetChange.setMoney(borrowTender.getValidMoney());
        assetChange.setGroupSeqNo(assetChangeProvider.getGroupSeqNo());
        assetChange.setSourceId(borrowTender.getId());
        assetChangeProvider.commonAssetChange(assetChange);
    }

    /**
     * 登记即信标的
     *
     * @param borrow
     * @param borrowTender
     * @return
     * @throws Exception
     */
    private Tender registerJixinTenderRecord(Borrow borrow, Tender borrowTender) throws Exception {
        if (!borrow.isTransfer()) {
            String txAmount = StringHelper.formatDouble(borrowTender.getValidMoney(), 100, false);
            /* 投标orderId */
            String orderId = JixinHelper.getOrderId(JixinHelper.TENDER_PREFIX);

            log.info(String.format("马上投资: 投资报备: %s", new Gson().toJson(borrowTender)));
            VoCreateThirdTenderReq voCreateThirdTenderReq = new VoCreateThirdTenderReq();
            voCreateThirdTenderReq.setAcqRes(String.valueOf(borrowTender.getId()));
            voCreateThirdTenderReq.setUserId(borrowTender.getUserId());
            voCreateThirdTenderReq.setTxAmount(txAmount);
            voCreateThirdTenderReq.setProductId(borrow.getProductId());
            voCreateThirdTenderReq.setOrderId(orderId);
            voCreateThirdTenderReq.setFrzFlag(FrzFlagContant.FREEZE);
            ResponseEntity<VoBaseResp> resp = tenderThirdBiz.createThirdTender(voCreateThirdTenderReq);
            if (resp.getBody().getState().getCode() == VoBaseResp.ERROR) {
                log.info("马上投资: 投资报备失败");
                return null;
            } else { // 保存即信投标申请到redis
                log.info("马上投资: 投资报备成功");
                UserThirdAccount userThirdAccount = userThirdAccountService.findByUserId(borrowTender.getUserId());
                VoSaveThirdTender voSaveThirdTender = new VoSaveThirdTender();
                voSaveThirdTender.setAccountId(userThirdAccount.getAccountId());
                voSaveThirdTender.setTxAmount(txAmount);
                voSaveThirdTender.setProductId(borrow.getProductId()); // productId
                voSaveThirdTender.setOrderId(orderId);
                voSaveThirdTender.setIsAuto(borrowTender.getIsAuto());
                jixinTenderRecordHelper.saveJixinTenderInRedis(voSaveThirdTender);
            }
        }

        borrowTender = tenderService.findById(borrowTender.getId());
        borrowTender.setIsThirdRegister(true);
        borrowTender.setUpdatedAt(new Date());
        return tenderService.save(borrowTender);
    }


    /**
     * 生成投标记录
     *
     * @param voCreateTenderReq
     * @param user
     * @param nowDate
     * @param validateMoney
     * @return
     */
    private Tender createBorrowTenderRecord(VoCreateTenderReq voCreateTenderReq, Users user, Date nowDate, long validateMoney) {
        Tender borrowTender = new Tender();
        borrowTender.setUserId(user.getId());
        borrowTender.setType(0);
        borrowTender.setBorrowId(voCreateTenderReq.getBorrowId());
        borrowTender.setStatus(1);
        borrowTender.setMoney(voCreateTenderReq.getTenderMoney().longValue());
        borrowTender.setValidMoney(validateMoney);
        Integer requestSource = 0;
        try {
            requestSource = Integer.valueOf(voCreateTenderReq.getRequestSource());
        } catch (Exception e) {
            requestSource = 0;
        }

        borrowTender.setSource(requestSource);
        Integer autoOrder = voCreateTenderReq.getAutoOrder();
        borrowTender.setAutoOrder(ObjectUtils.isEmpty(autoOrder) ? 0 : autoOrder);
        borrowTender.setIsAuto(voCreateTenderReq.getIsAutoTender());
        borrowTender.setUpdatedAt(nowDate);
        borrowTender.setCreatedAt(nowDate);
        borrowTender.setTransferFlag(0);
        borrowTender.setState(1);
        borrowTender = tenderService.save(borrowTender);
        return borrowTender;
    }


    /**
     * 借款用户审核检查
     * <p>
     * 主要做一下教研:
     * 1. 用户是否锁定
     * 2.投标是否满足最小投标原则
     * 3.有效金额是否大于自动投标设定的最大投标金额
     * 4.存管金额匹配
     * 4.账户有效金额匹配
     *
     * @param user
     * @param borrow
     * @param asset
     * @param voCreateTenderReq
     * @param extendMessage     @return
     */
    private boolean verifyUserInfo4Borrow(Users user, Borrow borrow, Asset asset, VoCreateTenderReq voCreateTenderReq, Multiset<String> extendMessage) {
        // 判断用户是否已经锁定
        if (user.getIsLock()) {
            extendMessage.add("当前用户属于锁定状态, 如有问题请联系客服!");
            log.error("当前用户属于锁定状态, 如有问题请联系客服!");
            return false;
        }

        // 判断最小投标金额
        long realTenderMoney = borrow.getMoney() - borrow.getMoneyYes();  // 剩余金额
        int minLimitTenderMoney = ObjectUtils.isEmpty(borrow.getLowest()) ? 50 * 100 : borrow.getLowest();  // 最小投标金额
        long realMiniTenderMoney = Math.min(realTenderMoney, minLimitTenderMoney);  // 获取最小投标金额
        if (realMiniTenderMoney > voCreateTenderReq.getTenderMoney()) {
            extendMessage.add("小于标的最小投标金额!");
            log.error("小于标的最小投标金额!");
            return false;
        }

        // 真实有效投标金额
        long invaildataMoney = Math.min(realTenderMoney, voCreateTenderReq.getTenderMoney().intValue());
        if (voCreateTenderReq.getIsAutoTender()) {
            // 对于设置最大自动投标金额进行判断
            if (borrow.getMostAuto() > 0) {
                invaildataMoney = Math.min(borrow.getMostAuto() - borrow.getMoneyYes(), invaildataMoney);
            }

            if ((invaildataMoney <= 0) || (invaildataMoney < minLimitTenderMoney)) {
                extendMessage.add("该借款已达到自投限额!");
                log.error("该借款已达到自投限额!");
                return false;
            }
        }

        if (invaildataMoney > asset.getUseMoney()) {
            extendMessage.add("您的账户可用余额不足,请先充值!");
            log.error("您的账户可用余额不足,请先充值!");
            return false;
        }

        // 查询存管系统资金
        UserThirdAccount userThirdAccount = userThirdAccountService.findByUserId(user.getId());
        BalanceQueryRequest balanceQueryRequest = new BalanceQueryRequest();
        balanceQueryRequest.setChannel(ChannelContant.HTML);
        balanceQueryRequest.setAccountId(userThirdAccount.getAccountId());
        BalanceQueryResponse balanceQueryResponse = jixinManager.send(JixinTxCodeEnum.BALANCE_QUERY, balanceQueryRequest, BalanceQueryResponse.class);
        if ((ObjectUtils.isEmpty(balanceQueryResponse)) || !balanceQueryResponse.getRetCode().equals(JixinResultContants.SUCCESS)) {
            extendMessage.add("当前网络不稳定,请稍后重试!");
            log.error("当前网络不稳定,请稍后重试!");
            return false;
        }


        String availBal1 = balanceQueryResponse.getAvailBal();
        long availBal = MoneyHelper.yuanToFen(NumberHelper.toDouble(availBal1));
        long useMoney = asset.getUseMoney().longValue();
        if (availBal < useMoney) {
            log.error(String.format("资金账户未同步userId:%s:本地:%s 即信:%s", user.getId(), useMoney, availBal));
            extendMessage.add("资金账户未同步，请先在个人中心进行资金同步操作!");
            return false;
        }

        extendMessage.add(String.valueOf(invaildataMoney));
        return true;
    }


    /**
     * 验证标的信息是否符合投标要求
     * 主要从:
     * 1.标的状态
     * 2.招标开始时间
     * 3.标的结束时间
     * 4.是否频繁投标
     * 5.投标密码判断
     * 6.债转转让是否与投标同一个人
     * 注意当标的已经过了招标时间, 会进行取消借款操作
     *
     * @param borrow
     * @param user
     * @param voCreateTenderReq
     * @param errerMessage      @return
     */
    private boolean verifyBorrowInfo4Borrow(Borrow borrow, Users user, VoCreateTenderReq voCreateTenderReq, Multiset<String> errerMessage) throws Exception {
        if (!(borrow.getStatus() == 1 && borrow.getMoneyYes() < borrow.getMoney())) {
            errerMessage.add("标的未在招标状态， 如有疑问请联系客服!");
            return false;
        }

        Date nowDate = new Date();
        Date releaseAt = borrow.getReleaseAt();
        boolean isAutoTender = voCreateTenderReq.getIsAutoTender();

        if (borrow.getIsNovice()) {  // 新手
            releaseAt = DateHelper.max(DateHelper.addHours(DateHelper.beginOfDate(releaseAt), 20), borrow.getReleaseAt());
        }

        //判断是否是理财计划专用借款
        long financeTenderUserId = 22002L;
        if (borrow.getIsFinance()) {
            //判断投标用户是否是22002
            if (user.getId().longValue() != financeTenderUserId) {
                errerMessage.add("此标的暂时无法投递!");
                return false;
            }
        }

        UserCache userCache = userCacheService.findById(user.getId());
        if (ObjectUtils.isEmpty(borrow.getLendId()) && releaseAt.getTime() > nowDate.getTime() && !userCache.isNovice()) {
            log.info(String.valueOf(ObjectUtils.isEmpty(borrow.getLendId())));
            log.info(String.valueOf(releaseAt.getTime() > nowDate.getTime()));
            log.info(String.valueOf(!userCache.isNovice()));
            if (borrow.getIsNovice()) {
                errerMessage.add("老用户可在20:00点后投新手标!");
            } else {
                errerMessage.add("当前标的未到发布时间");
            }

            return false;
        }

        Date endDate = DateHelper.addDays(DateHelper.beginOfDate(releaseAt), borrow.getValidDay() + 1);
        if (endDate.getTime() < nowDate.getTime()) {
            // 流标
            log.info("==========================================");
            log.info(String.format("标的流标操作: %s", GSON.toJson(borrow)));
            log.info("==========================================");
            VoCancelBorrow voCancelBorrow = new VoCancelBorrow();
            voCancelBorrow.setBorrowId(borrow.getId());
            voCancelBorrow.setUserId(borrow.getUserId());
            ResponseEntity<VoBaseResp> voBaseRespResponseEntity = borrowBiz.cancelBorrow(voCancelBorrow);
            if (voBaseRespResponseEntity.getStatusCode().equals(HttpStatus.OK)) {
                errerMessage.add("当前标的已经超过招标时间");
            } else {
                errerMessage.add(voBaseRespResponseEntity.getBody().getState().getMsg());
            }
            return false;
        }

        // 判断投标频繁
        if (tenderService.checkTenderNimiety(borrow.getId(), user.getId())) {
            errerMessage.add("投标间隔不能小于一分钟!");
            return false;
        }

        // 投标密码判断
        if (!StringUtils.isEmpty(borrow.getPassword())) {
            if (StringUtils.isEmpty(voCreateTenderReq.getBorrowPassword())) {
                errerMessage.add("投标密码不能为空!");
                return false;
            }

            if (!PasswordHelper.verifyPassword(borrow.getPassword(), voCreateTenderReq.getBorrowPassword())) {
                errerMessage.add("借款密码验证失败, 请重新输入!");
                return false;
            }
        }

        // 借款自投判断
        if (borrow.getUserId().equals(user.getId())) {
            errerMessage.add("不能投自己发布借款!");
            return false;
        }

        // 债转自投判断
        if (borrow.isTransfer()) {
            Tender tender = tenderService.findById(borrow.getTenderId());
            Borrow tempBorrow = borrowService.findById(tender.getBorrowId());
            if (user.getId().equals(tempBorrow.getUserId())) {
                errerMessage.add("不能投自己发布或转让的借款!");
                return false;
            }
        }

        if (!isAutoTender) {
            if (!userCache.isNovice() && borrow.getIsLock()) {
                log.info("borrowId -> %s,isLock -> %s,isNovice -> %s", borrow.getId(), borrow.getIsLock(), !userCache.isNovice());
                errerMessage.add("当前标的状态已锁定,请稍后再试吧");
                return false;
            }
        }
        return true;
    }

    /**
     * 投标
     *
     * @param voCreateTenderReq
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<VoBaseResp> tender(VoCreateTenderReq voCreateTenderReq) throws Exception {
        //投标撤回集合
        String borrowId = String.valueOf(voCreateTenderReq.getBorrowId());
        try {
            ResponseEntity<VoBaseResp> voBaseRespResponseEntity = createTender(voCreateTenderReq);
            if (voBaseRespResponseEntity.getStatusCode().equals(HttpStatus.OK)) {
                return ResponseEntity.ok(VoBaseResp.ok("投标成功!"));
            } else {
                return voBaseRespResponseEntity;
            }
        } catch (Exception e) {
            //投标撤回
            jixinTenderRecordHelper.cancelJixinTenderByRedisRecord(borrowId, false);
            throw new Exception(e);
        } finally {
            //从redis删除投标申请记录
            jixinTenderRecordHelper.removeJixinTenderRecordInRedis(borrowId, false);
        }
    }

    /**
     * 投标用户
     *
     * @param tenderUserReq
     * @param tenderUserReq
     * @return
     */
    @Override
    public ResponseEntity<VoBorrowTenderUserWarpListRes> findBorrowTenderUser(TenderUserReq tenderUserReq) {
        try {
            List<VoBorrowTenderUserRes> tenderUserRes = tenderService.findBorrowTenderUser(tenderUserReq);
            VoBorrowTenderUserWarpListRes warpListRes = VoBaseResp.ok("查询成功", VoBorrowTenderUserWarpListRes.class);
            warpListRes.setVoBorrowTenderUser(tenderUserRes);
            return ResponseEntity.ok(warpListRes);
        } catch (Throwable e) {
            e.printStackTrace();
            return ResponseEntity.
                    badRequest().
                    body(VoBaseResp.error(VoBaseResp.ERROR, "查询失败", VoBorrowTenderUserWarpListRes.class));
        }
    }

    @Override
    public ResponseEntity<VoBaseResp> adminCancelTender(VoAdminCancelTender voAdminCancelTender) {
        log.error("请求用户标的信息");
        String paramStr = voAdminCancelTender.getParamStr();
        if (!SecurityHelper.checkSign(voAdminCancelTender.getSign(), paramStr)) {
            log.error("BorrowBizImpl doAgainVerify error：自动车标不成功");
        }

        Map<String, String> paramMap = GSON.fromJson(paramStr, new com.google.gson.reflect.TypeToken<Map<String, String>>() {
        }.getType());


        String orderId = JixinHelper.getOrderId(JixinHelper.TENDER_CANCEL_PREFIX);
        String accountId = paramMap.get("accountId");
        String txAmount = paramMap.get("txAmount");
        String orgOrderId = paramMap.get("orgOrderId");
        String productId = paramMap.get("productId");
        log.info("标的撤销:" + orgOrderId);
        BidCancelReq request = new BidCancelReq();
        request.setAccountId(accountId);
        request.setTxAmount(txAmount);
        request.setChannel(ChannelContant.HTML);
        request.setOrderId(orderId);
        request.setOrgOrderId(orgOrderId);
        request.setProductId(productId);
        request.setAcqRes(accountId);
        BidCancelResp response = jixinManager.send(JixinTxCodeEnum.BID_CANCEL, request, BidCancelResp.class);
        if ((ObjectUtils.isEmpty(response)) || (!JixinResultContants.SUCCESS.equals(response.getRetCode()))) {
            String msg = ObjectUtils.isEmpty(response) ? "当前网络不稳定，请稍候重试" : response.getRetMsg();
            log.error("标的初审" + new Gson().toJson(response));
            return ResponseEntity.badRequest().body(VoBaseResp.error(VoBaseResp.ERROR, msg));
        }

        return ResponseEntity.badRequest().body(VoBaseResp.ok("撤销成功"));
    }


    /**
     * 结束普通第三方债权接口
     */
    public ResponseEntity<VoBaseResp> pcEndThirdTender(VoPcEndThirdTender voPcEndThirdTender) {
        String paramStr = voPcEndThirdTender.getParamStr();
        if (!SecurityHelper.checkSign(voPcEndThirdTender.getSign(), paramStr)) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "结束普通第三方债权接口 签名验证不通过!"));
        }

        Map<String, String> paramMap = new Gson().fromJson(paramStr, TypeTokenContants.MAP_ALL_STRING_TOKEN);
        Long borrowId = NumberHelper.toLong(paramMap.get("borrowId"));
        Borrow borrow = borrowService.findById(borrowId);
        Preconditions.checkNotNull(borrow, "借款记录不存在!");
        //判断是否是最后一起还款已还清
        Specification<BorrowRepayment> brs = Specifications
                .<BorrowRepayment>and()
                .eq("borrowId", borrowId)
                .eq("status", 0)
                .build();
        long count = borrowRepaymentService.count(brs);//判断是否有未还还款
        if (count > 0) {
            return ResponseEntity.badRequest().body(VoBaseResp.error(VoBaseResp.ERROR, "借款未结清不能结束债权!"));
        }

        //推送队列结束债权
        MqConfig mqConfig = new MqConfig();
        mqConfig.setQueue(MqQueueEnum.RABBITMQ_CREDIT);
        mqConfig.setTag(MqTagEnum.END_CREDIT);
        mqConfig.setSendTime(DateHelper.addMinutes(new Date(), 1));
        ImmutableMap<String, String> body = ImmutableMap
                .of(MqConfig.MSG_BORROW_ID, StringHelper.toString(borrowId),
                        MqConfig.MSG_TIME, DateHelper.dateToString(new Date()));
        mqConfig.setMsg(body);
        try {
            log.info(String.format("repaymentBizImpl endThirdTenderAndChangeBorrowStatus send mq %s", GSON.toJson(body)));
            mqHelper.convertAndSend(mqConfig);
        } catch (Throwable e) {
            log.error("repaymentBizImpl endThirdTenderAndChangeBorrowStatus send mq exception", e);
        }
        return ResponseEntity.ok(VoBaseResp.ok("发送结束债权成功!"));
    }
}
