package com.gofobao.framework.repayment.biz.Impl;

import com.github.wenhao.jpa.Specifications;
import com.gofobao.framework.api.contants.ChannelContant;
import com.gofobao.framework.api.contants.JixinResultContants;
import com.gofobao.framework.api.helper.JixinManager;
import com.gofobao.framework.api.helper.JixinTxCodeEnum;
import com.gofobao.framework.api.model.balance_freeze.BalanceFreezeReq;
import com.gofobao.framework.api.model.balance_freeze.BalanceFreezeResp;
import com.gofobao.framework.api.model.balance_un_freeze.BalanceUnfreezeReq;
import com.gofobao.framework.api.model.batch_bail_repay.BatchBailRepayResp;
import com.gofobao.framework.api.model.batch_credit_end.BatchCreditEndResp;
import com.gofobao.framework.api.model.batch_credit_invest.BatchCreditInvestReq;
import com.gofobao.framework.api.model.batch_credit_invest.CreditInvest;
import com.gofobao.framework.api.model.batch_repay.BatchRepayReq;
import com.gofobao.framework.api.model.batch_repay.BatchRepayResp;
import com.gofobao.framework.api.model.batch_repay.Repay;
import com.gofobao.framework.api.model.batch_repay_bail.BatchRepayBailReq;
import com.gofobao.framework.api.model.batch_repay_bail.BatchRepayBailResp;
import com.gofobao.framework.api.model.batch_repay_bail.RepayBail;
import com.gofobao.framework.asset.contants.BatchAssetChangeContants;
import com.gofobao.framework.asset.entity.AdvanceLog;
import com.gofobao.framework.asset.entity.Asset;
import com.gofobao.framework.asset.entity.BatchAssetChange;
import com.gofobao.framework.asset.entity.BatchAssetChangeItem;
import com.gofobao.framework.asset.service.AdvanceLogService;
import com.gofobao.framework.asset.service.AssetService;
import com.gofobao.framework.asset.service.BatchAssetChangeItemService;
import com.gofobao.framework.asset.service.BatchAssetChangeService;
import com.gofobao.framework.borrow.entity.Borrow;
import com.gofobao.framework.borrow.repository.BorrowRepository;
import com.gofobao.framework.borrow.service.BorrowService;
import com.gofobao.framework.borrow.vo.request.VoRepayAllReq;
import com.gofobao.framework.collection.entity.BorrowCollection;
import com.gofobao.framework.collection.service.BorrowCollectionService;
import com.gofobao.framework.collection.vo.request.VoCollectionListReq;
import com.gofobao.framework.collection.vo.request.VoCollectionOrderReq;
import com.gofobao.framework.collection.vo.response.VoViewCollectionDaysWarpRes;
import com.gofobao.framework.collection.vo.response.VoViewCollectionOrderListWarpResp;
import com.gofobao.framework.collection.vo.response.VoViewCollectionOrderRes;
import com.gofobao.framework.common.assets.AssetChange;
import com.gofobao.framework.common.assets.AssetChangeProvider;
import com.gofobao.framework.common.assets.AssetChangeTypeEnum;
import com.gofobao.framework.common.constans.TypeTokenContants;
import com.gofobao.framework.common.data.DataObject;
import com.gofobao.framework.common.data.LtSpecification;
import com.gofobao.framework.common.integral.IntegralChangeEntity;
import com.gofobao.framework.common.integral.IntegralChangeEnum;
import com.gofobao.framework.common.jxl.ExcelException;
import com.gofobao.framework.common.jxl.ExcelUtil;
import com.gofobao.framework.common.rabbitmq.MqConfig;
import com.gofobao.framework.common.rabbitmq.MqHelper;
import com.gofobao.framework.common.rabbitmq.MqQueueEnum;
import com.gofobao.framework.common.rabbitmq.MqTagEnum;
import com.gofobao.framework.core.vo.VoBaseResp;
import com.gofobao.framework.helper.*;
import com.gofobao.framework.helper.project.BatchAssetChangeHelper;
import com.gofobao.framework.helper.project.BorrowHelper;
import com.gofobao.framework.helper.project.IntegralChangeHelper;
import com.gofobao.framework.helper.project.SecurityHelper;
import com.gofobao.framework.member.entity.UserCache;
import com.gofobao.framework.member.entity.UserThirdAccount;
import com.gofobao.framework.member.entity.Users;
import com.gofobao.framework.member.service.UserCacheService;
import com.gofobao.framework.member.service.UserService;
import com.gofobao.framework.member.service.UserThirdAccountService;
import com.gofobao.framework.repayment.biz.BorrowRepaymentThirdBiz;
import com.gofobao.framework.repayment.biz.RepaymentBiz;
import com.gofobao.framework.repayment.entity.AdvanceAssetChange;
import com.gofobao.framework.repayment.entity.BorrowRepayment;
import com.gofobao.framework.repayment.entity.RepayAssetChange;
import com.gofobao.framework.repayment.service.BorrowRepaymentService;
import com.gofobao.framework.repayment.vo.request.*;
import com.gofobao.framework.repayment.vo.response.RepayCollectionLog;
import com.gofobao.framework.repayment.vo.response.RepaymentOrderDetail;
import com.gofobao.framework.repayment.vo.response.VoViewRepayCollectionLogWarpRes;
import com.gofobao.framework.repayment.vo.response.VoViewRepaymentOrderDetailWarpRes;
import com.gofobao.framework.repayment.vo.response.pc.VoCollection;
import com.gofobao.framework.repayment.vo.response.pc.VoOrdersList;
import com.gofobao.framework.repayment.vo.response.pc.VoViewCollectionWarpRes;
import com.gofobao.framework.repayment.vo.response.pc.VoViewOrderListWarpRes;
import com.gofobao.framework.system.biz.StatisticBiz;
import com.gofobao.framework.system.biz.ThirdBatchLogBiz;
import com.gofobao.framework.system.contants.ThirdBatchLogContants;
import com.gofobao.framework.system.entity.*;
import com.gofobao.framework.system.service.DictItemService;
import com.gofobao.framework.system.service.DictValueService;
import com.gofobao.framework.system.service.ThirdBatchLogService;
import com.gofobao.framework.tender.biz.TransferBiz;
import com.gofobao.framework.tender.contants.BorrowContants;
import com.gofobao.framework.tender.entity.Tender;
import com.gofobao.framework.tender.entity.Transfer;
import com.gofobao.framework.tender.entity.TransferBuyLog;
import com.gofobao.framework.tender.service.TenderService;
import com.gofobao.framework.tender.service.TransferBuyLogService;
import com.gofobao.framework.tender.service.TransferService;
import com.gofobao.framework.windmill.borrow.biz.WindmillTenderBiz;
import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.gofobao.framework.helper.DateHelper.isBetween;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;

/**
 * Created by admin on 2017/6/6.
 */
@Service
@Slf4j
public class RepaymentBizImpl implements RepaymentBiz {
    final Gson GSON = new GsonBuilder().create();

    @Autowired
    private BorrowService borrowService;
    @Autowired
    private AssetService assetService;
    @Autowired
    private StatisticBiz statisticBiz;
    @Autowired
    private TenderService tenderService;
    @Autowired
    private UserCacheService userCacheService;
    @Autowired
    private BorrowCollectionService borrowCollectionService;
    @Autowired
    private IntegralChangeHelper integralChangeHelper;
    @Autowired
    private BorrowRepaymentService borrowRepaymentService;
    @Autowired
    private AdvanceLogService advanceLogService;
    @Autowired
    private BorrowRepository borrowRepository;
    @Autowired
    private DictItemService dictItemService;
    @Autowired
    private ThirdBatchLogService thirdBatchLogService;
    @Autowired
    private ThirdBatchLogBiz thirdBatchLogBiz;
    @Autowired
    private JixinHelper jixinHelper;
    @Autowired
    private DictValueService dictValueService;
    @Autowired
    private BorrowRepaymentThirdBiz borrowRepaymentThirdBiz;
    @Autowired
    private BatchAssetChangeHelper batchAssetChangeHelper;
    @Autowired
    private BatchAssetChangeItemService batchAssetChangeItemService;
    @Autowired
    private AssetChangeProvider assetChangeProvider;
    @Autowired
    private TransferService transferService;
    @Autowired
    private UserService userService;
    @Autowired
    private MqHelper mqHelper;
    @Autowired
    private BatchAssetChangeService batchAssetChangeService;
    @Autowired
    private TransferBuyLogService transferBuyLogService;
    @Autowired
    private TransferBiz transferBiz;
    @Autowired
    private WindmillTenderBiz windmillTenderBiz;

    @Value("${gofobao.webDomain}")
    private String webDomain;

    @Value("${gofobao.javaDomain}")
    private String javaDomain;


    LoadingCache<String, DictValue> jixinCache = CacheBuilder
            .newBuilder()
            .expireAfterWrite(60, TimeUnit.MINUTES)
            .maximumSize(1024)
            .build(new CacheLoader<String, DictValue>() {
                @Override
                public DictValue load(String bankName) throws Exception {
                    DictItem dictItem = dictItemService.findTopByAliasCodeAndDel("JIXIN_PARAM", 0);
                    if (ObjectUtils.isEmpty(dictItem)) {
                        return null;
                    }

                    return dictValueService.findTopByItemIdAndValue01(dictItem.getId(), bankName);
                }
            });
    @Autowired
    private UserThirdAccountService userThirdAccountService;
    @Autowired
    private JixinManager jixinManager;


