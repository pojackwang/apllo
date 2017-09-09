package com.gofobao.framework.system.vo.request;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * Created by Zeke on 2017/9/9.
 */
@Data
public class VoFindThirdBatch {
    @ApiModelProperty(name = "sign", value = "签名")
    private String sign;

    @ApiModelProperty(name = "paramStr", value = "参数json", required = true)
    @NotNull(message = "参数json不能为空!")
    private String paramStr;
}
