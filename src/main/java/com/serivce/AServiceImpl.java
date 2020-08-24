package com.serivce;

import com.annotation.XService;

@XService
public class AServiceImpl implements AService{
    @Override
    public void doSomething() {
        System.out.println("do something...");
    }
}