    /**
     * pc提前结清
     *
     * @param voRepayAllReq
     * @return
     * @throws Exception
     */
    public ResponseEntity<VoBaseResp> pcRepayAll(VoRepayAllReq voRepayAllReq) throws Exception {
        String paramStr = voRepayAllReq.getParamStr();/* pc请求提前结清参数 */
        if (!SecurityHelper.checkSign(voRepayAllReq.getSign(), paramStr)) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "pc取消借款 签名验证不通过!"));
        }
        Map<String, String> paramMap = GSON.fromJson(paramStr, TypeTokenContants.MAP_ALL_STRING_TOKEN);
        /* 借款id */
        long borrowId = NumberHelper.toLong(paramMap.get("borrowId"));

        //提前结清操作
        return repayAll(borrowId);
    }

    /**
     * 提前结清处理
     *
     * @param borrowId
     */
    public ResponseEntity<VoBaseResp> repayAllDeal(long borrowId, long batchNo) throws Exception {
        //1.判断借款状态，
        Borrow borrow = borrowService.findByIdLock(borrowId);/* 提前结清操作的借款记录 */
        Preconditions.checkNotNull(borrow, "借款记录不存在!");
        //2.查询提前结清需要回款记录
        Specification<BorrowRepayment> brs = Specifications
                .<BorrowRepayment>and()
                .eq("borrowId", borrowId)
                .eq("status", 0)
                .build();
        List<BorrowRepayment> borrowRepaymentList = borrowRepaymentService.findList(brs);
        Preconditions.checkNotNull(borrowRepaymentList, "还款记录(提前结清)不存在！");
        //2.1/* 还款对应的投标记录  包括债权转让在里面 */
        Specification<Tender> ts = Specifications
                .<Tender>and()
                .eq("status", 1)
                .eq("borrowId", borrow.getId())
                .build();
        List<Tender> tenderList = tenderService.findList(ts);/* 还款对应的投标记录  包括债权转让在里面 */
        Preconditions.checkNotNull(tenderList, "立即还款: 投标记录为空!");
        /* 投标记录id集合 */
        Set<Long> tenderIds = tenderList.stream().map(tender -> tender.getId()).collect(Collectors.toSet());
        //迭代还款集合,逐期还款
        for (BorrowRepayment borrowRepayment : borrowRepaymentList) {
            /* 是否垫付 */
            boolean advance = !ObjectUtils.isEmpty(borrowRepayment.getAdvanceAtYes());
            /* 查询未转让的投标记录回款记录 */
            Specification<BorrowCollection> bcs = Specifications
                    .<BorrowCollection>and()
                    .in("tenderId", tenderIds.toArray())
                    .eq("status", 0)
                    .eq("order", borrowRepayment.getOrder())
                    .eq("transferFlag", 0)
                    .build();
            List<BorrowCollection> borrowCollectionList = borrowCollectionService.findList(bcs);
            Preconditions.checkNotNull(borrowCollectionList, "立即还款: 回款记录为空!");

            //4.还款成功后变更改还款状态
            changeRepaymentAndRepayStatus(borrow, tenderList, borrowRepayment, borrowCollectionList, advance);
            //5.结束第三方债权并更新借款状态（还款最后一期的时候）
            endThirdTenderAndChangeBorrowStatus(borrow, borrowRepayment);
            //6.发送投资人收到还款站内信
            sendCollectionNotices(borrowCollectionList, advance, borrow);
            //7.发放积分
            giveInterest(borrowCollectionList, borrow);
            //8.还款最后新增统计
            updateRepaymentStatistics(borrow, borrowRepayment);
            //9.更新投资人缓存
            updateUserCacheByReceivedRepay(borrowCollectionList, borrow);
            //10.项目回款短信通知
            smsNoticeByReceivedRepay(borrowCollectionList, borrow, borrowRepayment);
        }
        //2.进行批次资产改变
        //2.处理资金还款人、收款人资金变动
        batchAssetChangeHelper.batchAssetChangeAndCollection(borrowId, batchNo, BatchAssetChangeContants.BATCH_REPAY_ALL);
        return ResponseEntity.ok(VoBaseResp.ok("提前结清处理成功!"));
    }

    /**
     * 提前结清操作
     *
     * @param borrowId
     * @throws Exception
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<VoBaseResp> repayAll(long borrowId) throws Exception {
        Borrow borrow = borrowService.findByIdLock(borrowId);/* 借款记录 */
        Preconditions.checkNotNull(borrow, "借款记录不存在!");
        UserThirdAccount borrowUserThirdAccount = userThirdAccountService.findByUserId(borrow.getUserId());  /* 借款人存管账户不存在 */
        ResponseEntity<VoBaseResp> resp = ThirdAccountHelper.allConditionCheck(borrowUserThirdAccount);
        if (resp.getBody().getState().getCode() != VoBaseResp.OK) {
            return resp;
        }
        Asset borrowAsset = assetService.findByUserId(borrow.getUserId());/* 借款人资产账户 */
        Preconditions.checkNotNull(borrowAsset, "借款人资产记录不存在!");

        //判断提交还款批次是否多次重复提交
        int flag = thirdBatchLogBiz.checkBatchOftenSubmit(String.valueOf(borrowId), ThirdBatchLogContants.BATCH_REPAY);
        if (flag == ThirdBatchLogContants.AWAIT) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, StringHelper.toString("还款处理中，请勿重复点击!")));
        } else if (flag == ThirdBatchLogContants.SUCCESS) {
            /**
             * @// TODO: 2017/8/21 直接调用批次处理
             */
        }
        /* 有效未还的还款记录 */
        Specification<BorrowRepayment> brs = Specifications
                .<BorrowRepayment>and()
                .eq("borrowId", borrowId)
                .eq("status", 0)
                .build();
        List<BorrowRepayment> borrowRepaymentList = borrowRepaymentService.findList(brs);
        Map<Long/* repaymentId */, BorrowRepayment> borrowRepaymentMaps = borrowRepaymentList.stream().collect(Collectors.toMap(BorrowRepayment::getId, Function.identity()));
        /* 还款请求集合 */
        List<VoBuildThirdRepayReq> voBuildThirdRepayReqs = new ArrayList<>();
        //构建还款请求集合
        ImmutableSet<Long> resultSet = buildRepayReqList(voBuildThirdRepayReqs, borrow, borrowRepaymentList);
        Iterator<Long> iterator = resultSet.iterator();
        long penalty = iterator.next();/* 违约金 */
        long repayMoney = iterator.next();/* 提前结清需还总金额 */
        if (borrowAsset.getUseMoney() < (repayMoney)) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "结清总共需要还款 " + repayMoney + " 元，您的账户余额不足，请先充值!！"));
        }
        /* 批次号 */
        String batchNo = jixinHelper.getBatchNo();
                /* 资产记录流水号 */
        String seqNo = assetChangeProvider.getSeqNo();
        /* 资产记录分组流水号 */
        String groupSeqNo = assetChangeProvider.getGroupSeqNo();
        //生成批次资产改变主记录
        BatchAssetChange batchAssetChange = addBatchAssetChangeByRepayAll(batchNo, borrowId);
        //扣除提前结清的违约金
        addBatchAssetChangeByBorrowPenalty(borrowId, borrow, penalty, seqNo, groupSeqNo, batchAssetChange);
        //提前结清处理
        //1.生成批次资产变动记录
        //2.发送至存管系统进行备案
        repayAllProcess(borrowId, borrow, borrowUserThirdAccount, voBuildThirdRepayReqs, batchNo, seqNo, groupSeqNo, penalty, batchAssetChange, borrowRepaymentMaps);
        return ResponseEntity.ok(VoBaseResp.ok("提前结清成功!"));
    }

    /**
     * 扣除提前结清的违约金
     *
     * @param borrowId
     * @param borrow
     * @param penalty
     * @param seqNo
     * @param groupSeqNo
     * @param batchAssetChange
     */
    private void addBatchAssetChangeByBorrowPenalty(long borrowId, Borrow borrow, long penalty, String seqNo, String groupSeqNo, BatchAssetChange batchAssetChange) {
        //扣除提前结清的违约金
        if (penalty > 0) {
            BatchAssetChangeItem batchAssetChangeItem = new BatchAssetChangeItem();
            batchAssetChangeItem.setBatchAssetChangeId(batchAssetChange.getId());
            batchAssetChangeItem.setState(0);
            batchAssetChangeItem.setType(AssetChangeTypeEnum.interestManagementFee.getLocalType());  // 扣除借款人违约金
            batchAssetChangeItem.setUserId(borrow.getUserId());
            batchAssetChangeItem.setMoney(penalty);
            batchAssetChangeItem.setRemark("扣除提前结清的违约金");
            batchAssetChangeItem.setCreatedAt(new Date());
            batchAssetChangeItem.setUpdatedAt(new Date());
            batchAssetChangeItem.setSourceId(borrowId);
            batchAssetChangeItem.setSeqNo(seqNo);
            batchAssetChangeItem.setGroupSeqNo(groupSeqNo);
            batchAssetChangeItemService.save(batchAssetChangeItem);
        }
    }

    /**
     * 构建还款请求集合
     *
     * @param voBuildThirdRepayReqs 还款请求
     */
    private ImmutableSet<Long> buildRepayReqList(List<VoBuildThirdRepayReq> voBuildThirdRepayReqs, Borrow borrow, List<BorrowRepayment> borrowRepaymentList) {
        long repaymentTotal = 0;/* 还款总金额 */
        long penalty = 0;/* 违约金 */
        long repayMoney = 0;/* 还款总金额+违约金 */
        for (int i = 0; i < borrowRepaymentList.size(); i++) {
            BorrowRepayment borrowRepayment = borrowRepaymentList.get(i);
            if (borrowRepayment.getStatus() != 0) {
                continue;
            }
            /* 开始时间 */
            Date startAt;
            if (borrowRepayment.getOrder() == 0) {
                startAt = DateHelper.beginOfDate(borrow.getSuccessAt());
            } else {
                startAt = DateHelper.beginOfDate(borrowRepaymentList.get(i - 1).getRepayAt());
            }
            /* 结束时间 */
            Date endAt = DateHelper.beginOfDate(borrowRepayment.getRepayAt());

            //以结清第一期的14天利息作为违约金
            if (penalty == 0) { // 违约金
                penalty = borrowRepayment.getInterest() / DateHelper.diffInDays(endAt, startAt, false) * 14;
            }

            Date nowStartDate = DateHelper.beginOfDate(new Date());  // 现在的凌晨时间
            double interestPercent;/* 利息百分比 */
            if (nowStartDate.getTime() <= startAt.getTime()) {
                interestPercent = 0;
            } else {
                interestPercent = MathHelper.min(DateHelper.diffInDays(nowStartDate, startAt, false) / DateHelper.diffInDays(endAt, startAt, false), 1);
            }
            /* 逾期天数 */
            int lateDays = DateHelper.diffInDays(nowStartDate, endAt, false);
            /* 逾期利息 */
            long lateInterest = calculateLateInterest(lateDays, borrowRepayment, borrow);
            //累加金额用于判断还款账余额是否充足
            repaymentTotal += borrowRepayment.getPrincipal() + borrowRepayment.getInterest() * interestPercent + lateInterest;
            /* 还款请求 */
            VoBuildThirdRepayReq voBuildThirdRepayReq = new VoBuildThirdRepayReq();
            voBuildThirdRepayReq.setInterestPercent(interestPercent);   // 赔偿利息
            voBuildThirdRepayReq.setRepaymentId(borrowRepayment.getId());
            voBuildThirdRepayReq.setUserId(borrowRepayment.getUserId());
            voBuildThirdRepayReq.setIsUserOpen(false);
            voBuildThirdRepayReq.setLateDays(lateDays);
            voBuildThirdRepayReq.setLateInterest(lateInterest);
            voBuildThirdRepayReqs.add(voBuildThirdRepayReq);
        }
        repayMoney = repaymentTotal + penalty;/* 提前结清需还总金额 */
        return ImmutableSet.of(penalty, repayMoney);
    }

    /**
     * 提前结清操作
     *
     * @param borrowId
     * @param borrow
     * @param borrowUserThirdAccount
     * @param voBuildThirdRepayReqs
     * @param batchNo
     * @param seqNo
     * @param groupSeqNo
     * @param batchAssetChange
     * @param borrowRepaymentMaps
     * @throws Exception
     */
    private void repayAllProcess(long borrowId, Borrow borrow, UserThirdAccount borrowUserThirdAccount, List<VoBuildThirdRepayReq> voBuildThirdRepayReqs, String batchNo,
                                 String seqNo, String groupSeqNo, long penalty, BatchAssetChange batchAssetChange, Map<Long/* repaymentId */, BorrowRepayment> borrowRepaymentMaps) throws Exception {
        Date nowDate = new Date();
        List<Repay> repays = new ArrayList<>();/* 生成存管还款记录(提前结清) */
        for (VoBuildThirdRepayReq voBuildThirdRepayReq : voBuildThirdRepayReqs) {
            BorrowRepayment borrowRepayment = borrowRepaymentMaps.get(voBuildThirdRepayReq.getRepaymentId());/* 还款记录 */
            /* 投资记录：不包含理财计划 */
            Specification<Tender> specification = Specifications
                    .<Tender>and()
                    .eq("status", 1)
                    .eq("borrowId", borrow.getId())
                    .build();
            List<Tender> tenderList = tenderService.findList(specification);
            Preconditions.checkState(!CollectionUtils.isEmpty(tenderList), "投资记录不存在!");
            /* 投资id集合 */
            List<Long> tenderIds = tenderList.stream().map(p -> p.getId()).collect(Collectors.toList());
            /* 投资人回款记录 */
            Specification<BorrowCollection> bcs = Specifications
                    .<BorrowCollection>and()
                    .in("tenderId", tenderIds.toArray())
                    .eq("status", 0)
                    .eq("transferFlag", 0)
                    .eq("order", borrowRepayment.getOrder())
                    .build();
            List<BorrowCollection> borrowCollectionList = borrowCollectionService.findList(bcs);
            Preconditions.checkNotNull(borrowCollectionList, "生成即信还款计划: 获取回款计划列表为空!");
            List<RepayAssetChange> repayAssetChangeList = new ArrayList<>();
            // 生成存管投资人还款记录(提前结清)
            List<Repay> tempRepays = calculateRepayPlan(borrow,
                    borrowUserThirdAccount.getAccountId(),
                    borrowRepayment,
                    tenderList,
                    borrowCollectionList,
                    voBuildThirdRepayReq.getLateInterest(),
                    voBuildThirdRepayReq.getInterestPercent(),
                    repayAssetChangeList
            );
            repays.addAll(tempRepays);
            // 生成还款记录
            doGenerateAssetChangeRecodeByRepay(borrow, borrowRepayment, borrowRepayment.getUserId(), repayAssetChangeList, seqNo, groupSeqNo, batchAssetChange);
        }
        /* 总还款本金 */
        double sumTxAmount = repays.stream().mapToDouble(repay -> NumberHelper.toDouble(repay.getTxAmount())).sum();
        for (Repay repay : repays) {
            double partPenalty = NumberHelper.toDouble(repay.getTxAmount()) * sumTxAmount * penalty;/*分摊违约金*/
            //给每期回款分摊违约金
            repay.setTxFeeOut(StringHelper.formatDouble(NumberHelper.toDouble(repay.getTxFeeOut()) + partPenalty / 100.0, false));
        }

        //所有交易利息
        double intAmount = repays.stream().mapToDouble(r -> NumberHelper.toDouble(r.getIntAmount())).sum();
        //所有还款手续费
        double txFeeOut = repays.stream().mapToDouble(r -> NumberHelper.toDouble(r.getTxFeeOut())).sum();
        //冻结金额
        double freezeMoney = sumTxAmount + intAmount + txFeeOut;

        //====================================================================
        //冻结借款人账户资金
        //====================================================================
        String freezeOrderId = JixinHelper.getOrderId(JixinHelper.BALANCE_FREEZE_PREFIX);
        try {
            BalanceFreezeReq balanceFreezeReq = new BalanceFreezeReq();
            balanceFreezeReq.setAccountId(borrowUserThirdAccount.getAccountId());
            balanceFreezeReq.setTxAmount(StringHelper.formatDouble(freezeMoney, false));
            balanceFreezeReq.setOrderId(freezeOrderId);
            balanceFreezeReq.setChannel(ChannelContant.HTML);
            BalanceFreezeResp balanceFreezeResp = jixinManager.send(JixinTxCodeEnum.BALANCE_FREEZE, balanceFreezeReq, BalanceFreezeResp.class);
            if ((ObjectUtils.isEmpty(balanceFreezeReq)) || (!JixinResultContants.SUCCESS.equalsIgnoreCase(balanceFreezeResp.getRetCode()))) {
                throw new Exception("即信批次还款冻结资金失败：" + balanceFreezeResp.getRetMsg());
            }

            //请求保留参数
            Map<String, Object> acqResMap = new HashMap<>();
            acqResMap.put("borrowId", borrowId);
            acqResMap.put("freezeMoney", freezeMoney);
            acqResMap.put("freezeOrderId", freezeOrderId);
            acqResMap.put("userId", borrow.getUserId());

        /* 需要冻结资金 */
            long frozenMoney = new Double((freezeMoney) * 100).longValue();

            //立即还款冻结可用资金
            AssetChange assetChange = new AssetChange();
            assetChange.setType(AssetChangeTypeEnum.freeze);  // 立即还款冻结可用资金
            assetChange.setUserId(borrow.getUserId());
            assetChange.setMoney(frozenMoney);
            assetChange.setRemark("立即还款冻结可用资金");
            assetChange.setSourceId(borrow.getId());
            assetChange.setSeqNo(assetChangeProvider.getSeqNo());
            assetChange.setGroupSeqNo(assetChangeProvider.getSeqNo());
            assetChangeProvider.commonAssetChange(assetChange);

            BatchRepayReq request = new BatchRepayReq();
            request.setBatchNo(batchNo);
            request.setTxAmount(StringHelper.formatDouble(sumTxAmount, false));
            request.setRetNotifyURL(javaDomain + "/pub/borrow/v2/third/repayall/run");
            request.setNotifyURL(javaDomain + "/pub/borrow/v2/third/repayall/check");
            request.setAcqRes(GSON.toJson(acqResMap));
            request.setSubPacks(GSON.toJson(repays));
            request.setChannel(ChannelContant.HTML);
            request.setTxCounts(StringHelper.toString(repays.size()));
            BatchRepayResp response = jixinManager.send(JixinTxCodeEnum.BATCH_REPAY, request, BatchRepayResp.class);
            if ((ObjectUtils.isEmpty(response)) || (!JixinResultContants.BATCH_SUCCESS.equalsIgnoreCase(response.getReceived()))) {
                throw new Exception("即信批次还款失败：" + response.getRetMsg());
            }

            //记录日志
            ThirdBatchLog thirdBatchLog = new ThirdBatchLog();
            thirdBatchLog.setBatchNo(batchNo);
            thirdBatchLog.setCreateAt(nowDate);
            thirdBatchLog.setUpdateAt(nowDate);
            thirdBatchLog.setSourceId(borrowId);
            thirdBatchLog.setType(ThirdBatchLogContants.BATCH_REPAY_ALL);
            thirdBatchLog.setAcqRes(GSON.toJson(acqResMap));
            thirdBatchLog.setRemark("(提前结清)即信批次还款");
            thirdBatchLogService.save(thirdBatchLog);
        } catch (Exception e) {
            // 申请即信还款解冻
            String unfreezeOrderId = JixinHelper.getOrderId(JixinHelper.BALANCE_UNFREEZE_PREFIX);
            BalanceUnfreezeReq balanceUnfreezeReq = new BalanceUnfreezeReq();
            balanceUnfreezeReq.setAccountId(borrowUserThirdAccount.getAccountId());
            balanceUnfreezeReq.setTxAmount(StringHelper.formatDouble(freezeMoney, false));
            balanceUnfreezeReq.setOrderId(unfreezeOrderId);
            balanceUnfreezeReq.setOrgOrderId(freezeOrderId);
            balanceUnfreezeReq.setChannel(ChannelContant.HTML);
            BalanceFreezeResp balanceFreezeResp = jixinManager.send(JixinTxCodeEnum.BALANCE_UN_FREEZE, balanceUnfreezeReq, BalanceFreezeResp.class);
            if ((ObjectUtils.isEmpty(balanceUnfreezeReq)) || (!JixinResultContants.SUCCESS.equalsIgnoreCase(balanceFreezeResp.getRetCode()))) {
                throw new Exception("提前结清解冻异常：" + balanceFreezeResp.getRetMsg());
            }
        }
    }


    /**
     * 新增资产更改记录
     *
     * @param batchNo
     * @param borrowId
     * @return
     */
    private BatchAssetChange addBatchAssetChangeByRepayAll(String batchNo, long borrowId) {
        BatchAssetChange batchAssetChange = new BatchAssetChange();
        batchAssetChange.setSourceId(borrowId);
        batchAssetChange.setState(0);
        batchAssetChange.setType(BatchAssetChangeContants.BATCH_REPAY_ALL);/* 提前结清 */
        batchAssetChange.setCreatedAt(new Date());
        batchAssetChange.setUpdatedAt(new Date());
        batchAssetChange.setBatchNo(batchNo);
        batchAssetChangeService.save(batchAssetChange);
        return batchAssetChange;
    }

    @Override
    public ResponseEntity<VoViewCollectionDaysWarpRes> days(Long userId, String time) {
        VoViewCollectionDaysWarpRes collectionDayWarpRes = VoBaseResp.ok("查询成功", VoViewCollectionDaysWarpRes.class);
        try {
            List<Integer> result = borrowRepaymentService.days(userId, time);
            collectionDayWarpRes.setWarpRes(result);
            return ResponseEntity.ok(collectionDayWarpRes);
        } catch (Throwable e) {
            return ResponseEntity.badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "查询失败", VoViewCollectionDaysWarpRes.class));

        }
    }

    /**
     * 还款计划
     *
     * @param voCollectionOrderReq
     * @return
     */
    @Override
    public ResponseEntity<VoViewCollectionOrderListWarpResp> repaymentList(VoCollectionOrderReq voCollectionOrderReq) {
        try {
            List<BorrowRepayment> repaymentList = borrowRepaymentService.repaymentList(voCollectionOrderReq);
            if (CollectionUtils.isEmpty(repaymentList)) {
                VoViewCollectionOrderListWarpResp response = VoBaseResp.ok("查询成功", VoViewCollectionOrderListWarpResp.class);
                response.setOrder(0);
                response.setSumCollectionMoneyYes("0");
                return ResponseEntity.ok(response);
            }

            Set<Long> borrowIdSet = repaymentList.stream()
                    .map(p -> p.getBorrowId())
                    .collect(Collectors.toSet());

            List<Borrow> borrowList = borrowRepository.findByIdIn(new ArrayList(borrowIdSet));
            Map<Long, Borrow> borrowMap = borrowList.stream()
                    .collect(Collectors.toMap(Borrow::getId, Function.identity()));

            List<VoViewCollectionOrderListWarpResp> orderListRes = new ArrayList<>(0);
            List<VoViewCollectionOrderRes> orderResList = new ArrayList<>();

            repaymentList.stream().forEach(p -> {
                VoViewCollectionOrderRes collectionOrderRes = new VoViewCollectionOrderRes();
                Borrow borrow = borrowMap.get(p.getBorrowId());
                collectionOrderRes.setCollectionId(p.getId());
                collectionOrderRes.setBorrowName(borrow.getName());
                collectionOrderRes.setOrder(p.getOrder() + 1);
                collectionOrderRes.setCollectionMoneyYes(StringHelper.formatMon(p.getRepayMoneyYes() / 100d));
                collectionOrderRes.setCollectionMoney(StringHelper.formatMon(p.getRepayMoney() / 100d));
                collectionOrderRes.setTimeLime(borrow.getTimeLimit());
                orderResList.add(collectionOrderRes);
            });

            VoViewCollectionOrderListWarpResp collectionOrder = VoBaseResp.ok("查询成功", VoViewCollectionOrderListWarpResp.class);
            collectionOrder.setOrderResList(orderResList);
            //总数
            collectionOrder.setOrder(orderResList.size());
            //已还款
            long moneyYesSum = repaymentList.stream()
                    .filter(p -> p.getStatus() == 1)
                    .mapToLong(w -> w.getRepayMoneyYes())
                    .sum();
            collectionOrder.setSumCollectionMoneyYes(StringHelper.formatMon(moneyYesSum / 100d));
            orderListRes.add(collectionOrder);
            return ResponseEntity.ok(collectionOrder);

        } catch (Throwable e) {
            return ResponseEntity.badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "查询失败", VoViewCollectionOrderListWarpResp.class));
        }
    }

    /**
     * pc:还款计划
     *
     * @param listReq
     * @return
     */
    @Override
    public ResponseEntity<VoViewOrderListWarpRes> pcRepaymentList(VoOrderListReq listReq) {
        try {
            VoViewOrderListWarpRes warpRes = VoBaseResp.ok("查询成功", VoViewOrderListWarpRes.class);
            Map<String, Object> resultMaps = borrowRepaymentService.pcOrderList(listReq);
            Integer totalCount = Integer.valueOf(resultMaps.get("totalCount").toString());
            List<VoOrdersList> orderList = (List<VoOrdersList>) resultMaps.get("orderList");
            warpRes.setTotalCount(totalCount);
            warpRes.setOrdersLists(orderList);
            return ResponseEntity.ok(warpRes);
        } catch (Throwable e) {
            e.printStackTrace();
            return ResponseEntity.badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "查询失败", VoViewOrderListWarpRes.class));
        }
    }

    @Override
    public void toExcel(HttpServletResponse response, VoOrderListReq listReq) {

        List<VoOrdersList> ordersLists = borrowRepaymentService.toExcel(listReq);
        if (!CollectionUtils.isEmpty(ordersLists)) {
            LinkedHashMap<String, String> paramMaps = Maps.newLinkedHashMap();
            paramMaps.put("time", "时间");
            paramMaps.put("collectionMoney", "本息");
            paramMaps.put("principal", "本金");
            paramMaps.put("interest", "利息");
            paramMaps.put("orderCount", "笔数");
            try {
                ExcelUtil.listToExcel(ordersLists, paramMaps, "还款计划", response);
            } catch (ExcelException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 还款详情
     *
     * @param voInfoReq
     * @return
     */
    @Override
    public ResponseEntity<VoViewRepaymentOrderDetailWarpRes> detail(VoInfoReq voInfoReq) {
        try {
            RepaymentOrderDetail voViewOrderDetailResp = borrowRepaymentService.detail(voInfoReq);
            VoViewRepaymentOrderDetailWarpRes warpRes = VoBaseResp.ok("查询成功", VoViewRepaymentOrderDetailWarpRes.class);
            warpRes.setRepaymentOrderDetail(voViewOrderDetailResp);
            return ResponseEntity.ok(warpRes);
        } catch (Throwable e) {
            return ResponseEntity.badRequest().body(VoBaseResp.error(VoBaseResp.ERROR, "查询失败", VoViewRepaymentOrderDetailWarpRes.class));
        }
    }


    /**
     * pc:未还款详情
     *
     * @param collectionListReq
     * @return
     */
    @Override
    public ResponseEntity<VoViewCollectionWarpRes> orderList(VoCollectionListReq collectionListReq) {
        try {

            VoViewCollectionWarpRes warpRes = VoBaseResp.ok("查询成功", VoViewCollectionWarpRes.class);
            Map<String, Object> resultMaps = borrowRepaymentService.collectionList(collectionListReq);
            Integer totalCount = Integer.valueOf(resultMaps.get("totalCount").toString());
            List<VoCollection> repaymentList = (List<VoCollection>) resultMaps.get("repaymentList");
            warpRes.setTotalCount(totalCount);
            warpRes.setVoCollections(repaymentList);
            return ResponseEntity.ok(warpRes);
        } catch (Throwable e) {
            return ResponseEntity.badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "查询失败", VoViewCollectionWarpRes.class));
        }
    }

    @Override
    public ResponseEntity<VoViewRepayCollectionLogWarpRes> logs(Long borrowId) {
        try {
            List<RepayCollectionLog> logList = borrowRepaymentService.logs(borrowId);
            VoViewRepayCollectionLogWarpRes warpRes = VoBaseResp.ok("查询成功", VoViewRepayCollectionLogWarpRes.class);
            warpRes.setCollectionLogs(logList);
            return ResponseEntity.ok(warpRes);
        } catch (Throwable e) {
            return ResponseEntity.badRequest().body(VoBaseResp.error(VoBaseResp.ERROR, "查询失败", VoViewRepayCollectionLogWarpRes.class));
        }
    }

    /**
     * 新还款处理
     * 1.查询并判断还款记录是否存在!
     * 2.处理资金还款人、收款人资金变动
     * 3.判断是否是还名义借款人垫付，垫付需要改变垫付记录状态
     * 4.还款成功后变更改还款状态
     * 5.结束债权
     * 6.发送投资人收到还款站内信
     * 7.投资人收到积分
     * 8.还款最后新增统计
     *
     * @param repaymentId
     * @return
     * @throws Exception
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<VoBaseResp> newRepayDeal(long repaymentId, long batchNo) throws Exception {
        //1.查询并判断还款记录是否存在!
        BorrowRepayment borrowRepayment = borrowRepaymentService.findByIdLock(repaymentId);/* 当期还款记录 */
        Preconditions.checkNotNull(borrowRepayment, "还款记录不存在!");
        Borrow parentBorrow = borrowService.findById(borrowRepayment.getBorrowId());/* 还款记录对应的借款记录 */
        Preconditions.checkNotNull(parentBorrow, "借款记录不存在!");
        /* 还款对应的投标记录  包括债权转让在里面 */
        Specification<Tender> ts = Specifications
                .<Tender>and()
                .eq("status", 1)
                .eq("borrowId", parentBorrow.getId())
                .build();
        List<Tender> tenderList = tenderService.findList(ts);/* 还款对应的投标记录  包括债权转让在里面 */
        Preconditions.checkNotNull(tenderList, "立即还款: 投标记录为空!");
        /* 投标记录id */
        Set<Long> tenderIds = tenderList.stream().map(tender -> tender.getId()).collect(Collectors.toSet());
        /* 查询未转让的投标记录回款记录 */
        Specification<BorrowCollection> bcs = Specifications
                .<BorrowCollection>and()
                .in("tenderId", tenderIds.toArray())
                .eq("status", 0)
                .eq("order", borrowRepayment.getOrder())
                .eq("transferFlag", 0)
                .build();
        List<BorrowCollection> borrowCollectionList = borrowCollectionService.findList(bcs);
        Preconditions.checkNotNull(borrowCollectionList, "立即还款: 回款记录为空!");
        /* 是否垫付 */
        boolean advance = !ObjectUtils.isEmpty(borrowRepayment.getAdvanceAtYes());
        //2.处理资金还款人、收款人资金变动
        batchAssetChangeHelper.batchAssetChangeAndCollection(repaymentId, batchNo, BatchAssetChangeContants.BATCH_REPAY);
        //4.还款成功后变更改还款状态
        changeRepaymentAndRepayStatus(parentBorrow, tenderList, borrowRepayment, borrowCollectionList, advance);
        if (!advance) { //非转让标需要统计与发放短信
            //5.结束第三方债权并更新借款状态（还款最后一期的时候）
            endThirdTenderAndChangeBorrowStatus(parentBorrow, borrowRepayment);
            //6.发送投资人收到还款站内信
            sendCollectionNotices(borrowCollectionList, advance, parentBorrow);
            //7.发放积分
            giveInterest(borrowCollectionList, parentBorrow);
            //8.还款最后新增统计
            updateRepaymentStatistics(parentBorrow, borrowRepayment);
            //9.更新投资人缓存
            updateUserCacheByReceivedRepay(borrowCollectionList, parentBorrow);
            //10.项目回款短信通知
            smsNoticeByReceivedRepay(borrowCollectionList, parentBorrow, borrowRepayment);
        }
        //通知风车理财用户 回款成功
        windmillTenderBiz.backMoneyNotify(borrowCollectionList);

        return ResponseEntity.ok(VoBaseResp.ok("还款处理成功!"));
    }

    /**
     * 项目回款短信通知
     *
     * @param borrowCollectionList
     * @param parentBorrow
     * @param borrowRepayment
     */
    private void smsNoticeByReceivedRepay(List<BorrowCollection> borrowCollectionList, Borrow parentBorrow, BorrowRepayment borrowRepayment) {
        Set<Long> userIds = borrowCollectionList.stream().map(borrowCollection -> borrowCollection.getUserId()).collect(toSet());/* 回款用户id */
        Map<Long /* 投资会员id */, List<BorrowCollection>> borrowCollrctionMaps = borrowCollectionList.stream().collect(groupingBy(BorrowCollection::getUserId)); /* 回款记录集合 */
        Specification<Users> us = Specifications
                .<Users>and()
                .in("id", userIds.toArray())
                .build();
        List<Users> usersList = userService.findList(us);/* 回款用户缓存记录列表 */
        Map<Long /* 投资会员id */, Users> userMaps = usersList.stream().collect(Collectors.toMap(Users::getId, Function.identity()));/* 回款用户记录列表*/
        userIds.stream().forEach(userId -> {
            List<BorrowCollection> borrowCollections = borrowCollrctionMaps.get(userId);/* 当前用户的所有回款 */
            Users users = userMaps.get(userId);//投资人会员记录
            long principal = borrowCollections.stream().mapToLong(BorrowCollection::getPrincipal).sum(); /* 当前用户的所有回款本金 */
            long interest = borrowCollections.stream().mapToLong(BorrowCollection::getInterest).sum();/* 当前用户的所有回款本金 */
            String phone = users.getPhone();/* 投资人手机号 */
            String name = "";
            if (ObjectUtils.isEmpty(phone)) {
                MqConfig config = new MqConfig();
                config.setQueue(MqQueueEnum.RABBITMQ_SMS);
                config.setTag(MqTagEnum.SMS_REGISTER);
                switch (parentBorrow.getType()) {
                    case BorrowContants.CE_DAI:
                        name = "车贷标";
                        break;
                    case BorrowContants.JING_ZHI:
                        name = "净值标";
                        break;
                    case BorrowContants.QU_DAO:
                        name = "渠道标";
                        break;
                    default:
                        name = "投标还款";
                }
                Map<String, String> body = new HashMap<>();
                body.put(MqConfig.PHONE, phone);
                body.put(MqConfig.IP, "127.0.0.1");
                body.put(MqConfig.MSG_ID, StringHelper.toString(parentBorrow.getId()));
                body.put(MqConfig.MSG_NAME, name);
                body.put(MqConfig.MSG_ORDER, StringHelper.toString(borrowRepayment.getOrder() + 1));
                body.put(MqConfig.MSG_MONEY, StringHelper.formatDouble(principal, 100, true));
                body.put(MqConfig.MSG_INTEREST, StringHelper.formatDouble(interest, 100, true));
                config.setMsg(body);

                boolean state = mqHelper.convertAndSend(config);
                if (!state) {
                    log.error(String.format("发送投资人收到还款短信失败:%s", config));
                }
            }
        });
    }

    /**
     * 更新用户缓存
     *
     * @param borrowCollectionList
     * @param parentBorrow
     */
    private void updateUserCacheByReceivedRepay(List<BorrowCollection> borrowCollectionList, Borrow parentBorrow) {
        Set<Long> userIds = borrowCollectionList.stream().map(borrowCollection -> borrowCollection.getUserId()).collect(toSet());/* 回款用户id */
        Map<Long, List<BorrowCollection>> borrowCollrctionMaps = borrowCollectionList.stream().collect(groupingBy(BorrowCollection::getUserId)); /* 回款记录集合 */
        Specification<UserCache> ucs = Specifications
                .<UserCache>and()
                .in("userId", userIds.toArray())
                .build();
        List<UserCache> userCaches = userCacheService.findList(ucs);/* 回款用户缓存记录列表 */
        Map<Long, UserCache> userCacheMaps = userCaches.stream().collect(Collectors.toMap(UserCache::getUserId, Function.identity()));/* 回款用户缓存记录列表*/
        userIds.stream().forEach(userId -> {
            List<BorrowCollection> borrowCollections = borrowCollrctionMaps.get(userId);/* 当前用户的所有回款 */
            long principal = borrowCollections.stream().mapToLong(BorrowCollection::getPrincipal).sum(); /* 当前用户的所有回款本金 */
            long interest = borrowCollections.stream().mapToLong(BorrowCollection::getInterest).sum();/* 当前用户的所有回款本金 */
            UserCache userCache = userCacheMaps.get(userId);
            if (parentBorrow.getType() == 0) {
                userCache.setTjWaitCollectionPrincipal(userCache.getTjWaitCollectionPrincipal() - principal);
                userCache.setTjWaitCollectionInterest(userCache.getTjWaitCollectionInterest() - interest);
            } else if (parentBorrow.getType() == 4) {
                userCache.setQdWaitCollectionPrincipal(userCache.getQdWaitCollectionPrincipal() - principal);
                userCache.setQdWaitCollectionInterest(userCache.getQdWaitCollectionInterest() - interest);
            }
            userCacheService.save(userCache);
        });
    }

    /**
     * 给投资人发放积分
     *
     * @param borrowCollectionList
     * @param parentBorrow
     */
    private void giveInterest(List<BorrowCollection> borrowCollectionList, Borrow parentBorrow) {
        borrowCollectionList.stream().forEach(borrowCollection -> {
            long actualInterest = borrowCollection.getCollectionMoneyYes() - borrowCollection.getPrincipal();/* 实收利息 */
            //投资积分
            long integral = actualInterest / 100 * 10;
            if ((parentBorrow.getType() == 0 || parentBorrow.getType() == 4) && 0 < integral) {
                IntegralChangeEntity integralChangeEntity = new IntegralChangeEntity();
                integralChangeEntity.setType(IntegralChangeEnum.TENDER);
                integralChangeEntity.setValue(integral);
                integralChangeEntity.setUserId(borrowCollection.getUserId());
                try {
                    integralChangeHelper.integralChange(integralChangeEntity);
                } catch (Exception e) {
                    log.error("投资人回款积分发放失败：", e);
                }
            }
        });
    }

    /**
     * 发送回款站内信
     *
     * @param borrowCollectionList
     * @param advance
     * @param parentBorrow
     */
    private void sendCollectionNotices(List<BorrowCollection> borrowCollectionList, boolean advance, Borrow parentBorrow) {

        //迭代投标人记录
        borrowCollectionList.stream().forEach(borrowCollection -> {
            long actualInterest = borrowCollection.getCollectionMoneyYes() - borrowCollection.getPrincipal();/* 实收利息 */
            String noticeContent = String.format("客户在%s已将借款[%s]第%s期还款,还款金额为%s元", DateHelper.dateToString(new Date(), "yyyy-MM-dd HH:mm:ss"), BorrowHelper.getBorrowLink(parentBorrow.getId()
                    , parentBorrow.getName()), (borrowCollection.getOrder() + 1), StringHelper.formatDouble(actualInterest, 100, true));
            if (advance) {
                noticeContent = "广富宝在" + DateHelper.dateToString(new Date(), "yyyy-MM-dd HH:mm:ss") + " 已将借款[" + BorrowHelper.getBorrowLink(parentBorrow.getId(), parentBorrow.getName()) +
                        "]第" + (borrowCollection.getOrder() + 1) + "期垫付还款,垫付金额为" + StringHelper.formatDouble(actualInterest, 100, true) + "元";
            }

            Notices notices = new Notices();
            notices.setFromUserId(1L);
            notices.setUserId(borrowCollection.getUserId());
            notices.setRead(false);
            notices.setName("客户还款");
            notices.setContent(noticeContent);
            notices.setType("system");
            notices.setCreatedAt(new Date());
            notices.setUpdatedAt(new Date());
            //发送站内信
            MqConfig mqConfig = new MqConfig();
            mqConfig.setQueue(MqQueueEnum.RABBITMQ_NOTICE);
            mqConfig.setTag(MqTagEnum.NOTICE_PUBLISH);
            Map<String, String> body = GSON.fromJson(GSON.toJson(notices), TypeTokenContants.MAP_TOKEN);
            mqConfig.setMsg(body);
            try {
                log.info(String.format("repaymentBizImpl sendCollectionNotices send mq %s", GSON.toJson(body)));
                mqHelper.convertAndSend(mqConfig);
            } catch (Throwable e) {
                log.error("repaymentBizImpl sendCollectionNotices send mq exception", e);
            }
        });
    }

    /**
     * 还款最后新增统计
     *
     * @param borrowRepayment
     */
    private void updateRepaymentStatistics(Borrow parentBorrow, BorrowRepayment borrowRepayment) {
        //更新统计数据
        try {
            long repayMoney = borrowRepayment.getRepayMoney();/* 还款金额 */
            long principal = borrowRepayment.getPrincipal();/* 还款本金 */
            Statistic statistic = new Statistic();
            statistic.setWaitRepayTotal(-repayMoney);
            if (!parentBorrow.isTransfer()) {//判断非转让标
                if (parentBorrow.getType() == 0) { //车贷标
                    statistic.setTjWaitRepayPrincipalTotal(-principal);
                    statistic.setTjWaitRepayTotal(-repayMoney);
                } else if (parentBorrow.getType() == 1) { //净值标
                    statistic.setJzWaitRepayPrincipalTotal(-principal);
                    statistic.setJzWaitRepayTotal(-repayMoney);
                } else if (parentBorrow.getType() == 4) { //渠道标
                    statistic.setQdWaitRepayPrincipalTotal(-principal);
                    statistic.setQdWaitRepayTotal(-repayMoney);
                }
            }
            if (!ObjectUtils.isEmpty(statistic)) {
                statisticBiz.caculate(statistic);
            }
        } catch (Throwable e) {
            log.error(String.format("repaymentBizImpl updateRepaymentStatistics 立即还款统计错误：", e));
        }
    }

    /**
     * 结束第三方债权并更新借款状态（还款最后一期的时候）
     *
     * @param borrowRepayment
     */
    private void endThirdTenderAndChangeBorrowStatus(Borrow parentBorrow, BorrowRepayment borrowRepayment) {
        // 结束债权：最后一期还款时
        if (borrowRepayment.getOrder() == (parentBorrow.getTotalOrder() - 1)) {
            parentBorrow.setCloseAt(borrowRepayment.getRepayAtYes());
            //推送队列结束债权
            MqConfig mqConfig = new MqConfig();
            mqConfig.setQueue(MqQueueEnum.RABBITMQ_CREDIT);
            mqConfig.setTag(MqTagEnum.END_CREDIT_BY_NOT_TRANSFER);
            mqConfig.setSendTime(DateHelper.addMinutes(new Date(), 1));
            ImmutableMap<String, String> body = ImmutableMap
                    .of(MqConfig.MSG_BORROW_ID, StringHelper.toString(parentBorrow.getId()), MqConfig.MSG_TIME, DateHelper.dateToString(new Date()));
            mqConfig.setMsg(body);
            try {
                log.info(String.format("repaymentBizImpl endThirdTenderAndChangeBorrowStatus send mq %s", GSON.toJson(body)));
                mqHelper.convertAndSend(mqConfig);
            } catch (Throwable e) {
                log.error("repaymentBizImpl endThirdTenderAndChangeBorrowStatus send mq exception", e);
            }
            parentBorrow.setUpdatedAt(new Date());
            borrowService.updateById(parentBorrow);
        }
    }

    /**
     * @param borrowRepayment
     * @throws Exception 3.判断是否是还名义借款人垫付，垫付需要改变垫付记录状态（逾期天数与日期应当在还款前计算完成）
     *                   4.还款成功后变更改还款状态（还款金额在还款前计算完成）
     */
    private void changeRepaymentAndRepayStatus(Borrow parentBorrow, List<Tender> tenderList, BorrowRepayment borrowRepayment, List<BorrowCollection> borrowCollectionList, boolean advance) throws Exception {
        //更改垫付记录、还款记录状态
        borrowRepayment.setStatus(1);
        borrowRepayment.setRepayAtYes(new Date());
        borrowRepaymentService.updateById(borrowRepayment);

        // 结束债权：最后一期还款时
        if (borrowRepayment.getOrder() == (parentBorrow.getTotalOrder() - 1)) {
            tenderList.stream().forEach(tender -> {
                tender.setState(3);
                tender.setUpdatedAt(new Date());
            });
            tenderService.save(tenderList);
        }

        //改变回款状态
        borrowCollectionList.stream().forEach(borrowCollection -> {
            borrowCollection.setStatus(1);
            borrowCollection.setCollectionAtYes(new Date());
            borrowCollection.setUpdatedAt(new Date());
        });
        borrowCollectionService.save(borrowCollectionList);

        if (advance) { //存在垫付时间则当条还款已经被垫付过
            AdvanceLog advanceLog = advanceLogService.findByRepaymentId(borrowRepayment.getId());
            Preconditions.checkNotNull(advanceLog, "RepaymentBizImpl changeRepaymentAndRepayStatus 垫付记录不存在!请联系客服。");

            //更新垫付记录转状态
            advanceLog.setStatus(1);
            advanceLog.setRepayAtYes(new Date());
            advanceLogService.save(advanceLog);
        }
    }

    /**
     * 新版立即还款
     * 1.还款判断
     * 2.
     *
     * @param repayReq
     * @return
     */
    @Transactional(rollbackFor = Throwable.class)
    public ResponseEntity<VoBaseResp> newRepay(VoRepayReq repayReq) throws Exception {
        /* 还款人id */
        long userId = repayReq.getUserId();
        /* 还款记录id */
        long borrowRepaymentId = repayReq.getRepaymentId();
        /* 利息百分比 */
        double interestPercent = repayReq.getInterestPercent();
        /* 是否是本人还款 */
        boolean isUserOpen = repayReq.getIsUserOpen();
        UserThirdAccount repayUserThirdAccount = userThirdAccountService.findByUserId(userId);
        Preconditions.checkNotNull(repayUserThirdAccount, "批量还款: 还款用户存管账户不存在");
        BorrowRepayment borrowRepayment = borrowRepaymentService.findByIdLock(borrowRepaymentId);
        Preconditions.checkNotNull(borrowRepayment, "批量还款: 还款记录不存在");
        Borrow parentBorrow = borrowService.findByIdLock(borrowRepayment.getBorrowId());
        Preconditions.checkNotNull(parentBorrow, "批量还款: 还款标的信息不存在");
        ResponseEntity<VoBaseResp> conditionResponse = repayConditionCheck(repayUserThirdAccount, borrowRepayment);  // 验证参数
        if (!conditionResponse.getStatusCode().equals(HttpStatus.OK)) {
            return conditionResponse;
        }

        int lateDays = getLateDays(borrowRepayment);   //计算逾期天数
        long lateInterest = calculateLateInterest(lateDays, borrowRepayment, parentBorrow);   // 计算逾期产生的总费用
        boolean advance = !ObjectUtils.isEmpty(borrowRepayment.getAdvanceAtYes());   // 是否是垫付
        String batchNo = jixinHelper.getBatchNo();    // 批次号
        String seqNo = assetChangeProvider.getSeqNo(); // 资产记录流水号
        String groupSeqNo = assetChangeProvider.getGroupSeqNo(); // 资产记录分组流水号
        // 生成投资人还款资金变动记录
        BatchAssetChange batchAssetChange = addBatchAssetChange(batchNo, borrowRepayment.getId(), advance);
        // 生成还款人还款批次资金改变记录
        addBatchAssetChangeByBorrower(batchAssetChange.getId(), borrowRepayment, parentBorrow, interestPercent, isUserOpen, lateInterest, seqNo, groupSeqNo);
        ResponseEntity resp;
        if (advance) {
            // 垫付还款
            resp = repayGuarantor(userId, repayUserThirdAccount, borrowRepayment, parentBorrow, lateInterest, batchNo, seqNo, groupSeqNo);
        } else {
            // 正常还款
            resp = normalRepay(userId, repayUserThirdAccount, borrowRepayment, parentBorrow, lateInterest, interestPercent, batchNo, batchAssetChange, seqNo, groupSeqNo);
        }

        //改变还款与垫付记录的值
        changeRepaymentAndAdvanceRecord(borrowRepayment, lateDays, lateInterest, advance);
        return resp;
    }

    /**
     * 改变还款与垫付记录的值
     *
     * @param borrowRepayment
     * @param lateDays
     * @param lateInterest
     * @param advance
     */
    public void changeRepaymentAndAdvanceRecord(BorrowRepayment borrowRepayment, int lateDays, long lateInterest, boolean advance) {
        Date nowDate = new Date();
        borrowRepayment.setLateDays(lateDays);
        borrowRepayment.setLateInterest(lateInterest);
        borrowRepayment.setRepayMoneyYes(borrowRepayment.getRepayMoney());
        borrowRepayment.setUpdatedAt(nowDate);
        borrowRepaymentService.save(borrowRepayment);
        if (advance) {
            AdvanceLog advanceLog = advanceLogService.findByRepaymentId(borrowRepayment.getId());/* 担保人还款记录 */
            Preconditions.checkNotNull(advanceLog, "垫付记录不存在!请联系客服");
            //更新垫付记录
            advanceLog.setRepayMoneyYes(borrowRepayment.getRepayMoney() + lateInterest);
            advanceLogService.save(advanceLog);
        }
    }


    /**
     * 新增资产更改记录
     *
     * @param batchNo
     * @param id
     * @param advance
     * @return
     */
    private BatchAssetChange addBatchAssetChange(String batchNo, Long id, boolean advance) {
        BatchAssetChange batchAssetChange = new BatchAssetChange();
        batchAssetChange.setSourceId(id);
        batchAssetChange.setState(0);
        if (advance) { // 还款人还垫付
            batchAssetChange.setType(BatchAssetChangeContants.BATCH_REPAY_BAIL);
        } else { //正常还款
            batchAssetChange.setType(BatchAssetChangeContants.BATCH_REPAY);
        }
        batchAssetChange.setCreatedAt(new Date());
        batchAssetChange.setUpdatedAt(new Date());
        batchAssetChange.setBatchNo(batchNo);
        batchAssetChangeService.save(batchAssetChange);
        return batchAssetChange;
    }

    /**
     * 生成还款人还款批次资金改变记录
     *
     * @param batchAssetChangeId
     * @param borrowRepayment
     * @param borrow
     * @param interestPercent
     * @param isUserOpen
     * @param lateInterest
     * @param seqNo
     * @param groupSeqNo
     */
    public void addBatchAssetChangeByBorrower(long batchAssetChangeId,
                                              BorrowRepayment borrowRepayment,
                                              Borrow borrow,
                                              double interestPercent,
                                              boolean isUserOpen,
                                              long lateInterest,
                                              String seqNo,
                                              String groupSeqNo) {
        Date nowDate = new Date();
        // 借款人还款
        BatchAssetChangeItem batchAssetChangeItem = new BatchAssetChangeItem();
        batchAssetChangeItem.setBatchAssetChangeId(batchAssetChangeId);
        batchAssetChangeItem.setState(0);
        batchAssetChangeItem.setType(AssetChangeTypeEnum.repayment.getLocalType());  // 还款
        batchAssetChangeItem.setUserId(borrow.getUserId());
        batchAssetChangeItem.setMoney(borrowRepayment.getPrincipal() + borrowRepayment.getInterest());
        batchAssetChangeItem.setRemark(String.format("对借款[%s]第%s期的还款",
                BorrowHelper.getBorrowLink(borrow.getId(), borrow.getName()),
                StringHelper.toString(borrowRepayment.getOrder() + 1)));
        if (interestPercent < 1) {
            batchAssetChangeItem.setRemark("（提前结清）");
        } else if (!isUserOpen) {
            batchAssetChangeItem.setRemark("（系统自动还款）");
        }
        batchAssetChangeItem.setCreatedAt(nowDate);
        batchAssetChangeItem.setUpdatedAt(nowDate);
        batchAssetChangeItem.setSourceId(borrowRepayment.getId());
        batchAssetChangeItem.setSeqNo(seqNo);
        batchAssetChangeItem.setGroupSeqNo(groupSeqNo);
        batchAssetChangeItemService.save(batchAssetChangeItem);

        if ((lateInterest > 0)) { // 扣除借款人还款滞纳金
            batchAssetChangeItem = new BatchAssetChangeItem();
            batchAssetChangeItem.setBatchAssetChangeId(batchAssetChangeId);
            batchAssetChangeItem.setState(0);
            batchAssetChangeItem.setType(AssetChangeTypeEnum.repayMentPenaltyFee.getLocalType());  // 扣除借款人还款滞纳金
            batchAssetChangeItem.setUserId(borrow.getUserId());
            batchAssetChangeItem.setMoney(lateInterest);
            batchAssetChangeItem.setRemark(String.format("借款[%s]的逾期罚息", BorrowHelper.getBorrowLink(borrow.getId(), borrow.getName())));
            batchAssetChangeItem.setCreatedAt(nowDate);
            batchAssetChangeItem.setUpdatedAt(nowDate);
            batchAssetChangeItem.setSourceId(borrowRepayment.getId());
            batchAssetChangeItem.setSeqNo(seqNo);
            batchAssetChangeItem.setGroupSeqNo(groupSeqNo);
            batchAssetChangeItemService.save(batchAssetChangeItem);
        }

        // 扣除借款人待还
        batchAssetChangeItem = new BatchAssetChangeItem();
        batchAssetChangeItem.setBatchAssetChangeId(batchAssetChangeId);
        batchAssetChangeItem.setState(0);
        batchAssetChangeItem.setType(AssetChangeTypeEnum.paymentSub.getLocalType());  // 扣除待还
        batchAssetChangeItem.setUserId(borrow.getUserId());
        batchAssetChangeItem.setMoney(borrowRepayment.getRepayMoney());
        batchAssetChangeItem.setInterest(borrowRepayment.getInterest());
        batchAssetChangeItem.setRemark("还款成功扣除待还");
        batchAssetChangeItem.setCreatedAt(nowDate);
        batchAssetChangeItem.setUpdatedAt(nowDate);
        batchAssetChangeItem.setSourceId(borrowRepayment.getId());
        batchAssetChangeItem.setSeqNo(seqNo);
        batchAssetChangeItem.setGroupSeqNo(groupSeqNo);
        batchAssetChangeItemService.save(batchAssetChangeItem);
    }

    /**
     * 生成名义借款人垫付批次资金改变记录
     */
    public void addBatchAssetChangeByGuarantor(long batchAssetChangeId, BorrowRepayment borrowRepayment, Borrow parentBorrow,
                                               long lateInterest, String seqNo, String groupSeqNo) {
        Date nowDate = new Date();
        AdvanceLog advanceLog = advanceLogService.findByRepaymentId(borrowRepayment.getId());/* 还款垫付记录 */
        Preconditions.checkNotNull(advanceLog, "垫付记录不存在!");

        // 借款人偿还名义借款人垫付款
        BatchAssetChangeItem batchAssetChangeItem = new BatchAssetChangeItem();
        batchAssetChangeItem.setBatchAssetChangeId(batchAssetChangeId);
        batchAssetChangeItem.setState(0);
        batchAssetChangeItem.setType(AssetChangeTypeEnum.compensatoryReceivedPayments.getLocalType());  // 借款人偿还名义借款人垫付款
        batchAssetChangeItem.setUserId(advanceLog.getUserId());
        batchAssetChangeItem.setToUserId(parentBorrow.getUserId());
        batchAssetChangeItem.setMoney(borrowRepayment.getRepayMoney() + lateInterest);/* 还款金额加上逾期利息 */
        batchAssetChangeItem.setRemark(String.format("收到客户对借款[%s]第%s期垫付的还款",
                BorrowHelper.getBorrowLink(parentBorrow.getId(), parentBorrow.getName()),
                (borrowRepayment.getOrder() + 1)));
        batchAssetChangeItem.setCreatedAt(nowDate);
        batchAssetChangeItem.setUpdatedAt(nowDate);
        batchAssetChangeItem.setSourceId(borrowRepayment.getId());
        batchAssetChangeItem.setSeqNo(seqNo);
        batchAssetChangeItem.setGroupSeqNo(groupSeqNo);
        batchAssetChangeItemService.save(batchAssetChangeItem);
    }

    /**
     * @param userId
     * @param repayUserThirdAccount
     * @param borrowRepayment
     * @param parentBorrow
     * @param lateInterest
     * @return
     * @throws Exception
     */
    private ResponseEntity<VoBaseResp> repayGuarantor(Long userId,
                                                      UserThirdAccount repayUserThirdAccount, BorrowRepayment borrowRepayment,
                                                      Borrow parentBorrow, long lateInterest,
                                                      String batchNo, String seqNo, String groupSeqNo) throws Exception {
        Date nowDate = new Date();
        Specification<Tender> specification = Specifications
                .<Tender>and()
                .eq("status", 1)
                .eq("borrowId", parentBorrow.getId())
                .build();

        List<Tender> tenderList = tenderService.findList(specification);
        Preconditions.checkNotNull(tenderList, "借款人向到担保人还款计划: 获取投资记录为空");
        List<Long> tenderIds = tenderList.stream().map(p -> p.getId()).collect(Collectors.toList());

        //查询已经回款的
        Specification<BorrowCollection> bcs = Specifications
                .<BorrowCollection>and()
                .in("tenderId", tenderIds.toArray())
                .eq("status", 1)
                .eq("order", borrowRepayment.getOrder())
                .build();

        List<BorrowCollection> borrowCollectionList = borrowCollectionService.findList(bcs);
        Preconditions.checkNotNull(borrowCollectionList, "借款人向到担保人还款计划: 获取回款计划列表为空!");
        Map<Long/** 投资记录*/, BorrowCollection/** 对应的还款计划*/> borrowCollectionMap = borrowCollectionList
                .stream()
                .collect(Collectors.toMap(BorrowCollection::getTenderId,
                        Function.identity()));

        log.info("借款人还款垫付人开始");
        List<RepayBail> repayBails = calculateRepayBailPlan(parentBorrow, repayUserThirdAccount.getAccountId(), getLateDays(borrowRepayment), borrowRepayment.getOrder(), lateInterest);
        double txAmount = repayBails.stream().mapToDouble(r -> NumberHelper.toDouble(r.getTxAmount())).sum();
        double intAmount = repayBails.stream().mapToDouble(r -> NumberHelper.toDouble(r.getIntAmount())).sum();  //所有交易利息
        double txFeeOut = repayBails.stream().mapToDouble(r -> NumberHelper.toDouble(r.getTxFeeOut())).sum();   //所有还款手续费
        double freezeMoney = txAmount + intAmount + txFeeOut;  //冻结金额
        //生成担保人还垫付资产变更记录
        addBatchAssetChangeByGuarantor(borrowRepayment.getId(), borrowRepayment, parentBorrow, lateInterest, seqNo, groupSeqNo);
        /* 冻结orderId */
        String freezeOrderId = JixinHelper.getOrderId(JixinHelper.BALANCE_FREEZE_PREFIX);
        try {
            BalanceFreezeReq balanceFreezeReq = new BalanceFreezeReq();
            balanceFreezeReq.setAccountId(repayUserThirdAccount.getAccountId());
            balanceFreezeReq.setTxAmount(StringHelper.formatDouble(freezeMoney, false));
            balanceFreezeReq.setOrderId(freezeOrderId);
            balanceFreezeReq.setChannel(ChannelContant.HTML);
            BalanceFreezeResp balanceFreezeResp = jixinManager.send(JixinTxCodeEnum.BALANCE_FREEZE, balanceFreezeReq, BalanceFreezeResp.class);
            if ((ObjectUtils.isEmpty(balanceFreezeReq)) || (!JixinResultContants.SUCCESS.equalsIgnoreCase(balanceFreezeResp.getRetCode()))) {
                throw new Exception("即信借款人还款垫付人冻结资金失败：" + balanceFreezeResp.getRetMsg());
            }

            //立即还款冻结可用资金
            AssetChange assetChange = new AssetChange();
            assetChange.setType(AssetChangeTypeEnum.freeze);  // 立即还款冻结可用资金
            assetChange.setUserId(parentBorrow.getUserId());
            assetChange.setMoney(new Double(freezeMoney * 100).longValue());
            assetChange.setRemark("借款人还款垫付人冻结可用资金");
            assetChange.setSourceId(parentBorrow.getId());
            assetChange.setSeqNo(assetChangeProvider.getSeqNo());
            assetChange.setGroupSeqNo(assetChangeProvider.getSeqNo());
            assetChangeProvider.commonAssetChange(assetChange);

            Map<String, Object> acqResMap = new HashMap<>();
            acqResMap.put("userId", userId);
            acqResMap.put("repaymentId", borrowRepayment.getId());
            acqResMap.put("interestPercent", 1d);
            acqResMap.put("isUserOpen", true);
            acqResMap.put("freezeOrderId", freezeOrderId);
            acqResMap.put("freezeMoney", freezeMoney);

            BatchRepayBailReq request = new BatchRepayBailReq();
            request.setBatchNo(batchNo);
            request.setTxAmount(StringHelper.formatDouble(txAmount, false));
            request.setSubPacks(GSON.toJson(repayBails));
            request.setTxCounts(StringHelper.toString(repayBails.size()));
            request.setNotifyURL(javaDomain + "/pub/repayment/v2/third/batch/repaybail/check");
            request.setRetNotifyURL(javaDomain + "/pub/repayment/v2/third/batch/repaybail/run");
            request.setAcqRes(GSON.toJson(acqResMap));
            request.setChannel(ChannelContant.HTML);
            BatchRepayBailResp response = jixinManager.send(JixinTxCodeEnum.BATCH_REPAY_BAIL, request, BatchRepayBailResp.class);
            if ((ObjectUtils.isEmpty(response)) || (!JixinResultContants.BATCH_SUCCESS.equalsIgnoreCase(response.getReceived()))) {
                return ResponseEntity.badRequest().body(VoBaseResp.error(VoBaseResp.ERROR, "批次融资人还担保账户垫款失败!"));
            }

            //记录日志
            ThirdBatchLog thirdBatchLog = new ThirdBatchLog();
            thirdBatchLog.setBatchNo(batchNo);
            thirdBatchLog.setCreateAt(nowDate);
            thirdBatchLog.setUpdateAt(nowDate);
            thirdBatchLog.setSourceId(borrowRepayment.getId());
            thirdBatchLog.setType(ThirdBatchLogContants.BATCH_REPAY_BAIL);
            thirdBatchLog.setRemark("批次融资人还担保账户垫款");
            thirdBatchLog.setAcqRes(GSON.toJson(acqResMap));
            thirdBatchLogService.save(thirdBatchLog);
        } catch (Exception e) {
            // 申请即信还款解冻
            String unfreezeOrderId = JixinHelper.getOrderId(JixinHelper.BALANCE_UNFREEZE_PREFIX);
            BalanceUnfreezeReq balanceUnfreezeReq = new BalanceUnfreezeReq();
            balanceUnfreezeReq.setAccountId(repayUserThirdAccount.getAccountId());
            balanceUnfreezeReq.setTxAmount(StringHelper.formatDouble(freezeMoney, false));
            balanceUnfreezeReq.setOrderId(unfreezeOrderId);
            balanceUnfreezeReq.setOrgOrderId(freezeOrderId);
            balanceUnfreezeReq.setChannel(ChannelContant.HTML);
            BalanceFreezeResp balanceFreezeResp = jixinManager.send(JixinTxCodeEnum.BALANCE_UN_FREEZE, balanceUnfreezeReq, BalanceFreezeResp.class);
            if ((ObjectUtils.isEmpty(balanceUnfreezeReq)) || (!JixinResultContants.SUCCESS.equalsIgnoreCase(balanceFreezeResp.getRetCode()))) {
                throw new Exception("名义借款人还垫付解冻资金异常：" + balanceFreezeResp.getRetMsg());
            }
        }
        return ResponseEntity.ok(VoBaseResp.ok("批次融资人还担保账户垫款成功!"));

    }

    /**
     * 生成借款人偿还担保人计划
     *
     * @param borrow
     * @param repayAccountId
     * @param lateDays
     * @param order
     * @param lateInterest
     * @return
     * @throws Exception
     */
    public List<RepayBail> calculateRepayBailPlan(Borrow borrow, String repayAccountId, int lateDays, Integer order, long lateInterest) throws Exception {
        List<RepayBail> repayBailList = new ArrayList<>();
        Specification<Tender> specification = Specifications
                .<Tender>and()
                .eq("status", 1)
                .eq("borrowId", borrow.getId())
                .build();

        List<Tender> tenderList = tenderService.findList(specification);
        Preconditions.checkNotNull(tenderList, "借款人向到担保人还款计划: 获取投资记录为空");
        List<Long> tenderIds = tenderList.stream().map(p -> p.getId()).collect(Collectors.toList());

        //查询已经回款的
        Specification<BorrowCollection> bcs = Specifications
                .<BorrowCollection>and()
                .in("tenderId", tenderIds.toArray())
                .eq("status", 1)
                .eq("order", order)
                .build();

        List<BorrowCollection> borrowCollectionList = borrowCollectionService.findList(bcs);
        Preconditions.checkNotNull(borrowCollectionList, "借款人向到担保人还款计划: 获取回款计划列表为空!");
        Map<Long/** 投资记录*/, BorrowCollection/** 对应的还款计划*/> borrowCollectionMap = borrowCollectionList
                .stream()
                .collect(Collectors.toMap(BorrowCollection::getTenderId,
                        Function.identity()));
        for (Tender tender : tenderList) {
            int inIn = 0;
            int inPr = 0;
            int outFee = 0;
            BorrowCollection borrowCollection = borrowCollectionMap.get(tender.getId()); // 已经还款金额
            if (tender.getTransferFlag() == 1) { // 标的转让中时, 需要取消出让信息
                transferBiz.cancelTransferByTenderId(tender.getId());
            }

            if (tender.getTransferFlag() == 2) {  // 出现转让后, 需要递归处理
                continue;
            }

            // 生成还款计划
            inIn += borrowCollection.getInterest(); // 利息
            inPr += borrowCollection.getPrincipal(); // 本金
            if ((lateDays > 0) && (lateInterest > 0)) {  //借款人逾期罚息
                inIn += new Double(tender.getValidMoney() / new Double(borrow.getMoney()) * lateInterest).intValue();
            }
            String orderId = JixinHelper.getOrderId(JixinHelper.BAIL_REPAY_PREFIX);
            RepayBail repayBail = new RepayBail();
            repayBail.setOrderId(orderId);
            repayBail.setAccountId(repayAccountId);
            repayBail.setTxAmount(StringHelper.formatDouble(inPr, 100, false));
            repayBail.setIntAmount(StringHelper.formatDouble(inIn, 100, false));
            repayBail.setForAccountId(borrow.getBailAccountId());
            repayBail.setTxFeeOut(StringHelper.formatDouble(outFee, 100, false));
            /*repayBail.setOrgOrderId(borrowCollection.getTAdvanceOrderId());
            repayBail.setAuthCode(borrowCollection.getTAdvanceAuthCode());*/
            repayBailList.add(repayBail);
        }
        tenderService.save(tenderList);
        return repayBailList;
    }

    /**
     * 正常还款流程
     *
     * @param userId
     * @param repayUserThirdAccount
     * @param borrowRepayment
     * @param borrow
     * @param batchNo
     * @param batchAssetChange
     * @param seqNo
     * @param groupSeqNo
     * @return
     * @throws Exception
     */
    private ResponseEntity<VoBaseResp> normalRepay(long userId,
                                                   UserThirdAccount repayUserThirdAccount,
                                                   BorrowRepayment borrowRepayment,
                                                   Borrow borrow,
                                                   long lateInterest,
                                                   double interestPercent,
                                                   String batchNo,
                                                   BatchAssetChange batchAssetChange,
                                                   String seqNo,
                                                   String groupSeqNo) throws Exception {
        Date nowDate = new Date();
        log.info("批次还款: 进入正常还款流程");
        /* 投资记录：不包含理财计划 */
        Specification<Tender> specification = Specifications
                .<Tender>and()
                .eq("status", 1)
                .eq("borrowId", borrow.getId())
                .build();
        List<Tender> tenderList = tenderService.findList(specification);
        Preconditions.checkState(!CollectionUtils.isEmpty(tenderList), "投资记录不存在!");
        /* 投资id集合 */
        List<Long> tenderIds = tenderList.stream().map(p -> p.getId()).collect(Collectors.toList());
        /* 投资人回款记录 */
        Specification<BorrowCollection> bcs = Specifications
                .<BorrowCollection>and()
                .in("tenderId", tenderIds.toArray())
                .eq("status", 0)
                .eq("transferFlag", 0)
                .eq("order", borrowRepayment.getOrder())
                .build();
        List<BorrowCollection> borrowCollectionList = borrowCollectionService.findList(bcs);
        Preconditions.checkNotNull(borrowCollectionList, "生成即信还款计划: 获取回款计划列表为空!");
        /* 资金变动集合 */
        List<RepayAssetChange> repayAssetChanges = new ArrayList<>();
        List<Repay> repays = calculateRepayPlan(borrow,
                repayUserThirdAccount.getAccountId(),
                borrowRepayment,
                tenderList,
                borrowCollectionList,
                lateInterest,
                interestPercent,
                repayAssetChanges);
        // 生成资金变动记录
        doGenerateAssetChangeRecodeByRepay(borrow, borrowRepayment, borrowRepayment.getUserId(), repayAssetChanges, seqNo, groupSeqNo, batchAssetChange);
        //所有交易金额 交易金额指的是txAmount字段
        double txAmount = repays.stream().mapToDouble(r -> NumberHelper.toDouble(r.getTxAmount())).sum();
        //所有交易利息
        double intAmount = repays.stream().mapToDouble(r -> NumberHelper.toDouble(r.getIntAmount())).sum();
        //所有还款手续费
        double txFeeOut = repays.stream().mapToDouble(r -> NumberHelper.toDouble(r.getTxFeeOut())).sum();
        double freezeMoney = txAmount + txFeeOut + intAmount;/* 冻结金额 */
        // 申请即信还款冻结
        String freezeOrderId = JixinHelper.getOrderId(JixinHelper.BALANCE_FREEZE_PREFIX);
        try {
            BalanceFreezeReq balanceFreezeReq = new BalanceFreezeReq();
            balanceFreezeReq.setAccountId(repayUserThirdAccount.getAccountId());
            balanceFreezeReq.setTxAmount(StringHelper.formatDouble(freezeMoney, false));
            balanceFreezeReq.setOrderId(freezeOrderId);
            balanceFreezeReq.setChannel(ChannelContant.HTML);
            BalanceFreezeResp balanceFreezeResp = jixinManager.send(JixinTxCodeEnum.BALANCE_FREEZE, balanceFreezeReq, BalanceFreezeResp.class);
            if ((ObjectUtils.isEmpty(balanceFreezeReq)) || (!JixinResultContants.SUCCESS.equalsIgnoreCase(balanceFreezeResp.getRetCode()))) {
                throw new Exception("正常还款流程：" + balanceFreezeResp.getRetMsg());
            }

            // 冻结还款金额
            long money = new Double((freezeMoney) * 100).longValue();
            AssetChange freezeAssetChange = new AssetChange();
            freezeAssetChange.setForUserId(repayUserThirdAccount.getUserId());
            freezeAssetChange.setUserId(repayUserThirdAccount.getUserId());
            freezeAssetChange.setType(AssetChangeTypeEnum.freeze);
            freezeAssetChange.setRemark(String.format("成功还款标的[%s]冻结资金%s元", borrow.getName(), StringHelper.formatDouble(money / 100D, true)));
            freezeAssetChange.setSeqNo(assetChangeProvider.getSeqNo());
            freezeAssetChange.setMoney(money);
            freezeAssetChange.setGroupSeqNo(assetChangeProvider.getGroupSeqNo());
            freezeAssetChange.setSourceId(borrowRepayment.getId());
            assetChangeProvider.commonAssetChange(freezeAssetChange);

            //批量放款
            Map<String, Object> acqResMap = new HashMap<>();
            acqResMap.put("userId", userId);
            acqResMap.put("repaymentId", borrowRepayment.getId());
            acqResMap.put("interestPercent", 1d);
            acqResMap.put("isUserOpen", true);
            acqResMap.put("freezeOrderId", freezeOrderId);
            acqResMap.put("freezeMoney", freezeMoney);

            //批次还款操作
            BatchRepayReq request = new BatchRepayReq();
            request.setBatchNo(batchNo);
            request.setTxAmount(StringHelper.formatDouble(txAmount, false));
            request.setRetNotifyURL(javaDomain + "/pub/repayment/v2/third/batch/repayDeal/run");
            request.setNotifyURL(javaDomain + "/pub/repayment/v2/third/batch/repayDeal/check");
            request.setAcqRes(GSON.toJson(acqResMap));
            request.setSubPacks(GSON.toJson(repays));
            request.setChannel(ChannelContant.HTML);
            request.setTxCounts(StringHelper.toString(repays.size()));
            BatchRepayResp response = jixinManager.send(JixinTxCodeEnum.BATCH_REPAY, request, BatchRepayResp.class);
            if ((ObjectUtils.isEmpty(response)) || (!JixinResultContants.BATCH_SUCCESS.equalsIgnoreCase(response.getReceived()))) {
                throw new Exception(response.getRetMsg());
            }

            //记录日志
            ThirdBatchLog thirdBatchLog = new ThirdBatchLog();
            thirdBatchLog.setBatchNo(batchNo);
            thirdBatchLog.setCreateAt(nowDate);
            thirdBatchLog.setUpdateAt(nowDate);
            thirdBatchLog.setSourceId(borrowRepayment.getId());
            thirdBatchLog.setType(ThirdBatchLogContants.BATCH_REPAY);
            thirdBatchLog.setRemark("即信批次还款");
            thirdBatchLog.setAcqRes(GSON.toJson(acqResMap));
            thirdBatchLogService.save(thirdBatchLog);
        } catch (Exception e) {
            // 申请即信还款解冻
            String unfreezeOrderId = JixinHelper.getOrderId(JixinHelper.BALANCE_UNFREEZE_PREFIX);
            BalanceUnfreezeReq balanceUnfreezeReq = new BalanceUnfreezeReq();
            balanceUnfreezeReq.setAccountId(repayUserThirdAccount.getAccountId());
            balanceUnfreezeReq.setTxAmount(StringHelper.formatDouble(freezeMoney, false));
            balanceUnfreezeReq.setOrderId(unfreezeOrderId);
            balanceUnfreezeReq.setOrgOrderId(freezeOrderId);
            balanceUnfreezeReq.setChannel(ChannelContant.HTML);
            BalanceFreezeResp balanceFreezeResp = jixinManager.send(JixinTxCodeEnum.BALANCE_UN_FREEZE, balanceUnfreezeReq, BalanceFreezeResp.class);
            if ((ObjectUtils.isEmpty(balanceUnfreezeReq)) || (!JixinResultContants.SUCCESS.equalsIgnoreCase(balanceFreezeResp.getRetCode()))) {
                throw new Exception("正常还款解冻资金异常：" + balanceFreezeResp.getRetMsg());
            }
        }
        return ResponseEntity.ok(VoBaseResp.ok("还款正常"));
    }

    /**
     * 生成存管还款计划(递归调用解决转让问题)
     *
     * @param borrow
     * @param repayAccountId
     * @param borrowRepayment
     * @param lateInterest
     * @param repayAssetChanges
     * @return
     * @throws Exception
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Repay> calculateRepayPlan(Borrow borrow, String repayAccountId, BorrowRepayment borrowRepayment, List<Tender> tenderList,
                                          List<BorrowCollection> borrowCollectionList,
                                          long lateInterest, double interestPercent, List<RepayAssetChange> repayAssetChanges) throws Exception {
        List<Repay> repayList = new ArrayList<>();
        Map<Long/* 投资记录*/, BorrowCollection/* 对应的还款计划*/> borrowCollectionMap = borrowCollectionList.stream().collect(Collectors.toMap(BorrowCollection::getTenderId, Function.identity()));
        /* 投资会员id集合 */
        Set<Long> userIds = tenderList.stream().map(p -> p.getUserId()).collect(Collectors.toSet());
        /* 逾期日期 */
        int lateDays = getLateDays(borrowRepayment);
        /* 投资人存管记录列表 */
        Specification<UserThirdAccount> uts = Specifications
                .<UserThirdAccount>and()
                .in("userId", userIds.toArray())
                .build();
        List<UserThirdAccount> userThirdAccountList = userThirdAccountService.findList(uts);
        Preconditions.checkNotNull(userThirdAccountList, "生成即信还款计划: 查询用户存管开户记录列表为空!");
        Map<Long/* 用户ID*/, UserThirdAccount /* 用户存管*/> userThirdAccountMap = userThirdAccountList
                .stream()
                .collect(Collectors.toMap(UserThirdAccount::getUserId, Function.identity()));
        /* 当期回款总利息 */
        long sumCollectionInterest = borrowCollectionList.stream().mapToLong(BorrowCollection::getInterest).sum();
        for (Tender tender : tenderList) {
            RepayAssetChange repayAssetChange = new RepayAssetChange();
            repayAssetChanges.add(repayAssetChange);
            long inIn = 0; // 出借人的利息
            long inPr = 0; // 出借人的本金
            int inFee = 0; // 出借人利息费用
            int outFee = 0; // 借款人管理费
            BorrowCollection borrowCollection = borrowCollectionMap.get(tender.getId());  // 还款计划
            if (tender.getTransferFlag() == 1) {
                transferBiz.cancelTransferByTenderId(tender.getId()); // 标的转让中时, 需要取消出让信息
            }
            /**
             * @// TODO: 2017/8/18 资金变动有问题
             */
            if (tender.getTransferFlag() == 2 || ObjectUtils.isEmpty(borrowCollection)) {  // 已经转让的债权, 可以跳过还款
                continue;
            }
            inIn = (long) MathHelper.myRound(borrowCollection.getInterest() * interestPercent, 0); // 还款利息
            inPr = borrowCollection.getPrincipal(); // 还款本金
            repayAssetChange.setUserId(tender.getUserId());
            repayAssetChange.setInterest(inIn);
            repayAssetChange.setPrincipal(inPr);

            ImmutableSet<Integer> borrowTypeSet = ImmutableSet.of(0, 4);
            if (borrowTypeSet.contains(borrow.getType())) {  // 车贷标和渠道标利息管理费
                ImmutableSet<Long> stockholder = ImmutableSet.of(2480L, 1753L, 1699L,
                        3966L, 1413L, 1857L,
                        183L, 2327L, 2432L,
                        2470L, 2552L, 2739L,
                        3939L, 893L, 608L,
                        1216L);
                boolean between = isBetween(new Date(), DateHelper.stringToDate("2015-12-25 00:00:00"),
                        DateHelper.stringToDate("2017-12-25 23:59:59"));
                if ((stockholder.contains(tender.getUserId())) && (between)) {
                    inFee += 0;
                } else {
                    inFee += new Double(MathHelper.myRound(inIn * 0.1, 2)).intValue();
                }
            }

            repayAssetChange.setInterestFee(inFee);
            long overdueFee = 0;
            long platformOverdueFee = 0;
            if ((lateDays > 0) && (lateInterest > 0)) {  //借款人逾期罚息
                overdueFee = new Double(borrowCollection.getInterest() / new Double(sumCollectionInterest) * lateInterest / 2).intValue();// 出借人收取50% 逾期管理费 ;
                repayAssetChange.setOverdueFee(overdueFee);
                inIn += overdueFee;
                platformOverdueFee = new Double(borrowCollection.getInterest() / new Double(sumCollectionInterest) * lateInterest / 2).intValue(); // 平台收取50% 逾期管理费
                repayAssetChange.setPlatformOverdueFee(platformOverdueFee);
                outFee += platformOverdueFee;
            }

            /* 还款orderId */
            String orderId = JixinHelper.getOrderId(JixinHelper.REPAY_PREFIX);
            Repay repay = new Repay();
            repay.setAccountId(repayAccountId);
            repay.setOrderId(orderId);
            repay.setTxAmount(StringHelper.formatDouble(inPr, 100, false));
            repay.setIntAmount(StringHelper.formatDouble(inIn, 100, false));
            repay.setTxFeeIn(StringHelper.formatDouble(inFee, 100, false));
            repay.setTxFeeOut(StringHelper.formatDouble(outFee, 100, false));
            repay.setProductId(borrow.getProductId());
            repay.setAuthCode(tender.isTransferTender() ? tender.getTransferAuthCode() : tender.getAuthCode());
            UserThirdAccount userThirdAccount = userThirdAccountMap.get(tender.getUserId());
            Preconditions.checkNotNull(userThirdAccount, "投资人未开户!");
            repay.setForAccountId(userThirdAccount.getAccountId());
            repayList.add(repay);
            //改变回款状态
            borrowCollection.setTRepayOrderId(orderId);
            borrowCollection.setLateInterest(overdueFee);
            borrowCollection.setCollectionMoneyYes(borrowCollection.getCollectionMoney());
            borrowCollection.setUpdatedAt(new Date());
            borrowCollectionService.updateById(borrowCollection);
        }
        return repayList;
    }

    /**
     * 生成还款记录
     *
     * @param borrow
     * @param borrowRepayment
     * @param userId
     * @param repayAssetChanges
     * @param batchAssetChange
     */
    private void doGenerateAssetChangeRecodeByRepay(Borrow borrow, BorrowRepayment borrowRepayment, Long userId, List<RepayAssetChange> repayAssetChanges, String seqNo, String groupSeqNo, BatchAssetChange batchAssetChange) throws ExecutionException {
        long batchAssetChangeId = batchAssetChange.getId();
        Long feeAccountId = assetChangeProvider.getFeeAccountId();  // 平台收费账户ID
        Date nowDate = new Date();
        for (RepayAssetChange repayAssetChange : repayAssetChanges) {
            // 归还本金和利息
            BatchAssetChangeItem batchAssetChangeItem = new BatchAssetChangeItem();
            batchAssetChangeItem.setBatchAssetChangeId(batchAssetChangeId);
            batchAssetChangeItem.setState(0);
            batchAssetChangeItem.setType(AssetChangeTypeEnum.receivedPayments.getLocalType());  // 投资人收到还款
            batchAssetChangeItem.setUserId(repayAssetChange.getUserId());
            batchAssetChangeItem.setToUserId(userId);  // 出借人
            batchAssetChangeItem.setMoney(repayAssetChange.getPrincipal() + repayAssetChange.getInterest());   // 本金加利息
            batchAssetChangeItem.setInterest(repayAssetChange.getInterest());  // 利息
            batchAssetChangeItem.setRemark(String.format("收到客户对借款[%s]第%s期的还款", borrow.getName(), (borrowRepayment.getOrder() + 1)));
            batchAssetChangeItemService.save(batchAssetChangeItem);
            // 扣除利息管理费
            if (repayAssetChange.getInterestFee() > 0) {
                batchAssetChangeItem = new BatchAssetChangeItem();
                batchAssetChangeItem.setBatchAssetChangeId(batchAssetChangeId);
                batchAssetChangeItem.setState(0);
                batchAssetChangeItem.setType(AssetChangeTypeEnum.interestManagementFee.getLocalType());  // 扣除投资人利息管理费
                batchAssetChangeItem.setUserId(repayAssetChange.getUserId());
                batchAssetChangeItem.setToUserId(feeAccountId);
                batchAssetChangeItem.setMoney(repayAssetChange.getInterestFee());
                batchAssetChangeItem.setRemark(String.format("扣除借款标的[%s]利息管理费%s元", borrow.getName(), StringHelper.formatDouble(repayAssetChange.getInterestFee() / 100D, false)));
                batchAssetChangeItem.setCreatedAt(nowDate);
                batchAssetChangeItem.setUpdatedAt(nowDate);
                batchAssetChangeItem.setSourceId(borrowRepayment.getId());
                batchAssetChangeItem.setSeqNo(seqNo);
                batchAssetChangeItem.setGroupSeqNo(groupSeqNo);
                batchAssetChangeItemService.save(batchAssetChangeItem);

                // 收费账户添加利息管理费用
                batchAssetChangeItem = new BatchAssetChangeItem();
                batchAssetChangeItem.setBatchAssetChangeId(batchAssetChangeId);
                batchAssetChangeItem.setState(0);
                batchAssetChangeItem.setType(AssetChangeTypeEnum.platformInterestManagementFee.getLocalType());  // 收费账户添加利息管理费用
                batchAssetChangeItem.setUserId(feeAccountId);
                batchAssetChangeItem.setToUserId(repayAssetChange.getUserId());
                batchAssetChangeItem.setMoney(repayAssetChange.getInterestFee());
                batchAssetChangeItem.setRemark(String.format("收取借款标的[%s]利息管理费%s元", borrow.getName(), StringHelper.formatDouble(repayAssetChange.getInterestFee() / 100D, false)));
                batchAssetChangeItem.setCreatedAt(nowDate);
                batchAssetChangeItem.setUpdatedAt(nowDate);
                batchAssetChangeItem.setSourceId(borrowRepayment.getId());
                batchAssetChangeItem.setSeqNo(seqNo);
                batchAssetChangeItem.setGroupSeqNo(groupSeqNo);
                batchAssetChangeItemService.save(batchAssetChangeItem);
            }

            // 收取逾期管理费
            if (repayAssetChange.getPlatformOverdueFee() > 0) {
                batchAssetChangeItem = new BatchAssetChangeItem();
                batchAssetChangeItem.setBatchAssetChangeId(batchAssetChangeId);
                batchAssetChangeItem.setState(0);
                batchAssetChangeItem.setType(AssetChangeTypeEnum.platformRepayMentPenaltyFee.getLocalType());  // 收费账户添加利息管理费用
                batchAssetChangeItem.setUserId(feeAccountId);
                batchAssetChangeItem.setToUserId(repayAssetChange.getUserId());
                batchAssetChangeItem.setMoney(repayAssetChange.getPlatformOverdueFee());
                batchAssetChangeItem.setRemark(String.format("收取借款标的[%s]逾期管理费%s元", borrow.getName(), StringHelper.formatDouble(repayAssetChange.getPlatformOverdueFee() / 100D, false)));
                batchAssetChangeItem.setCreatedAt(nowDate);
                batchAssetChangeItem.setUpdatedAt(nowDate);
                batchAssetChangeItem.setSourceId(borrowRepayment.getId());
                batchAssetChangeItem.setSeqNo(seqNo);
                batchAssetChangeItem.setGroupSeqNo(groupSeqNo);
                batchAssetChangeItemService.save(batchAssetChangeItem);
            }

            //扣除投资人待收
            batchAssetChangeItem = new BatchAssetChangeItem();
            batchAssetChangeItem.setBatchAssetChangeId(batchAssetChangeId);
            batchAssetChangeItem.setState(0);
            batchAssetChangeItem.setType(AssetChangeTypeEnum.collectionSub.getLocalType());  //  扣除投资人待收
            batchAssetChangeItem.setUserId(repayAssetChange.getUserId());
            batchAssetChangeItem.setMoney(repayAssetChange.getInterest() + repayAssetChange.getPrincipal());
            batchAssetChangeItem.setInterest(repayAssetChange.getInterest());
            batchAssetChangeItem.setRemark(String.format("收到客户对[%s]借款的还款,扣除待收", borrow.getName()));
            batchAssetChangeItem.setCreatedAt(nowDate);
            batchAssetChangeItem.setUpdatedAt(nowDate);
            batchAssetChangeItem.setSourceId(borrowRepayment.getId());
            batchAssetChangeItem.setSeqNo(seqNo);
            batchAssetChangeItem.setGroupSeqNo(groupSeqNo);
            batchAssetChangeItemService.save(batchAssetChangeItem);
        }
    }


    /**
     * 获取用户逾期费用
     * 逾期规则: 未还款本金之和 * 0.4$ 的费用, 平台收取 0.2%, 出借人 0.2%
     *
     * @param borrowRepayment
     * @param repaymentBorrow
     * @return
     */

    private int calculateLateInterest(int lateDays, BorrowRepayment borrowRepayment, Borrow repaymentBorrow) {
        if (0 == lateDays) {
            return 0;
        }

        long overPrincipal = borrowRepayment.getPrincipal();
        if (borrowRepayment.getOrder() < (repaymentBorrow.getTotalOrder() - 1)) { //
            Specification<BorrowRepayment> brs = Specifications
                    .<BorrowRepayment>and()
                    .eq("borrowId", repaymentBorrow.getId())
                    .eq("status", 0)
                    .build();
            List<BorrowRepayment> borrowRepaymentList = borrowRepaymentService.findList(brs);
            Preconditions.checkNotNull(borrowRepayment, "垫付: 计算逾期费用时还款计划为空");
            //剩余未还本金
            overPrincipal = borrowRepaymentList.stream().mapToLong(w -> w.getPrincipal()).sum();
        }

        return new Double(MathHelper.myRound(overPrincipal * 0.004 * lateDays, 2)).intValue();
    }

    /**
     * 获取逾期天数
     *
     * @param borrowRepayment
     * @return
     */
    private int getLateDays(BorrowRepayment borrowRepayment) {
        Date nowDateOfBegin = DateHelper.beginOfDate(new Date());
        Date repayDateOfBegin = DateHelper.beginOfDate(borrowRepayment.getRepayAt());
        int lateDays = DateHelper.diffInDays(nowDateOfBegin, repayDateOfBegin, false);
        lateDays = lateDays < 0 ? 0 : lateDays;
        return lateDays;
    }

    /**
     * 根据
     *
     * @param tranferedTender
     * @return
     */
    private Map<Long, Borrow> findTranferedBorrowByTender(List<Tender> tranferedTender) {
        Map<Long, Borrow> refMap = new HashMap<>();
        tranferedTender.forEach((Tender tender) -> {
            Specification<Borrow> bs = Specifications
                    .<Borrow>and()
                    .eq("tenderId", tender.getId())
                    .eq("status", 3)
                    .build();
            List<Borrow> borrowList = borrowService.findList(bs);
            Preconditions.checkNotNull(borrowList, "批量还款: 查询转让标的为空");
            Borrow borrow = borrowList.get(0);
            refMap.put(tender.getId(), borrow);
        });

        return refMap;
    }


    /**
     * 查询已经债权转让成功投资记录
     *
     * @param tranferedTender
     * @return
     */
    private Map<Long, List<Tender>> findTranferedTenderRecord(List<Tender> tranferedTender) {

        Map<Long, List<Tender>> refMap = new HashMap<>();
        tranferedTender.forEach((Tender tender) -> {
            Specification<Borrow> bs = Specifications
                    .<Borrow>and()
                    .eq("tenderId", tender.getId())
                    .eq("status", 3)
                    .build();
            List<Borrow> borrowList = borrowService.findList(bs);
            Preconditions.checkNotNull(borrowList, "批量还款: 查询转让标的为空");
            Borrow borrow = borrowList.get(0);

            Specification<Tender> specification = Specifications
                    .<Tender>and()
                    .eq("status", 1)
                    .eq("borrowId", borrow.getId())
                    .build();

            List<Tender> tranferedTenderList = tenderService.findList(specification);
            Preconditions.checkNotNull(tranferedTenderList, "批量还款: 获取投资记录列表为空");
            refMap.put(tender.getId(), tranferedTenderList);
        });
        return refMap;
    }

    /**
     * 查询还款计划
     *
     * @param order
     * @param tenderList
     * @return
     */
    private List<BorrowCollection> queryBorrowCollectionByTender(int order, List<Tender> tenderList) {
        Set<Long> tenderIdSet = tenderList.stream().map(p -> p.getId()).collect(Collectors.toSet());
        Specification<BorrowCollection> bcs = Specifications
                .<BorrowCollection>and()
                .in("tenderId", tenderIdSet.toArray())
                .eq("status", 0)
                .eq("order", order)
                .build();

        List<BorrowCollection> borrowCollectionList = borrowCollectionService.findList(bcs);
        Preconditions.checkNotNull(borrowCollectionList, "批量还款: 查询还款计划为空");
        return borrowCollectionList;
    }

    /**
     * 获取正常投标记录
     *
     * @param borrowRepayment
     * @return
     */
    private List<Tender> queryTenderByRepayment(BorrowRepayment borrowRepayment) {
        Specification<Tender> specification = Specifications
                .<Tender>and()
                .eq("status", 1)
                .eq("borrowId", borrowRepayment.getBorrowId())
                .build();

        List<Tender> tenderList = tenderService.findList(specification);
        Preconditions.checkNotNull(tenderList, "批量还款: 获取投资记录列表为空");
        return tenderList;
    }


    /**
     * 用户还款前期判断
     * 1. 还款用户是否与还款计划用户一致
     * 2. 是否重复提交
     * 3. 判断是否跳跃还款
     *
     * @param userThirdAccount 用户开户
     * @param borrowRepayment  还款计划
     * @return
     */
    private ResponseEntity<VoBaseResp> repayConditionCheck(UserThirdAccount userThirdAccount, BorrowRepayment borrowRepayment) {
        // 1. 还款用户是否与还款计划用户一致
        if (!userThirdAccount.getUserId().equals(borrowRepayment.getUserId())) {
            log.error("批量还款: 还款前期判断, 还款计划用户与主动请求还款用户不匹配");
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "非法操作: 还款计划与当前请求用户不一致!"));
        }

        // 2判断提交还款批次是否多次重复提交
        int flag = thirdBatchLogBiz.checkBatchOftenSubmit(String.valueOf(borrowRepayment.getId()),
                ThirdBatchLogContants.BATCH_REPAY_BAIL,
                ThirdBatchLogContants.BATCH_REPAY);
        if (flag == ThirdBatchLogContants.AWAIT) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, StringHelper.toString("还款处理中，请勿重复点击!")));
        } else if (flag == ThirdBatchLogContants.SUCCESS) {
            /**
             * @// TODO: 2017/8/21  直接调用批次回调
             */
        }

        //  3. 判断是否跳跃还款
        Specification<BorrowRepayment> borrowRepaymentSpe = Specifications
                .<BorrowRepayment>and()
                .eq("borrowId", borrowRepayment.getBorrowId())
                .eq("status", 0)
                .predicate(new LtSpecification<BorrowRepayment>("order", new DataObject(borrowRepayment.getOrder())))
                .build();
        List<BorrowRepayment> borrowRepaymentList = borrowRepaymentService.findList(borrowRepaymentSpe);
        if (!CollectionUtils.isEmpty(borrowRepaymentList)) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, StringHelper.toString("该借款上一期还未还!")));
        }

        //4.判断是否在晚上9点30前还款
        Date endDate = DateHelper.beginOfDate(new Date());
        endDate = DateHelper.setHours(endDate, 21);
        endDate = DateHelper.setMinutes(endDate, 30);
        if (new Date().getTime() > endDate.getTime()) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, StringHelper.toString("还款截止时间为每天晚上9点30!")));
        }

        return ResponseEntity.ok(VoBaseResp.ok("验证成功"));
    }

    /**
     * 立即还款
     *
     * @param voPcInstantlyRepaymentReq
     * @return
     * @throws Exception
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<VoBaseResp> pcRepay(VoPcInstantlyRepaymentReq voPcInstantlyRepaymentReq) throws Exception {

        String paramStr = voPcInstantlyRepaymentReq.getParamStr();
        if (!SecurityHelper.checkSign(voPcInstantlyRepaymentReq.getSign(), paramStr)) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "pc取消借款 签名验证不通过!"));
        }
        Map<String, String> paramMap = GSON.fromJson(paramStr, TypeTokenContants.MAP_ALL_STRING_TOKEN);
        Long repaymentId = NumberHelper.toLong(paramMap.get("repaymentId"));
        BorrowRepayment borrowRepayment = borrowRepaymentService.findById(repaymentId);

        VoRepayReq voRepayReq = new VoRepayReq();
        voRepayReq.setRepaymentId(repaymentId);
        voRepayReq.setUserId(borrowRepayment.getUserId());
        return newRepay(voRepayReq);
    }

    /**
     * 垫付检查
     *
     * @param borrowRepayment
     * @return
     */
    public ResponseEntity<VoBaseResp> advanceCheck(BorrowRepayment borrowRepayment) throws Exception {
        Preconditions.checkNotNull(borrowRepayment, "还款记录不存在！");
        if (borrowRepayment.getStatus() != 0) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "还款状态已发生改变!"));
        }

        Borrow borrow = borrowService.findById(borrowRepayment.getBorrowId());
        Preconditions.checkNotNull(borrow, "借款记录不存在！");
        if (borrow.getType() != 1) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "只有净值标才能垫付!"));
        }
        if (StringUtils.isEmpty(borrow.getTitularBorrowAccountId())) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "当前借款没有登记名义借款人账号!"));
        }
        UserThirdAccount titularBorrowAccount = userThirdAccountService.findByAccountId(borrow.getTitularBorrowAccountId());
        Preconditions.checkNotNull(titularBorrowAccount, "当前名义收款账户开户信息为空");
        Asset advanceUserAsses = assetService.findByUserIdLock(titularBorrowAccount.getUserId());
        Specification<BorrowRepayment> brs = null;
        int order = borrowRepayment.getOrder();
        if (order > 0) {
            brs = Specifications
                    .<BorrowRepayment>and()
                    .eq("borrowId", borrowRepayment.getBorrowId())
                    .predicate(new LtSpecification("order", new DataObject(order)))
                    .eq("status", 0)
                    .build();
            if (borrowRepaymentService.count(brs) > 0) {
                return ResponseEntity
                        .badRequest()
                        .body(VoBaseResp.error(VoBaseResp.ERROR, "该借款上一期还未还，请先把上一期的还上!"));
            }
        }

        //判断提交还款批次是否多次重复提交
        int flag = thirdBatchLogBiz.checkBatchOftenSubmit(String.valueOf(borrowRepayment.getId()), ThirdBatchLogContants.BATCH_BAIL_REPAY);
        if (flag == ThirdBatchLogContants.AWAIT) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, StringHelper.toString("垫付处理中，请勿重复点击!")));
        } else if (flag == ThirdBatchLogContants.SUCCESS) {
            /**
             * @// TODO: 2017/7/18 增加本地查询
             */
        }

        long lateInterest = 0;//逾期利息
        int lateDays = 0;//逾期天数
        int diffDay = DateHelper.diffInDays(DateHelper.beginOfDate(new Date()), DateHelper.beginOfDate(borrowRepayment.getRepayAt()), false);
        if (diffDay > 0) {
            lateDays = diffDay;
            long overPrincipal = borrowRepayment.getPrincipal();//剩余未还本金
            if (order < (borrow.getTotalOrder() - 1)) {
                brs = Specifications
                        .<BorrowRepayment>and()
                        .eq("borrowId", borrow.getId())
                        .eq("status", 0)
                        .build();
                List<BorrowRepayment> borrowRepaymentList = borrowRepaymentService.findList(brs);
                //剩余未还本金
                overPrincipal = borrowRepaymentList.stream().mapToLong(w -> w.getPrincipal()).sum();
            }
            lateInterest = Math.round(overPrincipal * 0.004 * lateDays);
        }

        long repayInterest = borrowRepayment.getInterest();//还款利息
        long repayMoney = borrowRepayment.getPrincipal() + repayInterest;//还款金额
        if (advanceUserAsses.getUseMoney() < (repayMoney + lateInterest)) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "账户余额不足，请先充值"));
        }

        return ResponseEntity.ok(VoBaseResp.ok("垫付成功!"));
    }

    /**
     * pc垫付
     *
     * @param voPcAdvanceReq
     * @return
     * @throws Exception
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<VoBaseResp> pcAdvance(VoPcAdvanceReq voPcAdvanceReq) throws Exception {
        String paramStr = voPcAdvanceReq.getParamStr();
        if (!SecurityHelper.checkSign(voPcAdvanceReq.getSign(), paramStr)) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "pc取消借款 签名验证不通过!"));
        }
        Map<String, String> paramMap = GSON.fromJson(paramStr, TypeTokenContants.MAP_ALL_STRING_TOKEN);
        Long repaymentId = NumberHelper.toLong(paramMap.get("repaymentId"));

        VoAdvanceReq voAdvanceReq = new VoAdvanceReq();
        voAdvanceReq.setRepaymentId(repaymentId);
        return newAdvance(voAdvanceReq);
    }

    /**
     * 新版垫付
     *
     * @param voAdvanceReq
     * @return
     * @throws Exception
     */
    public ResponseEntity<VoBaseResp> newAdvance(VoAdvanceReq voAdvanceReq) throws Exception {
        long repaymentId = voAdvanceReq.getRepaymentId();/* 垫付还款id */
        //垫付前置判断
        BorrowRepayment borrowRepayment = borrowRepaymentService.findByIdLock(repaymentId);/* 垫付还款记录 */
        ResponseEntity<VoBaseResp> resp = advanceCheck(borrowRepayment);
        if (resp.getBody().getState().getCode() != VoBaseResp.OK) {
            return resp;
        }

        Preconditions.checkNotNull(borrowRepayment, "垫付还款记录不存在!");
        Borrow parentBorrow = borrowService.findById(borrowRepayment.getBorrowId());/* 借款记录 */
        Preconditions.checkNotNull(parentBorrow, "借款记录不存在!");
        long titularBorrowUserId = assetChangeProvider.getTitularBorrowUserId();  // 平台担保人ID
        Asset advanceUserAsses = assetService.findByUserIdLock(titularBorrowUserId);/* 垫付人资产记录 */
        /* 批次号 */
        String batchNo = jixinHelper.getBatchNo();
        /* 资产记录流水号 */
        String seqNo = assetChangeProvider.getSeqNo();
        /* 资产记录分组流水号 */
        String groupSeqNo = assetChangeProvider.getGroupSeqNo();
        /* 逾期天数 */
        int lateDays = getLateDays(borrowRepayment);
        long lateInterest = calculateLateInterest(lateDays, borrowRepayment, parentBorrow);/* 获取逾期利息 */
        long repayInterest = borrowRepayment.getInterest();//还款利息
        long repayMoney = borrowRepayment.getPrincipal() + repayInterest;//还款金额
        if (advanceUserAsses.getUseMoney() < (repayMoney + lateInterest)) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "账户余额不足，请先充值"));
        }
        // 生成垫付还款主记录
        BatchAssetChange batchAssetChange = addBatchAssetChangeByAdvance(repaymentId, batchNo);
        // 生成名义借款人垫付批次资产变更记录
        addBatchAssetChangeByAdvance(batchAssetChange.getId(), titularBorrowUserId, borrowRepayment, parentBorrow, lateInterest, seqNo, groupSeqNo);
        // 存管系统登记垫付
        resp = newAdvanceProcess(parentBorrow, borrowRepayment, batchAssetChange, lateInterest, lateDays, seqNo, groupSeqNo);
        if (resp.getBody().getState().getCode() != VoBaseResp.OK) {
            return resp;
        }
        return ResponseEntity.ok(VoBaseResp.ok("垫付成功!"));
    }

    /**
     * 生成债权转让记录
     *
     * @param borrow
     * @param tenderList
     * @param borrowCollectionMaps
     * @param titularBorrowUserId
     */
    private void addTransferTenderByAdvance(Borrow borrow, List<Transfer> transferList, List<TransferBuyLog> transferBuyLogList, List<Tender> tenderList, Map<Long/* tenderId */, BorrowCollection> borrowCollectionMaps, long titularBorrowUserId) {
        //创建债权转让信息，生成债权转让购买记录
        Map<Long/* 投资id */, Transfer> transferMaps = transferList.stream().collect(Collectors.toMap(Transfer::getTenderId, Function.identity()));
        Map<Long/* 债权转让id */, TransferBuyLog> transferBuyLogMaps = transferBuyLogList.stream().collect(Collectors.toMap(TransferBuyLog::getTransferId, Function.identity()));


        tenderList.stream().forEach(tender -> {
            /* 债权转让转让是否在存管系统登记成功 */
            boolean flag = false;
            Transfer transfer = transferMaps.get(tender.getId());
            if (!ObjectUtils.isEmpty(transfer)) {
                TransferBuyLog transferBuyLog = transferBuyLogMaps.get(transfer.getId());
                if (transferBuyLog.getThirdTransferFlag()) {
                    flag = true;
                }
            }
            if (flag) {
                return;
            }
            /* 回款记录 */
            BorrowCollection borrowCollection = borrowCollectionMaps.get(tender.getId());
            //计算当期应计利息
            long interest = borrowCollection.getInterest();/* 当期理论应计利息 */
            long principal = borrowCollection.getPrincipal();/* 当期应还本金 */
            Date startAt = DateHelper.beginOfDate(borrowCollection.getStartAt());//理论开始计息时间
            Date collectionAt = DateHelper.beginOfDate(borrowCollection.getCollectionAt());//理论结束还款时间
            Date startAtYes = DateHelper.beginOfDate(borrowCollection.getStartAtYes());//实际开始计息时间
            Date endAt = DateHelper.beginOfDate(new Date());//结束计息时间

            /* 当期应计利息 */
            long alreadyInterest = Math.round(interest *
                    Math.max(DateHelper.diffInDays(endAt, startAtYes, false), 0) /
                    DateHelper.diffInDays(collectionAt, startAt, false));
            //新增债权转让记录
            Date nowDate = new Date();
            transfer = new Transfer();
            transfer.setUpdatedAt(nowDate);
            transfer.setType(2);
            transfer.setUserId(tender.getUserId());
            transfer.setTransferMoney(principal + alreadyInterest);
            transfer.setTransferMoneyYes(1l);
            transfer.setVerifyAt(nowDate);
            transfer.setSuccessAt(nowDate);
            transfer.setDel(false);
            transfer.setBorrowId(borrow.getId());
            transfer.setPrincipal(principal);
            transfer.setAlreadyInterest(alreadyInterest);
            transfer.setApr(borrow.getApr());
            transfer.setCreatedAt(nowDate);
            transfer.setTimeLimit(1);
            transfer.setLowest(1000 * 100L);
            transfer.setState(0);
            transfer.setTenderCount(0);
            transfer.setTenderId(tender.getId());
            transfer.setStartOrder(borrowCollection.getOrder());
            transfer.setEndOrder(borrowCollection.getOrder());
            transfer.setIsAll(true);
            transfer.setBorrowCollectionIds(String.valueOf(borrowCollection.getId()));
            transfer.setTitle(borrow.getName() + "流转");
            transfer.setRepayAt(collectionAt);
            transferList.add(transfer);
            transferService.save(transfer);
            //生成债权购买记录
            TransferBuyLog transferBuyLog = new TransferBuyLog();
            transferBuyLog.setUserId(titularBorrowUserId);
            transferBuyLog.setType(0);
            transferBuyLog.setState(0);
            transferBuyLog.setAuto(false);
            transferBuyLog.setBuyMoney(principal + alreadyInterest);
            transferBuyLog.setValidMoney(principal + alreadyInterest);
            transferBuyLog.setPrincipal(principal);
            transferBuyLog.setCreatedAt(nowDate);
            transferBuyLog.setUpdatedAt(nowDate);
            transferBuyLog.setDel(false);
            transferBuyLog.setAutoOrder(0);
            transferBuyLog.setTransferId(transfer.getId());
            transferBuyLog.setAlreadyInterest(NumberHelper.toLong(alreadyInterest));
            transferBuyLog.setSource(0);
            transferBuyLogList.add(transferBuyLog);

            //改变tender状态
            tender.setTransferFlag(1);
            tender.setUpdatedAt(nowDate);
        });
        transferBuyLogService.save(transferBuyLogList);
    }

    /**
     * 新垫付流程
     *
     * @param borrow
     * @param borrowRepayment
     * @param batchAssetChange
     * @param lateInterest
     * @param lateDays
     * @param seqNo
     * @param groupSeqNo
     * @throws Exception
     */
    private ResponseEntity<VoBaseResp> newAdvanceProcess(Borrow borrow,
                                                         BorrowRepayment borrowRepayment,
                                                         BatchAssetChange batchAssetChange,
                                                         long lateInterest,
                                                         int lateDays,
                                                         String seqNo,
                                                         String groupSeqNo) throws Exception {
        log.info("垫付流程: 进入新的垫付流程");
        /* 查询投资列表 */
        Specification<Tender> specification = Specifications
                .<Tender>and()
                .eq("status", 1)
                .eq("borrowId", borrow.getId())
                .build();
        List<Tender> tenderList = tenderService.findList(specification);
        Preconditions.checkNotNull(tenderList, "投资人投标信息不存在!");
        /* 投资记录id集合 */
        List<Long> tenderIds = tenderList.stream().map(tender -> tender.getId()).collect(Collectors.toList());
        /* 查询未转让的回款记录 */
        Specification<BorrowCollection> bcs = Specifications
                .<BorrowCollection>and()
                .in("tenderId", tenderIds.toArray())
                .eq("status", 0)
                .eq("order", borrowRepayment.getOrder())
                .eq("transferFlag", 0)
                .build();
        List<BorrowCollection> borrowCollectionList = borrowCollectionService.findList(bcs);
        Preconditions.checkNotNull("投资回款记录不存在!");
        Map<Long/* tenderId */, BorrowCollection> borrowCollectionMaps = borrowCollectionList.stream().collect(Collectors.toMap(BorrowCollection::getTenderId, Function.identity()));
        Date nowDate = new Date();
        /* 名义借款人id */
        UserThirdAccount titularBorrowAccount = jixinHelper.getTitularBorrowAccount(borrow.getId());
        //垫付资产改变集合
        List<AdvanceAssetChange> advanceAssetChangeList = new ArrayList<>();
        //垫付记录 已经存在的债权转让记录
        Specification<Transfer> transferSpecification = Specifications
                .<Transfer>and()
                .in("tenderId", tenderIds.toArray())
                .eq("type", 2)
                .eq("state", 1)
                .build();
        List<Transfer> transferList = transferService.findList(transferSpecification);
        //垫付记录 已经存在购买债权转让记录
        List<TransferBuyLog> transferBuyLogList = new ArrayList<>();
        if (!CollectionUtils.isEmpty(transferList)) {
            List<Long> transferIds = transferList.stream().map(Transfer::getId).collect(Collectors.toList());
            Specification<TransferBuyLog> tbls = Specifications
                    .<TransferBuyLog>and()
                    .in("transferId", transferIds.toArray())
                    .in("state", 0, 1)/* 购买中、成功购买 */
                    .build();
            transferBuyLogList = transferBuyLogService.findList(tbls);
        }
        // 生成债权转让记录
        addTransferTenderByAdvance(borrow, transferList, transferBuyLogList, tenderList, borrowCollectionMaps, titularBorrowAccount.getUserId());
        Map<Long, Transfer> transferMaps = transferList.stream().collect(Collectors.toMap(Transfer::getTenderId, Function.identity()));
        Map<Long, TransferBuyLog> transferBuyLogMaps = transferBuyLogList.stream().collect(Collectors.toMap(TransferBuyLog::getTransferId, Function.identity()));
        //获取名义借款人垫付记录
        List<CreditInvest> creditInvestList = calculateAdvancePlan(borrow, titularBorrowAccount, transferMaps, transferBuyLogMaps, tenderList, borrowCollectionMaps, advanceAssetChangeList, lateDays, lateInterest);
        Preconditions.checkNotNull(creditInvestList, "存管垫付记录不存在!");
        // 生成还款记录
        doGenerateAssetChangeRecodeByAdvance(borrow, borrowRepayment, advanceAssetChangeList, batchAssetChange, titularBorrowAccount, seqNo, groupSeqNo);
        // 垫付金额 = sum(垫付本金 + 垫付利息)
        double txAmount = creditInvestList.stream().mapToDouble(w -> NumberHelper.toDouble(w.getTxAmount())).sum();
        // 批次号
        String batchNo = jixinHelper.getBatchNo();
        /* 冻结存管可用资金orderId */
        String freezeOrderId = JixinHelper.getOrderId(JixinHelper.BALANCE_FREEZE_PREFIX);
        try {
            BalanceFreezeReq balanceFreezeReq = new BalanceFreezeReq();
            balanceFreezeReq.setAccountId(titularBorrowAccount.getAccountId());
            balanceFreezeReq.setTxAmount(StringHelper.formatDouble(txAmount, false));
            balanceFreezeReq.setOrderId(freezeOrderId);
            balanceFreezeReq.setChannel(ChannelContant.HTML);
            BalanceFreezeResp balanceFreezeResp = jixinManager.send(JixinTxCodeEnum.BALANCE_FREEZE, balanceFreezeReq, BalanceFreezeResp.class);
            if ((ObjectUtils.isEmpty(balanceFreezeReq)) || (!JixinResultContants.SUCCESS.equalsIgnoreCase(balanceFreezeResp.getRetCode()))) {
                throw new Exception("即信批次名义借款人垫付冻结资金失败：" + balanceFreezeResp.getRetMsg());
            }

            // 垫付还款冻结
            long frozenMoney = new Double(txAmount * 100).longValue();
            AssetChange freezeAssetChange = new AssetChange();
            freezeAssetChange.setSourceId(borrowRepayment.getId());
            freezeAssetChange.setGroupSeqNo(groupSeqNo);
            freezeAssetChange.setSeqNo(seqNo);
            freezeAssetChange.setMoney(frozenMoney);
            freezeAssetChange.setUserId(titularBorrowAccount.getUserId());
            freezeAssetChange.setRemark(String.format("垫付还款,冻结资金%s元", StringHelper.formatDouble(frozenMoney / 100D, true)));
            freezeAssetChange.setSourceId(borrowRepayment.getId());
            freezeAssetChange.setType(AssetChangeTypeEnum.freeze);
            assetChangeProvider.commonAssetChange(freezeAssetChange);

            //请求保留参数
            Map<String, Object> acqResMap = new HashMap<>();
            acqResMap.put("repaymentId", borrowRepayment.getId());
            acqResMap.put("freezeOrderId", freezeOrderId);
            acqResMap.put("accountId", titularBorrowAccount.getAccountId());

            BatchCreditInvestReq request = new BatchCreditInvestReq();
            request.setChannel(ChannelContant.HTML);
            request.setBatchNo(batchNo);
            request.setTxAmount(StringHelper.formatDouble(txAmount, false));
            request.setTxCounts(StringHelper.toString(creditInvestList.size()));
            request.setNotifyURL(javaDomain + "/pub/repayment/v2/third/batch/advance/check");
            request.setRetNotifyURL(javaDomain + "/pub/repayment/v2/third/batch/advance/run");
            request.setAcqRes(GSON.toJson(acqResMap));
            request.setSubPacks(GSON.toJson(creditInvestList));
            BatchCreditEndResp response = jixinManager.send(JixinTxCodeEnum.BATCH_CREDIT_INVEST, request, BatchCreditEndResp.class);
            if ((ObjectUtils.isEmpty(response)) || (!JixinResultContants.BATCH_SUCCESS.equalsIgnoreCase(response.getReceived()))) {
                return ResponseEntity.badRequest().body(VoBaseResp.error(VoBaseResp.ERROR, "批次名义借款人垫付失败!"));
            }

            //记录日志
            ThirdBatchLog thirdBatchLog = new ThirdBatchLog();
            thirdBatchLog.setBatchNo(batchNo);
            thirdBatchLog.setCreateAt(nowDate);
            thirdBatchLog.setUpdateAt(nowDate);
            thirdBatchLog.setSourceId(borrowRepayment.getId());
            thirdBatchLog.setType(ThirdBatchLogContants.BATCH_BAIL_REPAY);
            thirdBatchLog.setRemark("批次名义借款人垫付");
            thirdBatchLog.setAcqRes(GSON.toJson(acqResMap));
            thirdBatchLogService.save(thirdBatchLog);
        } catch (Exception e) {
            // 申请即信还款解冻
            String unfreezeOrderId = JixinHelper.getOrderId(JixinHelper.BALANCE_UNFREEZE_PREFIX);
            BalanceUnfreezeReq balanceUnfreezeReq = new BalanceUnfreezeReq();
            balanceUnfreezeReq.setAccountId(titularBorrowAccount.getAccountId());
            balanceUnfreezeReq.setTxAmount(StringHelper.formatDouble(txAmount, false));
            balanceUnfreezeReq.setOrderId(unfreezeOrderId);
            balanceUnfreezeReq.setOrgOrderId(freezeOrderId);
            balanceUnfreezeReq.setChannel(ChannelContant.HTML);
            BalanceFreezeResp balanceFreezeResp = jixinManager.send(JixinTxCodeEnum.BALANCE_UN_FREEZE, balanceUnfreezeReq, BalanceFreezeResp.class);
            if ((ObjectUtils.isEmpty(balanceUnfreezeReq)) || (!JixinResultContants.SUCCESS.equalsIgnoreCase(balanceFreezeResp.getRetCode()))) {
                throw new Exception("名义借款人垫付解冻异常：" + balanceFreezeResp.getRetMsg());
            }
        }
        return ResponseEntity.ok(VoBaseResp.ok("批次名义借款人垫付成功!"));
    }

    /**
     * 生成名义借款人垫付记录
     *
     * @param borrow
     * @param titularBorrowAccount
     * @param tenderList
     * @param borrowCollectionMaps
     * @param advanceAssetChanges
     * @param lateDays
     * @param lateInterest
     * @return
     * @throws Exception
     */
    public List<CreditInvest> calculateAdvancePlan(Borrow borrow, UserThirdAccount titularBorrowAccount, Map<Long, Transfer> transferMaps, Map<Long, TransferBuyLog> transferBuyLogMaps, List<Tender> tenderList, Map<Long/* tenderId */, BorrowCollection> borrowCollectionMaps, List<AdvanceAssetChange> advanceAssetChanges, int lateDays, long lateInterest) throws Exception {

        /* 垫付记录集合 */
        List<CreditInvest> creditInvestList = new ArrayList<>();

        long txAmount = 0;/* 垫付金额 = 垫付本金 + 垫付利息 */
        long intAmount = 0;/* 交易利息 */
        long principal = 0;/* 还款本金 */
        for (Tender tender : tenderList) {
            Transfer transfer = transferMaps.get(tender.getId());
            TransferBuyLog transferBuyLog = transferBuyLogMaps.get(transfer.getId());

            //垫付资金变动
            AdvanceAssetChange advanceAssetChange = new AdvanceAssetChange();
            advanceAssetChanges.add(advanceAssetChange);
            //投标人银行存管账户
            UserThirdAccount tenderUserThirdAccount = userThirdAccountService.findByUserId(tender.getUserId());
            Preconditions.checkNotNull(tenderUserThirdAccount, "投资人存管账户未开户!");
            //当前投资的回款记录
            BorrowCollection borrowCollection = borrowCollectionMaps.get(tender.getId());

            //判断这笔回款是否已经在即信登记过批次垫付
            if (BooleanHelper.isTrue(transferBuyLog.getThirdTransferFlag())) {
                continue;
            }
            if (tender.getTransferFlag() == 1) {
                transferBiz.cancelTransferByTenderId(tender.getId()); // 标的转让中时, 需要取消出让信息
            }
            if (tender.getTransferFlag() == 2) {  // 已经转让的债权, 可以跳过还款
                continue;
            }

            intAmount = borrowCollection.getInterest();//还款利息
            principal = borrowCollection.getPrincipal(); //还款本金
            advanceAssetChange.setUserId(borrowCollection.getUserId());
            advanceAssetChange.setInterest(intAmount);
            advanceAssetChange.setPrincipal(principal);

            if ((lateDays > 0) && (lateInterest > 0)) {  //借款人逾期罚息
                int overdueFee = new Double(tender.getValidMoney() / new Double(borrow.getMoney()) * lateInterest / 2D).intValue();// 出借人收取50% 逾期管理费 ;
                advanceAssetChange.setOverdueFee(overdueFee);
                intAmount += overdueFee;
            }
            /* 垫付金额 */
            txAmount = principal + intAmount; //= 垫付本金 + 垫付利息
            String orderId = JixinHelper.getOrderId(JixinHelper.REPAY_BAIL_PREFIX);
            /* 存管垫付记录 */
            CreditInvest creditInvest = new CreditInvest();
            creditInvest.setAccountId(titularBorrowAccount.getAccountId());
            creditInvest.setOrderId(orderId);
            creditInvest.setTxAmount(StringHelper.formatDouble(txAmount, 100, false));
            creditInvest.setTxFee("0");
            creditInvest.setTsfAmount(StringHelper.formatDouble(principal, 100, false));
            creditInvest.setForAccountId(tenderUserThirdAccount.getAccountId());
            creditInvest.setOrgOrderId(tender.getThirdTenderOrderId());
            creditInvest.setOrgTxAmount(StringHelper.formatDouble(tender.getValidMoney(), 100, false));
            creditInvest.setProductId(borrow.getProductId());
            creditInvest.setContOrderId(tenderUserThirdAccount.getAutoTransferBondOrderId());
            creditInvestList.add(creditInvest);
            //更新回款记录
            transferBuyLog.setThirdTransferOrderId(orderId);
            transferBuyLog.setUpdatedAt(new Date());
            transferBuyLogService.save(transferBuyLog);

        }
        return creditInvestList;
    }

    /**
     * 生成还款记录
     *
     * @param borrow
     * @param borrowRepayment
     * @param advanceAsserChange
     * @param batchAssetChange
     * @param titularBorrowAccount
     * @param seqNo
     * @param groupSeqNo
     */
    private void doGenerateAssetChangeRecodeByAdvance(Borrow borrow, BorrowRepayment borrowRepayment, List<AdvanceAssetChange> advanceAsserChange, BatchAssetChange batchAssetChange, UserThirdAccount titularBorrowAccount, String seqNo, String groupSeqNo) throws ExecutionException {
        long batchAssetChangeId = batchAssetChange.getId();
        Date nowDate = new Date();

        for (AdvanceAssetChange advanceAssetChange : advanceAsserChange) {
            BatchAssetChangeItem batchAssetChangeItem = new BatchAssetChangeItem();   // 还款本金和利息
            batchAssetChangeItem.setBatchAssetChangeId(batchAssetChangeId);
            batchAssetChangeItem.setState(0);
            batchAssetChangeItem.setType(AssetChangeTypeEnum.receivedPayments.getLocalType());  // 投资人收到还款
            batchAssetChangeItem.setUserId(advanceAssetChange.getUserId());
            batchAssetChangeItem.setToUserId(titularBorrowAccount.getUserId());  // 垫付人
            batchAssetChangeItem.setMoney(advanceAssetChange.getPrincipal() + advanceAssetChange.getInterest());   // 本金加利息
            batchAssetChangeItem.setInterest(advanceAssetChange.getInterest());  // 利息
            batchAssetChangeItem.setRemark(String.format("收到客户对借款[%s]第%s期的还款", borrow.getName(), (borrowRepayment.getOrder() + 1)));
            batchAssetChangeItem.setSeqNo(seqNo);
            batchAssetChangeItem.setGroupSeqNo(groupSeqNo);
            batchAssetChangeItemService.save(batchAssetChangeItem);

            if (advanceAssetChange.getOverdueFee() > 0) {  // 用户收取逾期管理费
                batchAssetChangeItem = new BatchAssetChangeItem();
                batchAssetChangeItem.setBatchAssetChangeId(batchAssetChangeId);
                batchAssetChangeItem.setState(0);
                batchAssetChangeItem.setType(AssetChangeTypeEnum.receivedPaymentsPenalty.getLocalType());  // 收取用户逾期费
                batchAssetChangeItem.setUserId(advanceAssetChange.getUserId());
                batchAssetChangeItem.setToUserId(titularBorrowAccount.getUserId());
                batchAssetChangeItem.setMoney(advanceAssetChange.getOverdueFee());
                batchAssetChangeItem.setRemark(String.format("收取借款标的[%s]滞纳金%s元", borrow.getName(), StringHelper.formatDouble(advanceAssetChange.getOverdueFee() / 100D, false)));
                batchAssetChangeItem.setCreatedAt(nowDate);
                batchAssetChangeItem.setUpdatedAt(nowDate);
                batchAssetChangeItem.setSourceId(borrowRepayment.getId());
                batchAssetChangeItem.setSeqNo(seqNo);
                batchAssetChangeItem.setGroupSeqNo(groupSeqNo);
                batchAssetChangeItemService.save(batchAssetChangeItem);
            }

            //扣除投资人待收
            batchAssetChangeItem = new BatchAssetChangeItem();
            batchAssetChangeItem.setBatchAssetChangeId(batchAssetChangeId);
            batchAssetChangeItem.setState(0);
            batchAssetChangeItem.setType(AssetChangeTypeEnum.collectionSub.getLocalType());  //  扣除投资人待收
            batchAssetChangeItem.setUserId(advanceAssetChange.getUserId());
            batchAssetChangeItem.setMoney(advanceAssetChange.getInterest() + advanceAssetChange.getPrincipal());
            batchAssetChangeItem.setInterest(advanceAssetChange.getInterest());
            batchAssetChangeItem.setRemark(String.format("收到客户对[%s]借款的还款,扣除待收", borrow.getName()));
            batchAssetChangeItem.setCreatedAt(nowDate);
            batchAssetChangeItem.setUpdatedAt(nowDate);
            batchAssetChangeItem.setSourceId(borrowRepayment.getId());
            batchAssetChangeItem.setSeqNo(seqNo);
            batchAssetChangeItem.setGroupSeqNo(groupSeqNo);
            batchAssetChangeItemService.save(batchAssetChangeItem);
        }
    }

    /**
     * 新增垫付记录与更改还款记录
     *
     * @param bailAccountId
     * @param borrowRepayment
     * @param lateDays
     * @param lateInterest
     */
    private void addAdvanceLogAndChangeBorrowRepayment(long bailAccountId, BorrowRepayment borrowRepayment, int lateDays, long lateInterest) {
        //新增垫付记录
        AdvanceLog advanceLog = new AdvanceLog();
        advanceLog.setUserId(bailAccountId);
        advanceLog.setRepaymentId(borrowRepayment.getId());
        advanceLog.setAdvanceAtYes(new Date());
        advanceLog.setAdvanceMoneyYes((borrowRepayment.getRepayMoney() + lateInterest));
        advanceLogService.save(advanceLog);
        //更改付款记录
        borrowRepayment.setLateDays(lateDays);
        borrowRepayment.setLateInterest(lateInterest);
        borrowRepayment.setAdvanceAtYes(new Date());
        borrowRepayment.setAdvanceMoneyYes((borrowRepayment.getRepayMoney() + lateInterest));
        borrowRepaymentService.updateById(borrowRepayment);
    }

    /**
     * 生成名义借款人垫付批次资产变更记录
     *
     * @param batchAssetChangeId
     * @param bailAccountId
     * @param borrowRepayment
     * @param parentBorrow
     * @param lateInterest
     * @param seqNo
     * @param groupSeqNo
     */
    private void addBatchAssetChangeByAdvance(long batchAssetChangeId, long bailAccountId, BorrowRepayment borrowRepayment,
                                              Borrow parentBorrow, Long lateInterest, String seqNo, String groupSeqNo) {
        Date nowDate = new Date();
        // 名义借款人垫付还款
        BatchAssetChangeItem advanceBailAssetChangeItem = new BatchAssetChangeItem();
        advanceBailAssetChangeItem.setBatchAssetChangeId(batchAssetChangeId);
        advanceBailAssetChangeItem.setState(0);
        advanceBailAssetChangeItem.setType(AssetChangeTypeEnum.compensatoryRepayment.getLocalType());  // 名义借款人垫付还款
        advanceBailAssetChangeItem.setUserId(bailAccountId);
        advanceBailAssetChangeItem.setMoney(borrowRepayment.getRepayMoney());
        advanceBailAssetChangeItem.setRemark(String.format("对借款[%s]第%s期的垫付还款", BorrowHelper.getBorrowLink(parentBorrow.getId(), parentBorrow.getName()), (borrowRepayment.getOrder() + 1)));
        advanceBailAssetChangeItem.setCreatedAt(nowDate);
        advanceBailAssetChangeItem.setUpdatedAt(nowDate);
        advanceBailAssetChangeItem.setSourceId(borrowRepayment.getId());
        advanceBailAssetChangeItem.setSeqNo(seqNo);
        advanceBailAssetChangeItem.setGroupSeqNo(groupSeqNo);
        batchAssetChangeItemService.save(advanceBailAssetChangeItem);

        if (lateInterest > 0) {
            BatchAssetChangeItem overdueAssetChangeItem = new BatchAssetChangeItem();  // 滞纳金
            overdueAssetChangeItem.setBatchAssetChangeId(batchAssetChangeId);
            overdueAssetChangeItem.setState(0);
            overdueAssetChangeItem.setType(AssetChangeTypeEnum.compensatoryRepaymentOverdueFee.getLocalType());  // 名义借款人垫付还款
            overdueAssetChangeItem.setUserId(bailAccountId);
            overdueAssetChangeItem.setMoney(new Double(lateInterest.doubleValue() / 2D).longValue());
            overdueAssetChangeItem.setRemark(String.format("对借款[%s]第%s期的垫付滞纳金", BorrowHelper.getBorrowLink(parentBorrow.getId(), parentBorrow.getName()), (borrowRepayment.getOrder() + 1)));
            overdueAssetChangeItem.setCreatedAt(nowDate);
            overdueAssetChangeItem.setUpdatedAt(nowDate);
            overdueAssetChangeItem.setSourceId(borrowRepayment.getId());
            overdueAssetChangeItem.setSeqNo(seqNo);
            overdueAssetChangeItem.setGroupSeqNo(groupSeqNo);
            batchAssetChangeItemService.save(overdueAssetChangeItem);
        }

    }

    /**
     * 生成垫付还款主记录
     *
     * @param repaymentId
     * @param batchNo
     */
    private BatchAssetChange addBatchAssetChangeByAdvance(long repaymentId, String batchNo) {
        BatchAssetChange batchAssetChange = new BatchAssetChange();
        batchAssetChange.setSourceId(repaymentId);
        batchAssetChange.setState(0);
        batchAssetChange.setType(BatchAssetChangeContants.BATCH_BAIL_REPAY);/* 名义借款人垫付 */
        batchAssetChange.setCreatedAt(new Date());
        batchAssetChange.setUpdatedAt(new Date());
        batchAssetChange.setBatchNo(batchNo);
        batchAssetChangeService.save(batchAssetChange);
        return batchAssetChange;
    }

    /**
     * 新版垫付处理
     *
     * @param repaymentId
     * @return
     * @throws Exception
     */
    public ResponseEntity<VoBaseResp> newAdvanceDeal(long repaymentId, long batchNo) throws Exception {
        //1.查询判断还款记录是否存在
        BorrowRepayment borrowRepayment = borrowRepaymentService.findByIdLock(repaymentId);/* 当期还款记录 */
        Preconditions.checkNotNull(borrowRepayment, "还款记录不存在!");
        Borrow parentBorrow = borrowService.findById(borrowRepayment.getBorrowId());/* 还款记录对应的借款记录 */
        Preconditions.checkNotNull(parentBorrow, "借款记录不存在!");
        /* 还款对应的投标记录  包括债权转让在里面 */
        Specification<Tender> ts = Specifications
                .<Tender>and()
                .eq("status", 1)
                .eq("borrowId", parentBorrow.getId())
                .build();
        List<Tender> tenderList = tenderService.findList(ts);/* 还款对应的投标记录  包括债权转让在里面 */
        Preconditions.checkNotNull(tenderList, "立即还款: 投标记录为空!");
        Map<Long, Tender> tenderMaps = tenderList.stream().collect(Collectors.toMap(Tender::getId, Function.identity()));
        /* 投标记录id */
        Set<Long> tenderIds = tenderList.stream().map(tender -> tender.getId()).collect(Collectors.toSet());
        /* 查询未转让的投标记录回款记录 */
        Specification<BorrowCollection> bcs = Specifications
                .<BorrowCollection>and()
                .in("tenderId", tenderIds.toArray())
                .eq("status", 0)
                .eq("order", borrowRepayment.getOrder())
                .eq("transferFlag", 0)
                .build();
        List<BorrowCollection> borrowCollectionList = borrowCollectionService.findList(bcs);
        Preconditions.checkNotNull(borrowCollectionList, "立即还款: 回款记录为空!");
        /* 是否垫付 */
        boolean advance = !ObjectUtils.isEmpty(borrowRepayment.getAdvanceAtYes());
        //2.处理资金还款人、收款人资金变动
        batchAssetChangeHelper.batchAssetChangeAndCollection(repaymentId, batchNo, BatchAssetChangeContants.BATCH_LEND_REPAY);
        /* 逾期天数 */
        int lateDays = getLateDays(borrowRepayment);
        long lateInterest = new Double(calculateLateInterest(lateDays, borrowRepayment, parentBorrow) / 2D).longValue();/* 获取逾期利息的一半*/
        long titularBorrowUserId = assetChangeProvider.getTitularBorrowUserId();  // 平台担保人ID
        //3.新增垫付记录与更改还款状态
        addAdvanceLogAndChangeBorrowRepayment(titularBorrowUserId, borrowRepayment, lateDays, lateInterest);
        //3.5完成垫付债权转让操作
        transferTenderByAdvance(parentBorrow, tenderMaps, tenderIds);
        //4.结束垫付投资人债权
        endAdvanceCredit(parentBorrow);
        //5.发送投资人收到还款站内信
        sendCollectionNotices(borrowCollectionList, advance, parentBorrow);
        //6.发放积分
        giveInterest(borrowCollectionList, parentBorrow);
        //7.还款最后新增统计
        updateRepaymentStatistics(parentBorrow, borrowRepayment);
        //8.更新投资人缓存
        updateUserCacheByReceivedRepay(borrowCollectionList, parentBorrow);
        //9.项目回款短信通知
        smsNoticeByReceivedRepay(borrowCollectionList, parentBorrow, borrowRepayment);

        return ResponseEntity.ok(VoBaseResp.ok("垫付处理成功!"));
    }

    /**
     * 5完成垫付债权转让操作
     *
     * @param tenderMaps
     * @param tenderIds
     */
    public void transferTenderByAdvance(Borrow parentBorrow, Map<Long, Tender> tenderMaps, Set<Long> tenderIds) {
        /* 查询债权转让记录 */
        Specification<Transfer> ts = Specifications
                .<Transfer>and()
                .eq("userId", tenderIds.toArray())
                .eq("state", 1)
                .eq("type", 2)
                .build();
        List<Transfer> transferList = transferService.findList(ts);
        Preconditions.checkState(!CollectionUtils.isEmpty(transferList), "转让记录不存在!");
        /* 债权转让id */
        List<Long> transferIds = transferList.stream().map(Transfer::getId).collect(Collectors.toList());
        Specification<TransferBuyLog> tbls = Specifications
                .<TransferBuyLog>and()
                .in("transferId", transferIds.toArray())
                .build();
        /* 债权转让购买记录 */
        List<TransferBuyLog> transferBuyLogList = transferBuyLogService.findList(tbls);
        Preconditions.checkState(!CollectionUtils.isEmpty(transferBuyLogList), "购买债权转让记录不存在!");
        // 新增子级投标记录,更新老债权记录
        Date nowDate = new Date();
        transferList.stream().forEach(transfer -> {
            Tender parentTender = tenderMaps.get(transfer.getTenderId());
            List<Tender> childTenderList = transferBiz.addChildTender(nowDate, transfer, parentTender, transferBuyLogList);
            // 生成子级债权回款记录，标注老债权回款已经转出
            try {
                transferBiz.addChildTenderCollection(nowDate, transfer, parentBorrow, childTenderList);
            } catch (Exception e) {
                log.error("repaymentBizImpl updateTransferTenderByAdvance error", e);
            }
        });

    }

    private void endAdvanceCredit(Borrow parentBorrow) {
        //推送队列结束债权
        MqConfig mqConfig = new MqConfig();
        mqConfig.setQueue(MqQueueEnum.RABBITMQ_CREDIT);
        mqConfig.setTag(MqTagEnum.END_CREDIT_BY_ADVANCE);
        mqConfig.setSendTime(DateHelper.addMinutes(new Date(), 1));
        ImmutableMap<String, String> body = ImmutableMap
                .of(MqConfig.MSG_BORROW_ID, StringHelper.toString(parentBorrow.getId()), MqConfig.MSG_TIME, DateHelper.dateToString(new Date()));
        mqConfig.setMsg(body);
        try {
            log.info(String.format("repaymentBizImpl endThirdTenderAndChangeBorrowStatus send mq %s", GSON.toJson(body)));
            mqHelper.convertAndSend(mqConfig);
        } catch (Throwable e) {
            log.error("repaymentBizImpl endThirdTenderAndChangeBorrowStatus send mq exception", e);
        }
    }

    /**
     * 批次融资人还担保账户垫款
     *
     * @param voRepayReq
     */
    public ResponseEntity<VoBaseResp> thirdBatchRepayBail(VoRepayReq voRepayReq) throws Exception {
        Date nowDate = new Date();
        int lateInterest = 0;//逾期利息
        Double interestPercent = voRepayReq.getInterestPercent();
        Long repaymentId = voRepayReq.getRepaymentId();
        interestPercent = ObjectUtils.isEmpty(interestPercent) ? 1 : interestPercent;

        BorrowRepayment borrowRepayment = borrowRepaymentService.findByIdLock(repaymentId);
        Borrow borrow = borrowService.findById(borrowRepayment.getBorrowId());
        Long borrowId = borrow.getId();//借款ID

        UserThirdAccount borrowUserThirdAccount = userThirdAccountService.findByUserId(borrow.getUserId());

        // 逾期天数
        int lateDays = getLateDays(borrowRepayment);
        if (0 < lateDays) {  // 产生逾期
            long overPrincipal = borrowRepayment.getPrincipal();
            if (borrowRepayment.getOrder() < (borrow.getTotalOrder() - 1)) {
                Specification<BorrowRepayment> brs = Specifications.<BorrowRepayment>and()
                        .eq("status", 0)
                        .eq("borrowId", borrowId)
                        .build();
                List<BorrowRepayment> borrowRepaymentList = borrowRepaymentService.findList(brs);
                Preconditions.checkNotNull(borrowRepayment, "还款信息不存在");
                //剩余未还本金
                overPrincipal = borrowRepaymentList.stream().mapToLong(br -> br.getPrincipal()).sum();
            }
            lateInterest = (int) MathHelper.myRound(overPrincipal * 0.004 * lateDays, 2);
        }

        List<RepayBail> repayBails = null;
        if (!ObjectUtils.isEmpty(borrowRepayment.getAdvanceAtYes())) {
            repayBails = new ArrayList<>();
            receivedRepayBail(repayBails, borrow, borrowUserThirdAccount.getAccountId(), borrowRepayment.getOrder(), interestPercent, lateInterest);
        }

        if (CollectionUtils.isEmpty(repayBails)) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "垫付不存在"));
        }

        double txAmount = repayBails.stream().mapToDouble(rb -> NumberHelper.toDouble(rb.getTxAmount())).sum();

        String batchNo = jixinHelper.getBatchNo();

        //====================================================================
        //冻结担保人账户资金
        //====================================================================
        String orderId = JixinHelper.getOrderId(JixinHelper.BALANCE_FREEZE_PREFIX);
        BalanceFreezeReq balanceFreezeReq = new BalanceFreezeReq();
        balanceFreezeReq.setAccountId(borrow.getBailAccountId());
        balanceFreezeReq.setTxAmount(StringHelper.formatDouble(txAmount, false));
        balanceFreezeReq.setOrderId(orderId);
        balanceFreezeReq.setChannel(ChannelContant.HTML);
        BalanceFreezeResp balanceFreezeResp = jixinManager.send(JixinTxCodeEnum.BALANCE_FREEZE, balanceFreezeReq, BalanceFreezeResp.class);
        if ((ObjectUtils.isEmpty(balanceFreezeReq)) || (!JixinResultContants.SUCCESS.equalsIgnoreCase(balanceFreezeResp.getRetCode()))) {
            throw new Exception("即信批次还款冻结资金失败：" + balanceFreezeResp.getRetMsg());
        }


        BatchRepayBailReq request = new BatchRepayBailReq();
        request.setBatchNo(batchNo);
        request.setTxAmount(StringHelper.formatDouble(txAmount, false));
        request.setSubPacks(GSON.toJson(repayBails));
        request.setTxCounts(StringHelper.toString(repayBails.size()));
        request.setNotifyURL(javaDomain + "/pub/repayment/v2/third/batch/repaybail/check");
        request.setRetNotifyURL(javaDomain + "/pub/repayment/v2/third/batch/repaybail/run");
        request.setAcqRes(GSON.toJson(voRepayReq));
        request.setChannel(ChannelContant.HTML);
        BatchRepayBailResp response = jixinManager.send(JixinTxCodeEnum.BATCH_REPAY_BAIL, request, BatchRepayBailResp.class);
        if ((ObjectUtils.isEmpty(response)) || (!JixinResultContants.BATCH_SUCCESS.equalsIgnoreCase(response.getReceived()))) {
            return ResponseEntity.badRequest().body(VoBaseResp.error(VoBaseResp.ERROR, "批次融资人还担保账户垫款失败!"));
        }

        //记录日志
        ThirdBatchLog thirdBatchLog = new ThirdBatchLog();
        thirdBatchLog.setBatchNo(batchNo);
        thirdBatchLog.setCreateAt(nowDate);
        thirdBatchLog.setUpdateAt(nowDate);
        thirdBatchLog.setSourceId(repaymentId);
        thirdBatchLog.setType(ThirdBatchLogContants.BATCH_REPAY_BAIL);
        thirdBatchLog.setRemark("批次融资人还担保账户垫款");
        thirdBatchLog.setAcqRes(GSON.toJson(voRepayReq));
        thirdBatchLogService.save(thirdBatchLog);

        return ResponseEntity.ok(VoBaseResp.ok("批次融资人还担保账户垫款成功!"));
    }

    /**
     * 收到垫付还款
     *
     * @param borrow
     * @param order
     * @param interestPercent
     * @param lateInterest
     * @return
     * @throws Exception
     */
    private void receivedRepayBail(List<RepayBail> repayBails, Borrow borrow, String borrowUserThirdAccount, int order, double interestPercent, long lateInterest) throws Exception {
        do {
            //===================================还款校验==========================================
            if (ObjectUtils.isEmpty(borrow)) {
                break;
            }

            Long borrowId = borrow.getId();
            Specification<Tender> specification = Specifications
                    .<Tender>and()
                    .eq("status", 1)
                    .eq("borrowId", borrowId)
                    .build();

            List<Tender> tenderList = tenderService.findList(specification);
            if (CollectionUtils.isEmpty(tenderList)) {
                break;
            }

            List<Long> userIds = tenderList.stream().map(tender -> tender.getUserId()).collect(Collectors.toList());
            List<Long> tenderIds = tenderList.stream().map(tender -> tender.getId()).collect(Collectors.toList());

            Specification<UserCache> ucs = Specifications
                    .<UserCache>and()
                    .in("userId", userIds.toArray())
                    .build();

            List<UserCache> userCacheList = userCacheService.findList(ucs);
            if (CollectionUtils.isEmpty(userCacheList)) {
                break;
            }

            Specification<BorrowCollection> bcs = Specifications
                    .<BorrowCollection>and()
                    .in("tenderId", tenderIds.toArray())
                    .eq("status", 1)
                    .eq("order", order)
                    .build();

            List<BorrowCollection> borrowCollectionList = borrowCollectionService.findList(bcs);
            if (CollectionUtils.isEmpty(borrowCollectionList)) {
                break;
            }
            //==================================================================================
            RepayBail repayBail = null;
            long txAmount = 0;//融资人实际付出金额=交易金额+交易利息+还款手续费
            int intAmount = 0;//交易利息
            long principal = 0;
            int txFeeOut = 0;
            for (Tender tender : tenderList) {
                repayBail = new RepayBail();
                txAmount = 0;
                intAmount = 0;
                txFeeOut = 0;

                //当前借款的回款记录
                BorrowCollection borrowCollection = borrowCollectionList.stream()
                        .filter(bc -> StringHelper.toString(bc.getTenderId()).equals(StringHelper.toString(tender.getId())))
                        .collect(Collectors.toList()).get(0);

                if (tender.getTransferFlag() == 1) {//转让中
                    Specification<Borrow> bs = Specifications
                            .<Borrow>and()
                            .eq("tenderId", tender.getId())
                            .in("status", 0, 1)
                            .build();

                    List<Borrow> borrowList = borrowService.findList(bs);
                    if (CollectionUtils.isEmpty(borrowList)) {
                        continue;
                    }
                }

                if (tender.getTransferFlag() == 2) { //已转让
                    Specification<Borrow> bs = Specifications
                            .<Borrow>and()
                            .eq("tenderId", tender.getId())
                            .eq("status", 3)
                            .build();

                    List<Borrow> borrowList = borrowService.findList(bs);
                    if (CollectionUtils.isEmpty(borrowList)) {
                        continue;
                    }

                    Borrow tempBorrow = borrowList.get(0);
                    int tempOrder = order + tempBorrow.getTotalOrder() - borrow.getTotalOrder();
                    long tempLateInterest = tender.getValidMoney() / borrow.getMoney() * lateInterest;

                    //回调
                    receivedRepayBail(repayBails, tempBorrow, borrowUserThirdAccount, tempOrder, interestPercent, tempLateInterest);
                    continue;
                }

                intAmount = (int) (borrowCollection.getInterest() * interestPercent);
                principal = borrowCollection.getPrincipal();


                //借款人逾期罚息
                if (lateInterest > 0) {
                    txFeeOut += lateInterest;
                }

                txAmount = principal;

                String orderId = JixinHelper.getOrderId(JixinHelper.BAIL_REPAY_PREFIX);
                repayBail.setOrderId(orderId);
                repayBail.setAccountId(borrowUserThirdAccount);
                repayBail.setTxAmount(StringHelper.formatDouble(txAmount, 100, false));
                repayBail.setIntAmount(StringHelper.formatDouble(intAmount, 100, false));
                repayBail.setForAccountId(borrow.getBailAccountId());
                repayBail.setTxFeeOut(StringHelper.formatDouble(txFeeOut, 100, false));
                /*repayBail.setOrgOrderId(borrowCollection.getTAdvanceOrderId());
                repayBail.setAuthCode(borrowCollection.getTAdvanceAuthCode());*/
                repayBails.add(repayBail);
                /*borrowCollection.setTAdvanceOrderId(orderId);*/
                borrowCollectionService.updateById(borrowCollection);
            }
        } while (false);
    }
}
