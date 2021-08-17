package com.controller;

import com.annotation.XAutowired;
import com.annotation.XController;
import com.annotation.XRequestMapping;
import com.serivce.AService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@XController
@XRequestMapping("hello")
public class HelloController {
    @XAutowired
    AService aService;
    @XRequestMapping("index")
    public String index(HttpServletRequest req, HttpServletResponse resp){
        aService.doSomething();
        String str="<h1>hello abc</hello>";
        System.out.println(str);
        try {
            resp.getWriter().write(str);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return str;
    }
}
