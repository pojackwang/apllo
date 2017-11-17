package com.gofobao.framework.comment.vo.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.hibernate.validator.constraints.NotBlank;

/**
 * Created by xin on 2017/11/13.
 */
@Data
@ApiModel("评论回复接口")
public class VoTopicReplyReq {

    @ApiModelProperty("评论id")
    private Long topicCommentId;

    @ApiModelProperty("回复id")
    private Long topicReplyId;

    @ApiModelProperty("回复内容")
    @NotBlank(message = "回复内容不能为空")
    private String content;

}
