package com.gofobao.framework.tender.biz.impl;

import com.github.wenhao.jpa.Specifications;
import com.gofobao.framework.api.contants.ChannelContant;
import com.gofobao.framework.api.contants.JixinResultContants;
import com.gofobao.framework.api.helper.JixinManager;
import com.gofobao.framework.api.helper.JixinTxCodeEnum;
import com.gofobao.framework.api.model.balance_query.BalanceQueryRequest;
import com.gofobao.framework.api.model.balance_query.BalanceQueryResponse;
import com.gofobao.framework.asset.entity.Asset;
import com.gofobao.framework.asset.service.AssetService;
import com.gofobao.framework.borrow.entity.Borrow;
import com.gofobao.framework.borrow.service.BorrowService;
import com.gofobao.framework.collection.entity.BorrowCollection;
import com.gofobao.framework.collection.service.BorrowCollectionService;
import com.gofobao.framework.common.capital.CapitalChangeEntity;
import com.gofobao.framework.common.capital.CapitalChangeEnum;
import com.gofobao.framework.common.constans.TypeTokenContants;
import com.gofobao.framework.common.rabbitmq.MqConfig;
import com.gofobao.framework.common.rabbitmq.MqHelper;
import com.gofobao.framework.common.rabbitmq.MqQueueEnum;
import com.gofobao.framework.common.rabbitmq.MqTagEnum;
import com.gofobao.framework.core.vo.VoBaseResp;
import com.gofobao.framework.helper.*;
import com.gofobao.framework.helper.project.CapitalChangeHelper;
import com.gofobao.framework.helper.project.SecurityHelper;
import com.gofobao.framework.member.entity.UserThirdAccount;
import com.gofobao.framework.member.service.UserThirdAccountService;
import com.gofobao.framework.tender.biz.TransferBiz;
import com.gofobao.framework.tender.contants.BorrowContants;
import com.gofobao.framework.tender.entity.Tender;
import com.gofobao.framework.tender.entity.Transfer;
import com.gofobao.framework.tender.entity.TransferBuyLog;
import com.gofobao.framework.tender.service.TenderService;
import com.gofobao.framework.tender.service.TransferBuyLogService;
import com.gofobao.framework.tender.service.TransferService;
import com.gofobao.framework.tender.vo.request.VoBuyTransfer;
import com.gofobao.framework.tender.vo.request.VoPcFirstVerityTransfer;
import com.gofobao.framework.tender.vo.request.VoTransferReq;
import com.gofobao.framework.tender.vo.request.VoTransferTenderReq;
import com.gofobao.framework.tender.vo.response.*;
import com.gofobao.framework.tender.vo.response.web.TransferBuy;
import com.gofobao.framework.tender.vo.response.web.VoViewTransferBuyWarpRes;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Created by admin on 2017/6/12.
 */
@Slf4j
@Service
public class TransferBizImpl implements TransferBiz {

    @Autowired
    private TransferService transferService;
    @Autowired
    private TenderService tenderService;
    @Autowired
    private BorrowService borrowService;
    @Autowired
    private BorrowCollectionService borrowCollectionService;
    @Autowired
    private UserThirdAccountService userThirdAccountService;
    @Autowired
    private TransferBuyLogService transferBuyLogService;
    @Autowired
    private AssetService assetService;
    @Autowired
    private JixinManager jixinManager;
    @Autowired
    private CapitalChangeHelper capitalChangeHelper;
    @Autowired
    private MqHelper mqHelper;

    final Gson GSON = new GsonBuilder().create();

    public static final String MSG = "msg";
    public static final String LEFT_MONEY = "leftMoney";

