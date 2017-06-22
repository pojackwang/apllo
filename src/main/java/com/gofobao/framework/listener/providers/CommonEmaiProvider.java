package com.gofobao.framework.listener.providers;

import com.gofobao.framework.common.rabbitmq.MqConfig;
import com.gofobao.framework.core.helper.RandomHelper;
import com.gofobao.framework.helper.MacthHelper;
import com.gofobao.framework.helper.StringHelper;
import com.gofobao.framework.message.entity.SmsEntity;
import com.gofobao.framework.message.repository.SmsRepository;
import com.gofobao.framework.message.service.SmsTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by Max on 2017/5/17.
 */
@Component
@Slf4j
public class CommonEmaiProvider {

    @Autowired
    SmsTemplateService smsTemplateService ;

    @Autowired
    SmsRepository smsRepository ;

    @Autowired
    MacthHelper macthHelper;

    @Value("${gofobao.close-phone-send}")
    boolean closeEmailSend;

    public static final String TEMPLATE_KEY_SMSCODE = "smscode";


    @Autowired
    private JavaMailSender mailSender;

    /**
     * 邮件地址
     */
    public static final String EMAIL_ADDRESS = "service@gofobao.com";

    /**
     * 根据制定手机发送验证码
     * @param tag 类型
     * @param body 请求内容
     * @return 发送是否成功
     */
    public boolean doSendMessageCode(String tag, Map<String, String> body){
        checkNotNull(body,  "CommonEmaiProvider doSendMessageCode body is null") ;
        String email = body.get(MqConfig.EMAIL);
        String ip = body.get(MqConfig.IP) ;

        checkNotNull(email, "CommonEmaiProvider doSendMessageCode email is null") ;
        checkNotNull(ip, "CommonEmaiProvider doSendMessageCode ip is null") ;

        // 获取模板
        String template = smsTemplateService.findSmsTemplate(tag);
        checkNotNull(template, "CommonEmaiProvider doSendMessageCode template is null") ;

        // 获取随机验证码
        String code = RandomHelper.generateNumberCode(6); // 生成验证码
        log.info(String.format("验证码: %s", code));
        Map<String, String> params = new HashMap<>() ;
        params.put(TEMPLATE_KEY_SMSCODE, code) ;
        params.putAll(body);

        String message = replateTemplace(template, params);  // 替换短信模板
        boolean rs = false ;
        try {
            if(!closeEmailSend){
                sendSimpleEmail(email, body.get("subject"), message) ;
            }

            rs = true ;
        } catch (Exception e) {
            log.error("CommonEmaiProvider doSendMessageCode send message error", e);
            return false;
        }

        //  写入缓存
        if(rs){
            try {
                macthHelper.add(tag, email, code) ;
            } catch (Exception e) {
                log.error("CommonEmaiProvider doSendMessageCode put redis error", e);
                return false;
            }
        }

        // 写入数据库
        Date nowDate = new Date() ;
        SmsEntity smsEntity = new SmsEntity();
        smsEntity.setIp(ip) ;
        smsEntity.setType(tag) ;
        smsEntity.setContent(message) ;
        smsEntity.setPhone(email) ;
        smsEntity.setCreatedAt(nowDate) ;
        smsEntity.setStatus(rs? 0: 1) ;
        smsEntity.setUsername(email) ;
        smsEntity.setExt(" ");
        smsEntity.setId(null);
        smsEntity.setRrid(" ");
        smsEntity.setStime(" ");
        try {
            smsRepository.save(smsEntity);
        }catch (Exception e){
            log.error("保存数据失败", e);
        }
        return rs ;
    }


    /**
     * 邮件发送
     *
     * @param toEmail 收件人
     * @param subject 主题
     * @param text    消息内容
     */
    private boolean sendSimpleEmail(String toEmail, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(EMAIL_ADDRESS);
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(text);
        try {
            mailSender.send(message);
            return true;
        } catch (Exception e) {
            log.error("CommonEmaiProvider.sendSimpleEmail exception", e);
            return false;
        }
    }

    /**
     * 替换模板
     *
     * @param template 魔板
     * @param params   替换参数
     * @return 替换后模板
     */
    public static String replateTemplace(String template, Map<String, String> params) {
        return StringHelper.replateTemplace(template, "{", params, "}");
    }
}