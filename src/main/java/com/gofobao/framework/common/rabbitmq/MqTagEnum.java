package com.gofobao.framework.common.rabbitmq;

/**
 * Created by Max on 17/5/26.
 */
public enum MqTagEnum {
    SMS_WITHDRAW_CASH("SMS_WITHDRAW_CASH"),
    SMS_RESET_PASSWORD("SMS_RESET_PASSWORD"),
    SMS_SWICTH_PHONE("SMS_SWICTH_PHONE"),
    SMS_BUNDLE("SMS_BUNDLE"),
    SMS_MODIFY_BANK("SMS_MODIFY_BANK"),
    SMS_DEFAULT("SMS_DEFAULT"),
    SMS_EMAIL_BIND("SMS_EMAIL_BIND"),
    SEND_BORROW_PROTOCOL_EMAIL("SEND_BORROW_PROTOCOL_EMAIL"),
    SMS_RESET_PAY_PASSWORD("SMS_RESET_PAY_PASSWORD"),
    SMS_BORROW_SUCCESS("SMS_BORROW_SUCCESS"),
    SMS_RECEIVED_REPAY("SMS_RECEIVED_REPAY"),
    SMS_BORROW_REPAYMENT_PUSH("SMS_BORROW_REPAYMENT_PUSH"),
    SMS_REGISTER("SMS_REGISTER");
    private String value ;

    private MqTagEnum(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