    /**
     * 债权转让初审
     *
     * @param voPcFirstVerityTransfer
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<VoBaseResp> firstVerifyTransfer(VoPcFirstVerityTransfer voPcFirstVerityTransfer) throws Exception {
        String paramStr = voPcFirstVerityTransfer.getParamStr();
        if (!SecurityHelper.checkSign(voPcFirstVerityTransfer.getSign(), paramStr)) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "债权转让初审 签名验证不通过!"));
        }

        Map<String, String> paramMap = new Gson().fromJson(paramStr, TypeTokenContants.MAP_ALL_STRING_TOKEN);
        Long transferId = NumberHelper.toLong(paramMap.get("transferId"));
        if (doFirstVerifyTransfer(transferId)) {
            return ResponseEntity.ok(VoBaseResp.ok("债权转让初审初审成功!"));
        } else {
            return ResponseEntity.
                    badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "债权转让初审初审失败,transferId：" + transferId));
        }
    }

    /**
     * 去债权转让初审
     *
     * @param transferId
     * @throws Exception
     */
    private boolean doFirstVerifyTransfer(long transferId) throws Exception {
        //更新债权转让状态
        Date nowDate = DateHelper.subSeconds(new Date(), 10);
        Transfer transfer = transferService.findById(transferId);
        transfer.setVerifyAt(nowDate);
        transfer.setState(1);
        Date releaseAt = transfer.getReleaseAt();
        transfer.setReleaseAt(ObjectUtils.isEmpty(releaseAt) ? nowDate : releaseAt);
        transferService.save(transfer);

        // 自动购买债权转让前提:
        // 1.标的年化率为 800 以上
        int apr = transfer.getApr();
        if (apr > 800) {
            transfer.setLock(true);
            transferService.save(transfer);  // 锁住债权转让,禁止手动购买

            //触发自动投标队列
            MqConfig mqConfig = new MqConfig();
            mqConfig.setQueue(MqQueueEnum.RABBITMQ_TRANSFER);
            mqConfig.setTag(MqTagEnum.AUTO_TRANSFER);
            mqConfig.setSendTime(releaseAt);
            ImmutableMap<String, String> body = ImmutableMap
                    .of(MqConfig.MSG_TRANSFER_ID, StringHelper.toString(transferId), MqConfig.MSG_TIME, DateHelper.dateToString(new Date()));
            mqConfig.setMsg(body);
            try {
                log.info(String.format("transferBizImpl doFirstVerifyTransfer send mq %s", GSON.toJson(body)));
                mqHelper.convertAndSend(mqConfig);
                return true;
            } catch (Throwable e) {
                log.error("transferBizImpl doFirstVerifyTransfer send mq exception", e);
                return false;
            }
        }
        return true;
    }

    /**
     * 购买债权转让
     * 1.判断投资人是否存管开户、并且签约
     * 2.判断债权转让剩余金额是否大于等于购买金额
     * 3.判断账户可用金额是否大于购入金额
     * 4.生成购买债权记录
     */
    public ResponseEntity<VoBaseResp> buyTransfer(VoBuyTransfer voBuyTransfer) throws Exception {
        String msg = null;
        long userId = voBuyTransfer.getUserId();/*购买人id*/
        long transferId = voBuyTransfer.getTransferId();/*债权转让记录id*/
        long buyMoney = voBuyTransfer.getBuyMoney().longValue();/*购买债权转让金额*/
        boolean auto = voBuyTransfer.getAuto();/* 是否是自动购买债权转让 */
        int autoOrder = voBuyTransfer.getAutoOrder();/* 自动投标order编号 */

        UserThirdAccount buyUserThirdAccount = userThirdAccountService.findByUserId(userId);/*购买人存管信息*/
        ThirdAccountHelper.allConditionCheck(buyUserThirdAccount);
        Transfer transfer = transferService.findByIdLock(transferId);/*债权转让记录*/
        Preconditions.checkNotNull(transfer, "债权转让记录不存在!");
        Asset asset = assetService.findByUserIdLock(userId);/* 购买人资产记录 */
        Preconditions.checkNotNull(asset, "购买人资产记录不存在!");

        //验证债权转让
        ImmutableMap<String, Object> verifyTransferMap = verifyTransfer(buyMoney, transfer);
        long leftMoney = NumberHelper.toLong(verifyTransferMap.get(LEFT_MONEY));
        msg = StringHelper.toString(verifyTransferMap.get(MSG));
        if (!StringUtils.isEmpty(msg)) {
            return ResponseEntity.badRequest().body(VoBaseResp.error(VoBaseResp.ERROR, msg));
        }

        long validMoney = (long) MathHelper.min(leftMoney, buyMoney);/* 可购债权金额  */
        long alreadyInterest = validMoney / transfer.getPrincipal() * transfer.getAlreadyInterest();/* 应付给债权转让人的当期应计利息 */

        //验证购买人账户
        ImmutableMap<String, Object> verifyBuyTransferUserMap = verifyBuyTransferUser(buyUserThirdAccount, asset, validMoney);
        msg = StringHelper.toString(verifyBuyTransferUserMap.get(MSG));
        if (!StringUtils.isEmpty(msg)) {
            return ResponseEntity.badRequest().body(VoBaseResp.error(VoBaseResp.ERROR, msg));
        }

        //生成购买债权记录
        TransferBuyLog transferBuyLog = saveTransferAndTransferLog(userId, transferId, buyMoney, transfer, validMoney, alreadyInterest, auto, autoOrder);

        //更新购买人账户金额
        updateAssetByBuyUser(transferBuyLog, transfer);

        //判断是否满标，满标触发债权转让复审
        MqConfig mqConfig = new MqConfig();
        mqConfig.setQueue(MqQueueEnum.RABBITMQ_TRANSFER);
        mqConfig.setTag(MqTagEnum.AGAIN_VERIFY_TRANSFER);
        ImmutableMap<String, String> body = ImmutableMap
                .of(MqConfig.MSG_TRANSFER_ID, StringHelper.toString(transferId), MqConfig.MSG_TIME, DateHelper.dateToString(new Date()));
        mqConfig.setMsg(body);
        try {
            log.info(String.format("transferBizImpl buyTransfer send mq %s", GSON.toJson(body)));
            mqHelper.convertAndSend(mqConfig);
        } catch (Throwable e) {
            log.error("transferBizImpl buyTransfer send mq exception", e);
        }

        return ResponseEntity.ok(VoBaseResp.ok("购买成功!"));
    }

