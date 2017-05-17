package com.gofobao.framework.message.vo;

import com.gofobao.framework.core.vo.VoBaseReq;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Created by Max on 17/5/17.
 */
@Data
@ApiModel
public class VoSmsReq extends VoBaseReq{
    @ApiModelProperty(name = "手机号码", required = true, dataType = "String" )
    private String phone ;

    @ApiModelProperty(name = "图形验证码", required = true, dataType = "String" )
    private String captcha ;
}