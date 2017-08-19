package com.gofobao.framework.member.biz.impl;

import com.gofobao.framework.asset.biz.AssetSynBiz;
import com.gofobao.framework.asset.entity.Asset;
import com.gofobao.framework.asset.service.AssetService;
import com.gofobao.framework.common.qiniu.common.QiniuException;
import com.gofobao.framework.common.qiniu.common.Zone;
import com.gofobao.framework.common.qiniu.http.Response;
import com.gofobao.framework.common.qiniu.storage.Configuration;
import com.gofobao.framework.common.qiniu.storage.UploadManager;
import com.gofobao.framework.common.qiniu.util.Auth;
import com.gofobao.framework.common.rabbitmq.MqConfig;
import com.gofobao.framework.common.rabbitmq.MqHelper;
import com.gofobao.framework.common.rabbitmq.MqQueueEnum;
import com.gofobao.framework.common.rabbitmq.MqTagEnum;
import com.gofobao.framework.core.helper.PasswordHelper;
import com.gofobao.framework.core.vo.VoBaseResp;
import com.gofobao.framework.currency.entity.Currency;
import com.gofobao.framework.currency.service.CurrencyService;
import com.gofobao.framework.helper.*;
import com.gofobao.framework.helper.project.UserHelper;
import com.gofobao.framework.integral.entity.Integral;
import com.gofobao.framework.integral.service.IntegralService;
import com.gofobao.framework.member.biz.UserBiz;
import com.gofobao.framework.member.entity.*;
import com.gofobao.framework.member.enums.RegisterSourceEnum;
import com.gofobao.framework.member.service.*;
import com.gofobao.framework.member.vo.request.VoRegisterReq;
import com.gofobao.framework.member.vo.request.VoRestPayPassWord;
import com.gofobao.framework.member.vo.request.VoSettingTranPassWord;
import com.gofobao.framework.member.vo.request.VoUserInfoUpdateReq;
import com.gofobao.framework.member.vo.response.VoBasicUserInfoResp;
import com.gofobao.framework.member.vo.response.VoOpenAccountInfo;
import com.gofobao.framework.member.vo.response.pc.ServiceUser;
import com.gofobao.framework.member.vo.response.pc.UserInfoExt;
import com.gofobao.framework.member.vo.response.pc.VipInfoRes;
import com.gofobao.framework.member.vo.response.pc.VoViewServiceUserListWarpRes;
import com.gofobao.framework.security.helper.JwtTokenHelper;
import com.gofobao.framework.security.vo.VoLoginReq;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Zeke on 2017/5/19.
 */
@Service
@Slf4j
public class UserBizImpl implements UserBiz {
    static final Gson GSON = new Gson();

    @Autowired
    UserService userService;

    @Autowired
    UserInfoService userInfoService;

    @Autowired
    AssetService assetService;

    @Autowired
    UserCacheService userCacheService;

    @Autowired
    CurrencyService currencyService;

    @Autowired
    UserThirdAccountService userThirdAccountService;

    @Autowired
    VipService vipService;


    @Autowired
    RedisHelper redisHelper;

    @Autowired
    MacthHelper macthHelper;

    @Autowired
    AssetSynBiz assetSynBiz;


    @Value("${jwt.header}")
    String tokenHeader;

    @Value("${jwt.prefix}")
    String prefix;

    @Value("${gofobao.imageDomain}")
    String imageDomain;

    @Value("${gofobao.javaDomain}")
    String javaDomain;

    @Value("${qiniu.sk}")
     String SECRET_KEY;

    @Value("${qiniu.ak}")
     String ACCESS_KEY;

    @Value("${qiniu.domain}")
     String qiNiuDomain;

    @Value("${qiniu.bucket}")
     String bucketname;


    @Autowired
    JwtTokenHelper jwtTokenHelper;

    @Autowired
    MqHelper mqHelper;

    @Autowired
    private IntegralService integralService;