    /**
     * 保存债权转让与购买债权转让记录
     *
     * @param userId
     * @param transferId
     * @param buyMoney
     * @param transfer
     * @param validMoney
     * @param alreadyInterest
     */
    private TransferBuyLog saveTransferAndTransferLog(long userId, long transferId, long buyMoney, Transfer transfer, long validMoney, long alreadyInterest, boolean auto, int autoOrder) {
        //新增购买债权记录
        TransferBuyLog transferBuyLog = new TransferBuyLog();
        transferBuyLog.setUserId(userId);
        transferBuyLog.setState(0);
        transferBuyLog.setAuto(auto);
        transferBuyLog.setBuyMoney(buyMoney);
        transferBuyLog.setValidMoney(validMoney);
        transferBuyLog.setCreatedAt(new Date());
        transferBuyLog.setUpdatedAt(new Date());
        transferBuyLog.setDel(false);
        transferBuyLog.setAutoOrder(autoOrder);
        transferBuyLog.setTransferId(transferId);
        transferBuyLog.setAlreadyInterest(NumberHelper.toLong(alreadyInterest));
        transferBuyLog.setSource(0);
        transferBuyLogService.save(transferBuyLog);

        //更新债权抓让信息
        transfer.setTenderCount(NumberHelper.toInt(transfer.getTenderCount()) + 1);
        transfer.setPrincipalYes(transfer.getPrincipalYes() + validMoney);
        transfer.setUpdatedAt(new Date());
        transferService.save(transfer);
        return transferBuyLog;
    }

    /**
     * 更新购买人资产
     *
     * @param transferBuyLog
     * @param transfer
     */
    private void updateAssetByBuyUser(TransferBuyLog transferBuyLog, Transfer transfer) throws Exception {
        CapitalChangeEntity entity = new CapitalChangeEntity();
        entity.setType(CapitalChangeEnum.Frozen);
        entity.setUserId(transferBuyLog.getUserId());
        entity.setToUserId(transfer.getUserId());
        entity.setMoney(transferBuyLog.getValidMoney());
        entity.setRemark("购买债权转让冻结资金");
        capitalChangeHelper.capitalChange(entity);
    }

