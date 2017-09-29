package com.gofobao.framework.financial.service.impl;

import com.gofobao.framework.financial.entity.NewAleve;
import com.gofobao.framework.financial.repository.NewAleveRepository;
import com.gofobao.framework.financial.service.NewAleveService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class NewAleveServiceImpl implements NewAleveService {
    @Autowired
    NewAleveRepository newAleveRepository ;


    @Override
    public NewAleve findTopByQueryTimeAndTranno(String date, String tranno) {
        return newAleveRepository.findTopByQueryTimeAndTranno(date, tranno) ;
    }

    @Override
    public NewAleve save(NewAleve newAleve) {
        return newAleveRepository.save(newAleve) ;
    }
}
