package com.servlet;

import com.annotation.XAutowired;
import com.annotation.XController;
import com.annotation.XRequestMapping;
import com.annotation.XService;
import com.controller.HelloController;
import com.serivce.AService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

public class XDispatchServlet extends HttpServlet {
    Properties contextConfig=new Properties();
    List<String> classNameList=new ArrayList<>();
    Map<String,Object> iocMap=new HashMap<>();
    Map<String,Method> handlerMapping=new HashMap<>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        //System.out.println(req.getContextPath());
        doPost(req, resp);
    }
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            doDispatch(req,resp);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    public void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws IOException, InvocationTargetException, IllegalAccessException {
        String url=req.getRequestURI().replaceAll(req.getContextPath(),"").replaceAll("/+","/");
        if(!handlerMapping.containsKey(url)){
            resp.getWriter().write("404 ");
            return;
        }
        Method method=handlerMapping.get(url);
        String  beanName=toLowerFirstCase(method.getDeclaringClass().getSimpleName());
        method.invoke(iocMap.get(beanName),req,resp);

    }
    @Override
    public void init(ServletConfig config) throws ServletException {
        System.out.println("init");
        //1.加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));
        //2.记录所有类
        doScanner(contextConfig.getProperty("scan-package"));
        //3.IOC容器
        doInstance();
        //4.依赖注入
        doAutowired();
        //5.初始化HandlerMapping
        initHandlerMapping();

        doTestPrintData();
    }
    public void initHandlerMapping(){
        if(iocMap.isEmpty()){
            return;
        }
        for(Map.Entry<String,Object> entry:iocMap.entrySet()){
            Class<?> clazz=entry.getValue().getClass();
            if(!clazz.isAnnotationPresent(XController.class)){
                continue;
            }
            String baseUrl;
            if(clazz.isAnnotationPresent(XRequestMapping.class)){
                XRequestMapping xRequestMapping=clazz.getAnnotation(XRequestMapping.class);
                baseUrl=xRequestMapping.value();
                Method[] methods=clazz.getMethods();
                for(Method method:methods){
                    if(!method.isAnnotationPresent(XRequestMapping.class)){
                        continue;
                    }
                    baseUrl="/"+baseUrl+"/"+method.getAnnotation(XRequestMapping.class).value();
                    handlerMapping.put(baseUrl,method);
                    System.out.println("HandlerMapping-"+baseUrl+"-"+method.getName());
                }
            }
        }
    }
    public void doAutowired(){
        if(iocMap.isEmpty()){
            return;
        }
        for(Map.Entry<String,Object> entry:iocMap.entrySet()){
            Field [] fields=entry.getValue().getClass().getDeclaredFields();
            for(Field field:fields){
                if(!field.isAnnotationPresent(XAutowired.class)){
                    continue;
                }
                String beanName=field.getType().getSimpleName();
                if(!"".equals(field.getAnnotation(XAutowired.class).value())){
                    beanName=field.getAnnotation(XAutowired.class).value().trim();
                }
                field.setAccessible(true);
                try {
                    field.set(entry.getValue(),iocMap.get(beanName));
                    System.out.println("XAutowired-"+entry.getKey()+"-"+field.getName()+"-"+beanName);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }

            }
        }
    }
    public void doInstance(){
        if(classNameList.isEmpty()){
            return;
        }else{
            try {
                for(String className:classNameList){

                    Class<?> clazz=Class.forName(className);
                    if(clazz.isAnnotationPresent(XController.class)){
                        XController xController=clazz.getAnnotation(XController.class);
                        String beanName=toLowerFirstCase("".equals(xController.value())?clazz.getSimpleName():xController.value());
                        Object instance=clazz.newInstance();
                        iocMap.put(beanName,instance);
                        System.out.println("[INFO-3] {" + beanName + "} has been saved in iocMap.");
                    }else if(clazz.isAnnotationPresent(XService.class)){
                        XService xService=clazz.getAnnotation(XService.class);
                        String beanName=toLowerFirstCase("".equals(xService.value())?clazz.getSimpleName():xService.value());
                        Object instance=clazz.newInstance();
                        iocMap.put(beanName,instance);
                        // 找类的接口
                        for (Class<?> i : clazz.getInterfaces()) {
                            if (iocMap.containsKey(i.getName())) {
                                throw new Exception("The Bean Name Is Exist.");
                            }
                            iocMap.put(i.getName(), instance);
                            System.out.println("[INFO-3] {" + i.getName() + "} has been saved in iocMap.");
                        }
                    }

            }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    /**
     * 获取类的首字母小写的名称
     *
     * @param className ClassName
     * @return java.lang.String
     */
    private String toLowerFirstCase(String className) {
        char[] charArray = className.toCharArray();
        charArray[0] += 32;
        return String.valueOf(charArray);
    }
    public void doScanner(String scanPackage){
        URL resourcePath=this.getClass().getClassLoader().getResource(scanPackage.replaceAll("\\.","/"));
        if(resourcePath==null){
            return;
        }
        File classpath=new File(resourcePath.getFile());
        System.out.println("-----classpth="+classpath);
        for(File file:classpath.listFiles()){
            if(file.isDirectory()){
                doScanner(scanPackage+"."+file.getName());
            }else{
                if(!file.getName().endsWith(".class")){
                    continue;
                }else{
                    String classname=(scanPackage + "." + file.getName()).replace(".class", "");
                    classNameList.add(classname);
                    System.out.println("add class success-----"+classname);
                }
            }
        }


    }
    public void doLoadConfig(String contextConfigLocation){
        InputStream inputStream=Thread.currentThread().getContextClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            contextConfig.load(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            try {
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    public static void main(String[] args) {
        Class a= HelloController.class;
        Annotation [] annotations=a.getDeclaredAnnotations();
        for(Annotation annotation:annotations){
            System.out.println(annotation.annotationType());
        }

    }

    private void doTestPrintData() {

        System.out.println("[INFO-6]----data------------------------");

        System.out.println("contextConfig.propertyNames()-->" + contextConfig.propertyNames());

        System.out.println("[classNameList]-->");
        for (String str : classNameList) {
            System.out.println(str);
        }

        System.out.println("[iocMap]-->");
        for (Map.Entry<String, Object> entry : iocMap.entrySet()) {
            System.out.println(entry);
        }

        System.out.println("[handlerMapping]-->");
        for (Map.Entry<String, Method> entry : handlerMapping.entrySet()) {
            System.out.println(entry);
        }

        System.out.println("[INFO-6]----done-----------------------");

        System.out.println("====启动成功====");

    }
}