    /**
     * 校验购买人账户
     *
     * @param buyUserThirdAccount
     * @param asset
     * @param validMoney
     * @return
     */
    private ImmutableMap<String, Object> verifyBuyTransferUser(UserThirdAccount buyUserThirdAccount, Asset asset, double validMoney) {
        String msg = "";
        if (validMoney > asset.getUseMoney()) {
            msg = "账户余额不足，请先充值或同步资金!";
        }

        // 查询存管系统资金
        BalanceQueryRequest balanceQueryRequest = new BalanceQueryRequest();
        balanceQueryRequest.setChannel(ChannelContant.HTML);
        balanceQueryRequest.setAccountId(buyUserThirdAccount.getAccountId());
        BalanceQueryResponse balanceQueryResponse = jixinManager.send(JixinTxCodeEnum.BALANCE_QUERY, balanceQueryRequest, BalanceQueryResponse.class);
        if ((ObjectUtils.isEmpty(balanceQueryResponse)) || !balanceQueryResponse.getRetCode().equals(JixinResultContants.SUCCESS)) {
            msg = "当前网络不稳定,请稍后重试!";
        }

        double availBal = NumberHelper.toDouble(balanceQueryResponse.getAvailBal()) * 100.0;// 可用余额  账面余额-可用余额=冻结金额
        if (availBal < validMoney) {
            msg = "资金账户未同步，请先在个人中心进行资金同步操作!";
        }
        return ImmutableMap.of(MSG, msg);
    }

    /**
     * 验证债权转让
     *
     * @param buyMoney
     * @param transfer
     * @return
     */
    private ImmutableMap<String, Object> verifyTransfer(double buyMoney, Transfer transfer) {
        String msg = "";
        if (transfer.getState() != 1) {
            msg = "您看到的债权转让消失啦!";
        }

        if (transfer.getPrincipal() == transfer.getPrincipalYes()) {
            msg = "债权转出金额已购满!";
        }

        long leftMoney = transfer.getPrincipal() - transfer.getPrincipalYes();/*债权转让剩余可购买金额*/
        double mayBuyMoney = MathHelper.min(leftMoney, transfer.getLower());//获取剩余可购买金额
        if (buyMoney < mayBuyMoney) {
            msg = "购买金额小于最小购买金额";
        }
        ImmutableMap<String, Object> immutableMap = ImmutableMap.of(
                MSG, msg,
                LEFT_MONEY, leftMoney
        );

        return immutableMap;
    }


