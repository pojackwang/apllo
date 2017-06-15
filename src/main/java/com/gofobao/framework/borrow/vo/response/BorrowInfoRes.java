package com.gofobao.framework.borrow.vo.response;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Created by admin on 2017/6/9.
 */
@Data
public class   BorrowInfoRes {

    @ApiModelProperty("每万元收益")
    private String earnings;

    @ApiModelProperty("期限")
    private String timeLimit;

    @ApiModelProperty("还款方式；0：按月分期；1：一次性还本付息；2：先息后本")
    private Integer repayFashion;

    @ApiModelProperty("投标记录")
    private String tenderCount;

    @ApiModelProperty("起投金额")
    private String lowest;

    @ApiModelProperty("融资金额")
    private String money;

    @ApiModelProperty("剩余金额")
    private String moneyYes;

    @ApiModelProperty("进度")
    private double spend;

    @ApiModelProperty("年华率")
    private String apr;

    @ApiModelProperty("结束时间")
    private String endAt;

    @ApiModelProperty("满标时间")
    private String successAt;

    @ApiModelProperty("新手标标识")
    private Boolean isNovice;

    @ApiModelProperty("状态 1.待发布 2.还款中 3.招标中 4.已完成 5.其它")
    private Integer status;

    @ApiModelProperty("秒差 ：当状态是招标中 为正数  其他状态则返回-1")
    private Long surplusSecond;

    @ApiModelProperty("标类型 type: 0：车贷标；1：净值标；2：秒标；4：渠道标 ; 5流转标")
    private Integer type;

}