    /**
     * @param request       请求
     * @param voRegisterReq 注册实体
     * @return
     * @throws Exception
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<VoBaseResp> register(HttpServletRequest request, VoRegisterReq voRegisterReq) throws Exception {
        // 0.短信验证码

        boolean match = macthHelper.match(MqTagEnum.SMS_REGISTER.getValue(), voRegisterReq.getPhone(), voRegisterReq.getSmsCode());
        if (!match) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "短信验证码失效/错误, 请重新发送短信验证码!"));
        }

        // 1.手机处理
        boolean notPhoneState = userService.notExistsByPhone(voRegisterReq.getPhone());
        if (!notPhoneState) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "当前手机已经在平台注册！"));
        }

        // 2.用户名处理
        if (!StringUtils.isEmpty(voRegisterReq.getUserName())) {

            boolean notUserName = userService.notExistsByUserName(voRegisterReq.getUserName());
            if (!notUserName) {
                return ResponseEntity
                        .badRequest()
                        .body(VoBaseResp.error(VoBaseResp.ERROR, "当前用户名已经在平台注册！"));
            }
        }

        Integer parentId = 0;
        if (!StringUtils.isEmpty(voRegisterReq.getInviteCode())) {
            // 3.推荐人处理
            Users invitedUser = userService.findByInviteCode(voRegisterReq.getInviteCode());
            if (ObjectUtils.isEmpty(invitedUser)) {
                return ResponseEntity
                        .badRequest()
                        .body(VoBaseResp.error(VoBaseResp.ERROR, "无效的邀请码！"));
            }

            parentId = invitedUser.getId().intValue();
        }

        // 处理注册来源
        String source = voRegisterReq.getSource();
        Integer channel = RegisterSourceEnum.getIndex(source.toLowerCase());
        Date now = new Date();
        // 插入数据
        Users users = new Users();
        users.setEmail(null);
        users.setUsername(voRegisterReq.getUserName());
        users.setPhone(voRegisterReq.getPhone());
        users.setCardId(null);
        users.setPassword(PasswordHelper.encodingPassword(voRegisterReq.getPassword())); // 设置密码
        users.setPayPassword("");
        users.setRealname("");
        users.setType("");
        users.setBranch(0);
        users.setSource(channel);
        users.setInviteCode(GenerateInviteCodeHelper.getRandomCode()); // 生成用户邀请码
        users.setParentId(parentId);
        users.setParentAward(0);
        users.setCreatedAt(now);
        users.setUpdatedAt(now);

        users = userService.save(users);
        if ((ObjectUtils.isEmpty(users)) || (ObjectUtils.isEmpty(users.getId()))) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "服务器开小差了， 请稍候再试！"));
        }

        if (!registerExtend(users.getId())) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "服务器开小差了， 请稍候再试！"));
        }

        // 4.触发注册事件
        MqConfig mqConfig = new MqConfig();
        mqConfig.setQueue(MqQueueEnum.RABBITMQ_USER_ACTIVE);
        mqConfig.setTag(MqTagEnum.USER_ACTIVE_REGISTER);
        ImmutableMap<String, String> body = ImmutableMap
                .of(MqConfig.MSG_USER_ID, users.getId().toString(), MqConfig.MSG_TIME, DateHelper.dateToString(now));
        mqConfig.setMsg(body);
        mqConfig.setSendTime(DateHelper.addSeconds(new Date(), 30));
        boolean mqState;
        try {
            log.info(String.format("userBizImpl register send mq %s", GSON.toJson(body)));
            mqState = mqHelper.convertAndSend(mqConfig);
        } catch (Throwable e) {
            log.error("userBizImpl register send mq exception", e);
            throw new Exception(e);
        }

        if (!mqState) {
            log.error("userBizImpl register send mq error");
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "服务器开小差了，请稍候再试！"));

        }
        // 5.删除短信验证码
        redisHelper.remove(String.format("%s_%s", MqTagEnum.SMS_REGISTER, voRegisterReq.getPhone()));
        return ResponseEntity.ok(VoBaseResp.ok("注册成功"));
    }

    @Override
    public Users findByAccount(String account) {
        return userService.findByAccount(account);
    }

    @Override
    public ResponseEntity<VoBasicUserInfoResp> getUserInfoResp(Users user) {
        VoBasicUserInfoResp voBasicUserInfoResp = VoBaseResp.ok("操作成功", VoBasicUserInfoResp.class);
        UserThirdAccount userThirdAccount = userThirdAccountService.findByUserId(user.getId());
        if (ObjectUtils.isEmpty(userThirdAccount)) {
            voBasicUserInfoResp.setThirdAccountState(false);
            voBasicUserInfoResp.setBankPassworState(false);
            voBasicUserInfoResp.setBankState(false);
            voBasicUserInfoResp.setBankAccout(" ");
            voBasicUserInfoResp.setAutoTenderState(false);
            voBasicUserInfoResp.setAutoTranferState(false);
        } else {
            voBasicUserInfoResp.setThirdAccountState(true);
            voBasicUserInfoResp.setBankPassworState(userThirdAccount.getPasswordState() == 1);
            voBasicUserInfoResp.setBankAccout(UserHelper.hideChar(userThirdAccount.getCardNo(), UserHelper.BANK_ACCOUNT_NUM));
            voBasicUserInfoResp.setBankState(!StringUtils.isEmpty(userThirdAccount.getCardNo()));
            voBasicUserInfoResp.setAutoTenderState(userThirdAccount.getAutoTenderState().equals(1));
            voBasicUserInfoResp.setAutoTranferState(userThirdAccount.getAutoTransferState().equals(1));
            assetSynBiz.doAssetSyn(user.getId());
        }

        // 获取vip状态
        Vip vip = vipService.findTopByUserIdAndStatus(user.getId(), 1);
        voBasicUserInfoResp.setAvatarUrl(String.format("%S/data/images/avatar/$s_avatar_small.jpg", imageDomain, user.getId()));
        voBasicUserInfoResp.setVipState(ObjectUtils.isEmpty(vip) ? false : DateHelper.diffInDays(new Date(), vip.getExpireAt(), false) > 0);
        voBasicUserInfoResp.setEmail(UserHelper.hideChar(StringUtils.isEmpty(user.getEmail()) ? " " : user.getEmail(), UserHelper.EMAIL_NUM));
        voBasicUserInfoResp.setEmailState(!StringUtils.isEmpty(user.getEmail()));
        voBasicUserInfoResp.setPhone(UserHelper.hideChar(StringUtils.isEmpty(user.getPhone()) ? " " : user.getPhone(), UserHelper.PHONE_NUM));
        voBasicUserInfoResp.setPhoneState(!StringUtils.isEmpty(user.getPhone()));
        voBasicUserInfoResp.setRealname(UserHelper.hideChar(StringUtils.isEmpty(user.getRealname()) ? " " : user.getRealname(), UserHelper.REALNAME_NUM));
        voBasicUserInfoResp.setRealnameState(!StringUtils.isEmpty(user.getRealname()));
        voBasicUserInfoResp.setIdNo(UserHelper.hideChar(StringUtils.isEmpty(user.getCardId()) ? " " : user.getCardId(), UserHelper.CARD_ID_NUM));
        voBasicUserInfoResp.setRegisterAt(DateHelper.dateToString(user.getCreatedAt()));
        Integral integral = integralService.findByUserId(user.getId());
        voBasicUserInfoResp.setTenderIntegral(new Long(integral.getUseIntegral() + integral.getNoUseIntegral()));
        voBasicUserInfoResp.setIdNoState(!StringUtils.isEmpty(user.getCardId()));
        voBasicUserInfoResp.setAlias(user.getPushId());
        return ResponseEntity.ok(voBasicUserInfoResp);
    }

    @Override
    public ResponseEntity<VoBasicUserInfoResp> userInfo(Long userId) {
        Users user = userService.findById(userId);
        return getUserInfoResp(user);
    }

    /**
     * 金服用户登录
     *
     * @param httpServletRequest
     * @param response
     * @param voLoginReq
     * @return
     */
    @Override
    public ResponseEntity<VoBasicUserInfoResp> login(HttpServletRequest httpServletRequest, HttpServletResponse response, VoLoginReq voLoginReq, boolean financeState) {
        // 登录验证
        Users user = userService.findByAccount(voLoginReq.getAccount());
        if (ObjectUtils.isEmpty(user)) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "账户/密码错误", VoBasicUserInfoResp.class));
        }

        // 验证密码
        try {
            boolean assetState = PasswordHelper.verifyPassword(user.getPassword(), voLoginReq.getPassword());
            if (!assetState) {
                return ResponseEntity
                        .badRequest()
                        .body(VoBaseResp.error(VoBaseResp.ERROR, "账户/密码错误", VoBasicUserInfoResp.class));
            }
        } catch (Exception e) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "系统开小差了, 请轻声联系客服!", VoBasicUserInfoResp.class));
        }

        if (user.getIsLock()) {
            return ResponseEntity
                    .badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR, "账户已被系统冻结，如有问题请联系客服！", VoBasicUserInfoResp.class));
        }
        if (financeState) {
            if (!user.getType().equals("finance")) {  // 理财用户
                return ResponseEntity
                        .badRequest()
                        .body(VoBaseResp.error(VoBaseResp.ERROR, "系统拒绝了你的访问请求", VoBasicUserInfoResp.class));
            }
        } else {
            if (user.getType().equals("finance")) {  // 金服用户
                return ResponseEntity
                        .badRequest()
                        .body(VoBaseResp.error(VoBaseResp.ERROR, "系统拒绝了你的访问请求", VoBasicUserInfoResp.class));
            }
        }

        // 保存登录信息
        user.setIp(IpHelper.getIpAddress(httpServletRequest)); // 设置ip
        user.setSource(voLoginReq.getSource());
        user.setLoginTime(new Date());
        if (StringUtils.isEmpty(user.getPushId())) {   // 推送
            user.setPushId(UUID.randomUUID().toString().replace("-", ""));
        }
        user = userService.save(user);// 记录登录信息

        // 生成jwt
        String username = user.getUsername();
        if (StringUtils.isEmpty(username)) username = user.getPhone();
        if (StringUtils.isEmpty(username)) username = user.getEmail();
        user.setUsername(username);
        final String token = jwtTokenHelper.generateToken(user, voLoginReq.getSource());
        response.addHeader(tokenHeader, String.format("%s %s", prefix, token));
        try {
            // 触发登录队列
            MqConfig mqConfig = new MqConfig();
            mqConfig.setTag(MqTagEnum.LOGIN);
            mqConfig.setQueue(MqQueueEnum.RABBITMQ_USER_ACTIVE);
            mqConfig.setSendTime(DateHelper.addSeconds(new Date(), 10));
            ImmutableMap<String, String> body = ImmutableMap.of(MqConfig.MSG_USER_ID, user.getId().toString());
            mqConfig.setMsg(body);
            mqHelper.convertAndSend(mqConfig);
        } catch (Exception e) {
            log.error("触发登录队列异常", e);
        }

        return getUserInfoResp(user);
    }

    /**
     * 注册后续操作
     *
     * @param userId
     * @return
     */
    public boolean registerExtend(Long userId) throws Exception {
        Date now = new Date();
        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(userId);
        userInfo.setUpdatedAt(now);
        userInfo = userInfoService.save(userInfo);
        if ((ObjectUtils.isEmpty(userInfo))) {
            return false;
        }

        Asset asset = new Asset();
        asset.setCollection(0L);
        asset.setNoUseMoney(0L);
        asset.setPayment(0L);
        asset.setUpdatedAt(now);
        asset.setUseMoney(0L);
        asset.setVirtualMoney(0L);
        asset.setUserId(userId);
        asset = assetService.save(asset);
        if (ObjectUtils.isEmpty(asset)) {
            return false;
        }

        UserCache userCache = new UserCache();
        userCache.setUserId(userId);
        userCache = userCacheService.save(userCache);
        if (ObjectUtils.isEmpty(userCache)) {
            return false;
        }

        Integral integral = new Integral();
        integral.setUserId(userId);
        integral = integralService.save(integral);
        if (ObjectUtils.isEmpty(integral)) {
            return false;
        }

        Currency currency = new Currency();
        currency.setUserId(userId);
        currency = currencyService.save(currency);
        if (ObjectUtils.isEmpty(currency)) {
            return false;
        }

        return true;
    }

    @Override
    public ResponseEntity<VoOpenAccountInfo> openAccountInfo(Long userId) {
        UserThirdAccount userThirdAccount = userThirdAccountService.findByUserId(userId);
        if (ObjectUtils.isEmpty(userThirdAccount)) {
            return ResponseEntity.badRequest().body(VoBaseResp.error(VoBaseResp.ERROR, "你还没有开通银行存管!", VoOpenAccountInfo.class));
        }
        VoOpenAccountInfo voOpenAccountInfo = VoBaseResp.ok("查询成功", VoOpenAccountInfo.class);
        voOpenAccountInfo.setAccountId(userThirdAccount.getAccountId());
        voOpenAccountInfo.setRealName(String.format("*%s", userThirdAccount.getName().substring(1)));
        voOpenAccountInfo.setPasswordState(userThirdAccount.getPasswordState() == 1);
        voOpenAccountInfo.setPhone(userThirdAccount.getMobile().replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2"));
        voOpenAccountInfo.setSignedState(userThirdAccount.getAutoTransferState() == 1 && userThirdAccount.getAutoTransferState() == 1);
        if (userThirdAccount.getCardNoBindState() == 1) {
            voOpenAccountInfo.setBankName(userThirdAccount.getBankName());
            voOpenAccountInfo.setBankLogo(String.format("%s%s", javaDomain, userThirdAccount.getBankLogo()));
            voOpenAccountInfo.setBankNo(userThirdAccount.getCardNo().substring(userThirdAccount.getCardNo().length() - 5));
        } else {
            voOpenAccountInfo.setBankName("");
            voOpenAccountInfo.setBankLogo("");
            voOpenAccountInfo.setBankNo("");
        }
        return ResponseEntity.ok(voOpenAccountInfo);
    }

    /**
     * 用户扩展信息
     *
     * @param userId
     * @return
     */
    @Override
    public ResponseEntity<UserInfoExt> pcUserInfo(Long userId) {
        UserInfoExt userInfoExt = VoBaseResp.ok("查询成功", UserInfoExt.class);
        UserInfo userInfo = userInfoService.info(userId);
        userInfoExt.setAddress(userInfo.getAddress());
        userInfoExt.setBir(DateHelper.dateToString(userInfo.getBirthday(), DateHelper.DATE_FORMAT_YMD));
        userInfoExt.setQq(userInfo.getQq());
        userInfoExt.setGraduation(userInfo.getGraduation());
        userInfoExt.setEducation(userInfo.getEducation());
        userInfoExt.setMaritalStatus(userInfo.getMarital());
        userInfoExt.setSchool(StringUtils.isEmpty(userInfo.getSchool()) ? "" : userInfo.getSchool());
        userInfoExt.setSex(userInfo.getSex());
        return ResponseEntity.ok(userInfoExt);
    }

    @Override
    public ResponseEntity<VoBaseResp> pcUserInfoUpdate(VoUserInfoUpdateReq infoUpdateReq) {
        try {
            UserInfo userInfo = userInfoService.info(infoUpdateReq.getUserId());
            if (ObjectUtils.isEmpty(userInfo)) {
                return ResponseEntity.badRequest()
                        .body(VoBaseResp.error(VoBaseResp.ERROR,
                                "非法请求"));
            }
            if (!ObjectUtils.isEmpty(infoUpdateReq.getBirthday())) {
                Date birthday = infoUpdateReq.getBirthday();
                SimpleDateFormat yyyy = new SimpleDateFormat("yyyy");
                SimpleDateFormat md = new SimpleDateFormat("Md");
                userInfo.setBirthdayMd(Integer.valueOf(md.format(birthday).toString()));
                userInfo.setBirthdayY(Integer.valueOf(yyyy.format(birthday).toString()));
            }
            userInfo.setEducation(infoUpdateReq.getEducation());
            if (!StringUtils.isEmpty(infoUpdateReq.getQq())) {
                userInfo.setQq(infoUpdateReq.getQq());
            }
            userInfo.setGraduation(infoUpdateReq.getGraduation());
            if (!StringUtils.isEmpty(infoUpdateReq.getAddress())) {
                userInfo.setAddress(infoUpdateReq.getAddress());
            }
            userInfo.setSex(infoUpdateReq.getSex());

            if (!StringUtils.isEmpty(infoUpdateReq.getGraduatedSchool())) {
                userInfo.setGraduatedSchool(infoUpdateReq.getGraduatedSchool());
            }
            userInfo.setMarital(infoUpdateReq.getMarital());

            UserInfo userInfo1 = userInfoService.update(userInfo);
            if (ObjectUtils.isEmpty(userInfo1))
                return ResponseEntity.badRequest()
                        .body(VoBaseResp.error(VoBaseResp.ERROR,
                                "更新失败，请稍候再试！"));
            else
                return ResponseEntity.ok(VoBaseResp.ok("更新成功"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR,
                            "更新失败，请稍候再试！"));
        }
    }

    @Override
    public ResponseEntity<VoBaseResp> saveVip(Vip vip) {
        Boolean flag = vipService.save(vip);

        List<Users> users = userService.serviceUser();
        boolean isContains = users.stream().map(p -> p.getId()).collect(Collectors.toList()).contains(vip.getKefuId());
        if (!isContains) {
            return ResponseEntity.badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR,
                            "非法请求"));
        }
        if (flag)
            return ResponseEntity.ok(VoBaseResp.ok("更新成功"));
        else
            return ResponseEntity.badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR,
                            "更新失败，请稍候再试！"));
    }

    @Override
    public ResponseEntity<VoViewServiceUserListWarpRes> serviceUserList() {

        try {
            VoViewServiceUserListWarpRes warpRes = VoBaseResp.ok("查询成功", VoViewServiceUserListWarpRes.class);
            List<Users> user = userService.serviceUser();
            List<ServiceUser> serviceUsers = new ArrayList<>(user.size());
            user.stream().forEach(p -> {
                ServiceUser serviceUser = new ServiceUser();
                serviceUser.setUserName(p.getUsername());
                serviceUser.setUserId(p.getId());
                serviceUsers.add(serviceUser);
            });
            warpRes.setServiceUsers(serviceUsers);
            return ResponseEntity.ok(warpRes);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest()
                    .body(VoBaseResp.error(VoBaseResp.ERROR,
                            "查询异常！",
                            VoViewServiceUserListWarpRes.class));
        }
    }


    @Override
    public ResponseEntity<VipInfoRes> vipInfo(Long userId) {
        Vip vip = vipService.findTopByUserIdAndStatus(userId, 1);
        VipInfoRes vipInfoRes = VoBaseResp.ok("查询成功", VipInfoRes.class);
        vipInfoRes.setEndAt(DateHelper.dateToString(vip.getExpireAt()));
        Users user = userService.findById(vip.getKefuId());
        vipInfoRes.setServiceUserName(user.getUsername());
        return ResponseEntity.ok(vipInfoRes);
    }

    @Override
    public ResponseEntity<VoBaseResp> saveUserTranPassWord(VoSettingTranPassWord tranPassWord) {
        Users users = userService.findById(tranPassWord.getUserId());
        if (ObjectUtils.isEmpty(users)) {
            return ResponseEntity.badRequest().body(VoBaseResp.error(VoBaseResp.ERROR, "非法访问"));
        }
        if (!StringUtils.isEmpty(users.getPayPassword()) && users.getPayPassword().length() > 1) {
            return ResponseEntity.badRequest().body(VoBaseResp.error(VoBaseResp.ERROR, "用户已设置过交易密码"));
        }
        try {
            users.setPayPassword(PasswordHelper.encodingPassword(tranPassWord.getTranPassWord()));
            userService.save(users);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(VoBaseResp.error(VoBaseResp.ERROR, "系统异常,请稍后再试试吧"));
        }
        return ResponseEntity.ok(VoBaseResp.ok("设置成功"));
    }

    @Override
    public ResponseEntity<VoBaseResp> restPayPassWord(VoRestPayPassWord restPayPassWord) {
        Users users = userService.findById(restPayPassWord.getUserId());
        if (ObjectUtils.isEmpty(users) || StringUtils.isEmpty(users.getPayPassword())) {
            return ResponseEntity.badRequest().body(VoBaseResp.error(VoBaseResp.ERROR, "非法访问"));
        }
        Boolean flag = macthHelper.match(MqTagEnum.SMS_REST_PAY_PASSWORD.getValue(), users.getPhone(), restPayPassWord.getCode());
        if (!flag)
            return ResponseEntity.badRequest().body(VoBaseResp.error(VoBaseResp.ERROR, "系统异常,请稍后再试试吧"));
        else
            return ResponseEntity.ok(VoBaseResp.ok("重置密码成功"));

    }
    
    public Map<String,Object> upload(byte[] file, String key) throws IOException {
        //密钥配置
        Auth auth = Auth.create(ACCESS_KEY, SECRET_KEY);
        //创建上传对象

        Zone z = Zone.autoZone();
        Configuration c = new Configuration(z);
        UploadManager uploadManager = new UploadManager(c);
        String token=auth.uploadToken(bucketname);
        Map<String, Object> resultMap = Maps.newHashMap();
        try {
            //调用put方法上传
            Response res = uploadManager.put(file, key, token);
            resultMap.put("result",Boolean.TRUE);
            resultMap.put("code", VoBaseResp.ERROR);
            resultMap.put("msg", res.bodyString());

        } catch (QiniuException e) {
            Response r = e.response;
            // 请求失败时打印的异常的信息
            System.out.println(r.toString());
            resultMap.put("result",Boolean.FALSE);
            resultMap.put("code", VoBaseResp.ERROR);
            resultMap.put("msg", r.bodyString());

        }
       return resultMap;
    }


    @Override
    public Map<String, Object> uploadAvatar(byte[] file, String filePath)throws Exception {
           return upload(file, filePath);

    }
}