    /**
     * 新版债权转让
     *
     * @param voTransferTenderReq
     * @return
     * @throws Exception
     */
    public ResponseEntity<VoBaseResp> newTransferTender(VoTransferTenderReq voTransferTenderReq) throws Exception {
        long tenderId = voTransferTenderReq.getTenderId();/* 转让债权id */
        long userId = voTransferTenderReq.getUserId();/* 转让人id */

        Tender tender = tenderService.findById(tenderId);
        Preconditions.checkNotNull(tender, "立即转让: 查询用户投标记录为空!");
        Borrow borrow = borrowService.findByIdLock(tender.getBorrowId());
        Preconditions.checkNotNull(borrow, "立即转让: 查询用户投标标的信息为空!");

        // 前期债权转让检测
        ResponseEntity<VoBaseResp> transferConditionCheckResponse = transferConditionCheck(tender, borrow);
        if (!transferConditionCheckResponse.getStatusCode().equals(HttpStatus.OK)) {
            return transferConditionCheckResponse;
        }

        // 计算（未还款）债权本金之和
        // 判断本金必须大于 1000元
        Specification<BorrowCollection> bcs = Specifications
                .<BorrowCollection>and()
                .eq("transferFlag", 0)
                .eq("status", 0)
                .eq("tenderId", tenderId)
                .build();

        List<BorrowCollection> borrowCollectionList = borrowCollectionService.findList(bcs, new Sort(Sort.Direction.ASC, "order"));
        Preconditions.checkNotNull(borrowCollectionList, "立即转让: 查询转让用户还款计划为空");
        int waitTimeLimit = borrowCollectionList.size();  // 等待回款期数
        long leftCapital = borrowCollectionList.stream().mapToLong(borrowCollection -> borrowCollection.getPrincipal()).sum(); // 待回款本金
        if (leftCapital < (1000 * 100)) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "可转本金必须大于1000元才能转让"));
        }

        //保存债权转让记录
        saveTransfer(tenderId, userId, tender, borrow, waitTimeLimit, leftCapital, borrowCollectionList.get(0));

        return ResponseEntity.ok(VoBaseResp.ok("购买成功!"));
    }

    /**
     * 保存债权转让记录
     *
     * @param tenderId
     * @param userId
     * @param tender
     * @param borrow
     * @param waitTimeLimit
     * @param leftCapital
     * @param firstBorrowCollection
     */
    private void saveTransfer(long tenderId, long userId, Tender tender, Borrow borrow, int waitTimeLimit, long leftCapital, BorrowCollection firstBorrowCollection) {
        //计算当期应计利息
        int interest = firstBorrowCollection.getInterest();/* 当期理论应计利息 */
        Date startAt = DateHelper.beginOfDate(firstBorrowCollection.getStartAt());//理论开始计息时间
        Date collectionAt = DateHelper.beginOfDate(firstBorrowCollection.getCollectionAt());//理论结束还款时间
        Date startAtYes = DateHelper.beginOfDate(firstBorrowCollection.getStartAtYes());//实际开始计息时间
        Date endAt = DateHelper.beginOfDate(new Date());//结束计息时间

        /* 当期应计利息 */
        long alreadyInterest = Math.round(interest *
                Math.max(DateHelper.diffInDays(endAt, startAtYes, false), 0) /
                DateHelper.diffInDays(collectionAt, startAt, false));

        //新增债权转让记录
        Date nowDate = new Date();
        Transfer transfer = new Transfer();
        transfer.setUpdatedAt(nowDate);
        transfer.setUserId(userId);
        transfer.setPrincipalYes(0l);
        transfer.setDel(false);
        transfer.setBorrowId(borrow.getId());
        transfer.setPrincipal(leftCapital);
        transfer.setAlreadyInterest(alreadyInterest);
        transfer.setApr(borrow.getApr());
        transfer.setCreatedAt(nowDate);
        transfer.setTimeLimit(waitTimeLimit);
        transfer.setLower(1000 * 100L);
        transfer.setState(0);
        transfer.setTenderCount(0);
        transfer.setTenderId(tenderId);
        transfer.setTitle(borrow.getName() + "转");
        transferService.save(transfer);

        //更新投资记录
        tender.setTransferFlag(1);
        tender.setUpdatedAt(nowDate);
        tenderService.updateById(tender);
    }

    /**
     * 转让中
     *
     * @param voTransferReq
     * @return
     */
    @Override
    public ResponseEntity<VoViewTransferOfWarpRes> tranferOfList(VoTransferReq voTransferReq) {
        try {
            Map<String, Object> resultMaps = transferService.transferOfList(voTransferReq);
            List<TransferOf> transferOfs = (List<TransferOf>) resultMaps.get("transferOfList");
            Integer totalCount = Integer.valueOf(resultMaps.get("totalCount").toString());
            VoViewTransferOfWarpRes voViewTransferOfWarpRes = VoBaseResp.ok("查询成功", VoViewTransferOfWarpRes.class);
            voViewTransferOfWarpRes.setTransferOfs(transferOfs);
            voViewTransferOfWarpRes.setTotalCount(totalCount);
            return ResponseEntity.ok(voViewTransferOfWarpRes);
        } catch (Throwable e) {
            log.info("TransferBizImpl tranferOfList query fail%S", e);
            return ResponseEntity.badRequest()
                    .body(VoBaseResp.error(
                            VoBaseResp.ERROR,
                            "查询失败",
                            VoViewTransferOfWarpRes.class));
        }
    }

    /**
     * 已转让
     *
     * @param voTransferReq
     * @return
     */
    @Override
    public ResponseEntity<VoViewTransferedWarpRes> transferedlist(VoTransferReq voTransferReq) {
        try {
            Map<String, Object> resultMaps = transferService.transferedList(voTransferReq);
            List<Transfered> transfereds = (List<Transfered>) resultMaps.get("transferedList");
            Integer totalCount = Integer.valueOf(resultMaps.get("totalCount").toString());
            VoViewTransferedWarpRes voViewTransferOfWarpRes = VoBaseResp.ok("查询成功", VoViewTransferedWarpRes.class);
            voViewTransferOfWarpRes.setTransferedList(transfereds);
            voViewTransferOfWarpRes.setTotalCount(totalCount);
            return ResponseEntity.ok(voViewTransferOfWarpRes);
        } catch (Throwable e) {
            log.info("TransferBizImpl transferedlist query fail%S", e);
            return ResponseEntity.badRequest()
                    .body(VoBaseResp.error(
                            VoBaseResp.ERROR,
                            "查询失败",
                            VoViewTransferedWarpRes.class));
        }
    }

    /**
     * 可转让
     *
     * @param voTransferReq
     * @return
     */
    @Override
    public ResponseEntity<VoViewTransferMayWarpRes> transferMayList(VoTransferReq voTransferReq) {
        try {
            Map<String, Object> resultMaps = transferService.transferMayList(voTransferReq);
            List<TransferMay> transferOfs = (List<TransferMay>) resultMaps.get("transferMayList");
            Integer totalCount = Integer.valueOf(resultMaps.get("totalCount").toString());
            VoViewTransferMayWarpRes voViewTransferOfWarpRes = VoBaseResp.ok("查询成功", VoViewTransferMayWarpRes.class);
            voViewTransferOfWarpRes.setMayList(transferOfs);
            voViewTransferOfWarpRes.setTotalCount(totalCount);
            return ResponseEntity.ok(voViewTransferOfWarpRes);
        } catch (Throwable e) {
            log.info("TransferBizImpl transferMayList query fail%S", e);
            return ResponseEntity.badRequest()
                    .body(VoBaseResp.error(
                            VoBaseResp.ERROR,
                            "查询失败",
                            VoViewTransferMayWarpRes.class));
        }
    }

    /**
     * 已购买
     *
     * @param voTransferReq
     * @return
     */
    @Override
    public ResponseEntity<VoViewTransferBuyWarpRes> tranferBuyList(VoTransferReq voTransferReq) {
        try {
            Map<String, Object> resultMaps = transferService.transferBuyList(voTransferReq);
            List<TransferBuy> transferOfs = (List<TransferBuy>) resultMaps.get("transferBuys");
            Integer totalCount = Integer.valueOf(resultMaps.get("totalCount").toString());
            VoViewTransferBuyWarpRes warpRes = VoBaseResp.ok("查询成功", VoViewTransferBuyWarpRes.class);
            warpRes.setTransferBuys(transferOfs);
            warpRes.setTotalCount(totalCount);
            return ResponseEntity.ok(warpRes);
        } catch (Exception e) {
            log.info("TransferBizImpl transferMayList query fail%S", e);
            return ResponseEntity.badRequest()
                    .body(VoBaseResp.error(
                            VoBaseResp.ERROR,
                            "查询失败",
                            VoViewTransferBuyWarpRes.class));
        }
    }

    /**
     * 债权转让
     *
     * @param voTransferTenderReq
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<VoBaseResp> transferTender(VoTransferTenderReq voTransferTenderReq) {
        Date nowDate = new Date();
        Long userId = voTransferTenderReq.getUserId();
        Long tenderId = voTransferTenderReq.getTenderId();

        Tender tender = tenderService.findById(tenderId);
        Preconditions.checkNotNull(tender, "立即转让: 查询用户投标记录为空!");
        Borrow borrow = borrowService.findByIdLock(tender.getBorrowId());
        Preconditions.checkNotNull(borrow, "立即转让: 查询用户投标标的信息为空!");

        // 前期债权转让检测
        ResponseEntity<VoBaseResp> transferConditionCheck = transferConditionCheck(tender, borrow);
        if (!transferConditionCheck.getStatusCode().equals(HttpStatus.OK)) {
            return transferConditionCheck;
        }

        // 计算债权本金之和
        // 判断本金必须大于 1000元
        Specification<BorrowCollection> bcs = Specifications
                .<BorrowCollection>and()
                .eq("transferFlag", 0)
                .eq("status", 0)
                .eq("tenderId", tenderId)
                .build();

        List<BorrowCollection> borrowCollectionList = borrowCollectionService.findList(bcs, new Sort(Sort.Direction.ASC, "order"));
        Preconditions.checkNotNull(borrowCollectionList, "立即转让: 查询转让用户还款计划为空");
        int waitTimeLimit = borrowCollectionList.size();  // 等待回款期数
        int cantrCapital = borrowCollectionList.stream().mapToInt(borrowCollection -> borrowCollection.getPrincipal()).sum(); // 待汇款本金
        if (cantrCapital < (1000 * 100)) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "可转本金必须大于1000元才能转让"));
        }

        saveTranferBorrow(nowDate, userId, tender, borrow, waitTimeLimit, cantrCapital);
        return ResponseEntity.ok(VoBaseResp.ok("操作成功"));
    }

    /**
     * 保存债权,并且更改投标记录为转让中
     *
     * @param nowDate
     * @param userId
     * @param tender
     * @param borrow
     * @param waitTimeLimit
     * @param cantrCapital
     */
    private void saveTranferBorrow(Date nowDate, Long userId, Tender tender, Borrow borrow, int waitTimeLimit, int cantrCapital) {
        // 转让借款
        Borrow tranferBorrow = new Borrow();
        tranferBorrow.setType(3); // 3 转让标
        tranferBorrow.setUse(borrow.getUse());
        tranferBorrow.setIsLock(false);
        tranferBorrow.setRepayFashion(borrow.getRepayFashion());
        tranferBorrow.setTimeLimit(borrow.getRepayFashion() == 1 ? borrow.getTimeLimit() : waitTimeLimit);
        tranferBorrow.setMoney(cantrCapital);
        tranferBorrow.setApr(borrow.getApr());
        tranferBorrow.setLowest(1000 * 100);
        tranferBorrow.setValidDay(1);
        tranferBorrow.setName(borrow.getName());
        tranferBorrow.setDescription(borrow.getDescription());
        tranferBorrow.setIsVouch(borrow.getIsVouch());
        tranferBorrow.setIsMortgage(borrow.getIsMortgage());
        tranferBorrow.setIsConversion(borrow.getIsConversion());
        tranferBorrow.setUserId(userId);
        tranferBorrow.setTenderId(tender.getId());
        tranferBorrow.setCreatedAt(nowDate);
        tranferBorrow.setUpdatedAt(nowDate);
        tranferBorrow.setMost(0);
        tranferBorrow.setMostAuto(0);
        tranferBorrow.setAwardType(0);
        tranferBorrow.setAward(0);
        tranferBorrow.setPassword("");
        tranferBorrow.setMoneyYes(0);
        tranferBorrow.setTenderCount(0);
        borrowService.insert(tranferBorrow);//插入转让标

        tender.setTransferFlag(1);
        tender.setUpdatedAt(nowDate);
        tenderService.updateById(tender);
    }

    /**
     * 债权装让前期检测
     * 1. 当期债权是否已经发生转让行为
     * 2. 当前待还是否为官方标的
     * 3. 保证只能同时发生一个债权转让
     *
     * @param tender
     * @param borrow
     * @return
     */
    private ResponseEntity<VoBaseResp> transferConditionCheck(Tender tender, Borrow borrow) {
        if ((tender.getTransferFlag() != 0) || (borrow.isTransfer())) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "操作失败: 你已经出让债权了!"));
        }

        if ((tender.getStatus() != 1)) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "当前系统出现异常, 麻烦通知平台客服人员!"));
        }


        if ((borrow.getType() != 0)) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "投资非投资官方标的是不可债权转让!"));
        }

        if ((borrow.getStatus() != 3)) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "当前债权不符合转让规则!"));
        }


        Specification<Borrow> borrowSpecification = Specifications
                .<Borrow>and()
                .eq("userId", tender.getUserId())
                .in("status", 0, 1)
                .build();

        long tranferingNum = borrowService.count(borrowSpecification);
        if (tranferingNum > 0) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "您已经有一个进行中的借款标"));
        }

        UserThirdAccount userThirdAccount = userThirdAccountService.findByUserId(tender.getUserId());
        if (ObjectUtils.isEmpty(userThirdAccount)) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR_OPEN_ACCOUNT, "你没有开通银行存管，请先开通银行存管！"));
        }

        Integer passwordState = userThirdAccount.getPasswordState();
        if (passwordState == 0) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR_INIT_BANK_PASSWORD, "请先初始化江西银行存管账户交易密码！"));
        }

        if (userThirdAccount.getAutoTransferState() != 1) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR_CREDIT, "请先签订自动债权转让协议！", VoAutoTenderInfo.class));
        }


        if (userThirdAccount.getAutoTenderState() != 1) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR_CREDIT, "请先签订自动投标协议！", VoAutoTenderInfo.class));
        }
        return ResponseEntity.ok(VoBaseResp.ok("检测成功!"));
    }

    /**
     * 获取立即转让详情
     *
     * @param tenderId 投标记录Id
     * @return
     */
    public ResponseEntity<VoGoTenderInfo> goTenderInfo(Long tenderId, Long userId) {
        Tender tender = tenderService.findById(tenderId);
        Preconditions.checkNotNull(tender, "");
        Preconditions.checkArgument(userId.equals(tender.getUserId()), "获取立即转让详情: 非法操作!");

        Specification<BorrowCollection> bcs = Specifications
                .<BorrowCollection>and()
                .eq("tenderId", tenderId)
                .eq("status", 0)
                .build();
        List<BorrowCollection> borrowCollections = borrowCollectionService.findList(bcs, new Sort(Sort.Direction.ASC, "id"));
        Preconditions.checkNotNull(borrowCollections, "获取立即转让详情: 还款计划查询失败!");
        BorrowCollection borrowCollection = borrowCollections.get(0);
        Borrow borrow = borrowService.findById(tender.getBorrowId());
        Preconditions.checkNotNull(borrowCollections, "获取立即转让详情: 获取投资的标的信息失败!");
        String repayFashionStr = "";
        switch (borrow.getRepayFashion()) {
            case BorrowContants.REPAY_FASHION_AYFQ_NUM:
                repayFashionStr = "按月分期";
                break;
            case BorrowContants.REPAY_FASHION_YCBX_NUM:
                repayFashionStr = "一次性还本付息";
                break;
            case BorrowContants.REPAY_FASHION_XXHB_NUM:
                repayFashionStr = "先息后本";
                break;
            default:
        }

        int money = borrowCollections.stream().mapToInt(borrowCollectionItem -> borrowCollectionItem.getPrincipal()).sum(); // 待汇款本金
        // 0.4% + 0.08% * (剩余期限-1)  （费率最高上限为1.28%）
        double rate = 0.004 + 0.0008 * (borrowCollections.size() - 1);
        rate = Math.min(rate, 0.0128);
        Double fee = money * rate;  // 费用
        int day = DateHelper.diffInDays(borrowCollection.getCollectionAt(), new Date(), false);
        day = day < 0 ? 0 : day;
        VoGoTenderInfo voGoTenderInfo = VoGoTenderInfo.ok("查询成功!", VoGoTenderInfo.class);
        voGoTenderInfo.setTenderId(tender.getId());
        voGoTenderInfo.setApr(StringHelper.formatDouble(borrow.getApr(), 100.0, false));
        voGoTenderInfo.setBorrowName(borrow.getName());
        voGoTenderInfo.setNextRepaymentDate(DateHelper.dateToString(borrowCollection.getCollectionAt(), DateHelper.DATE_FORMAT_YMD));
        voGoTenderInfo.setSurplusDate(String.valueOf(day));
        voGoTenderInfo.setRepayFashionStr(repayFashionStr);
        voGoTenderInfo.setTimeLimit(String.valueOf(borrowCollections.size()) + "个月");
        voGoTenderInfo.setMoney(StringHelper.formatDouble(money, 100.0, true));
        voGoTenderInfo.setFee(StringHelper.formatDouble(fee, 100.0, true));
        return ResponseEntity.ok(voGoTenderInfo);
    }
}
