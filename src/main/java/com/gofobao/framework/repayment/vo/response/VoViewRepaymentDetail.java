package com.gofobao.framework.repayment.vo.response;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Created by admin on 2017/6/2.
 */
@Data
@ApiModel("借款详情")
public class VoViewRepaymentDetail {

    @ApiModelProperty("项目名")
    private String borrowName;

    @ApiModelProperty("投资金额")
    private String money;

    @ApiModelProperty("投资时间")
    private String createdAt;

    @ApiModelProperty("年华利率")
    private String apr;

    @ApiModelProperty("期限")
    private String timeLimit;

    @ApiModelProperty("还款方式")
    private String repayFashion;

    @ApiModelProperty("起息时间")
    private String successAt;

    @ApiModelProperty("状态")
    private String status;

    @ApiModelProperty("状态描述 还款中 已还款")
    private String statusStr;

    @ApiModelProperty("应还利息")
    private String receivableInterest;

    @ApiModelProperty("已还利息")
    private String interest;

    @ApiModelProperty("已还本金")
    private String principal;



}
