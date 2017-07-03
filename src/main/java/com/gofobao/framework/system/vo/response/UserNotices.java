package com.gofobao.framework.system.vo.response;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Created by admin on 2017/6/15.
 */
@Data
public class UserNotices {
    private Long id;

    private String time;

    private String title;

    @ApiModelProperty(hidden =true)
    private Integer pageCount;
}
