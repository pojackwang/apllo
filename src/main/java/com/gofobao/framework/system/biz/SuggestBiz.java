package com.gofobao.framework.system.biz;

import com.gofobao.framework.core.vo.VoBaseResp;
import com.gofobao.framework.system.entity.Suggest;
import org.springframework.http.ResponseEntity;

/**
 * Created by admin on 2017/8/10.
 */
public interface SuggestBiz {
        ResponseEntity<VoBaseResp> save(Suggest suggest);

}
