package com.fantj.mvc.framework.servlet;

import com.fantj.mvc.framework.annotation.Controller;
import com.fantj.mvc.framework.annotation.RequestMapping;
import com.fantj.mvc.framework.annotation.RequestParam;
import com.fantj.mvc.framework.context.ApplicationContext;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Pattern;

public class DispatcherServlet extends HttpServlet {

    private static final String LOCATION = "contextConfigLocation";

    /**
     * 存放 Handler集合
     */
    private List<Handler> handlerMapping = new ArrayList<>();

    /**
     * 存放 Handler HandlerAdapter 集合
     */
    private Map<Handler, HandlerAdapter> adapterMapping= new HashMap<>();
    /**
     * 初始化IOC容器
     */
    @Override
    public void init(ServletConfig config) throws ServletException {
        ApplicationContext context = new ApplicationContext(config.getInitParameter(LOCATION));

        // 解析 url 和 method
        initHandlerMapping(context);
        // 适配器 -- 适配过程
        initHandlerAdapters(context);

    }

    /**
     * 适配过程
     */
    private void initHandlerAdapters(ApplicationContext context) {
        if(!handlerMapping.isEmpty()){
            return;
        }
        Map<String, Integer> paramMapping = new HashMap<>();
        for (Handler handler: handlerMapping ) {
            // 拿到 handler 对应的 method 对应的参数
            Class<?>[] parameterTypes = handler.method.getParameterTypes();
            // 遍历参数数组，并放入paramMapping 中， 以便后续工作用反射调用方法
            for (int i = 0; i < parameterTypes.length; i++) {
                Class<?> parameterType = parameterTypes[i];
                // 如果参数类型是HttpServletRequest/Response
                if (parameterType == HttpServletRequest.class ||
                    parameterType == HttpServletResponse.class){
                    paramMapping.put(parameterType.getName(), i);
                }
            }
            // 遍历参数上的注解
            Annotation[][] parameterAnnotations = handler.method.getParameterAnnotations();
            for (int i = 0; i < parameterAnnotations.length; i++) {
                for (Annotation a:parameterAnnotations[i] ) {
                    // 判断 @RequestParam参数
                    if (a instanceof RequestParam){
                        // 如果是， 拿到值进行存储
                        String value = ((RequestParam) a).value();
                        paramMapping.put(value, i);
                    }
                }
            }
            // 每个方法 对应存放其参数
            adapterMapping.put(handler, new HandlerAdapter(paramMapping));
        }
        // 循环将所有handler的反射执行 所要的参数存储好
    }

    private void initHandlerMapping(ApplicationContext context) {
        // 1. 拿到ioc容器
        Map<String, Object> ioc = context.getAll();
        if (ioc.isEmpty()) {
            return;
        }
        // 2. 拿到有Controller注解的类， 并解析其方法
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            if (!clazz.isAnnotationPresent(Controller.class)) {
                continue;
            }
            String url = "";
            if (clazz.isAnnotationPresent(RequestMapping.class)) {
                // 3. 获取类上的requestMapping 值
                RequestMapping clazzAnnotation = clazz.getAnnotation(RequestMapping.class);
                url = clazzAnnotation.value();
            }
            // 4. 处理方法上的RequestMapping 值
            Method[] methods = clazz.getDeclaredMethods();
            for (Method method : methods) {
                if (!method.isAnnotationPresent(RequestMapping.class)) {
                    continue;
                }
                RequestMapping annotation = method.getAnnotation(RequestMapping.class);
                String regex = url+annotation.value();
                Pattern pattern = Pattern.compile(regex);
                handlerMapping.add(new Handler(entry.getValue(), method, pattern));
                System.out.println("Mapping: " + regex + " " +  method.toString());
            }
        }
    }
    private class Handler{
        /**
         * 类
         */
        private Object controller;
        /**
         * 方法
         */
        private Method method;
        /**
         * url
         */
        private Pattern pattern;

        public Handler(Object controller, Method method, Pattern pattern) {
            this.controller = controller;
            this.method = method;
            this.pattern = pattern;
        }
    }

    /**
     * 方法适配器：存放方法的参数
     */
    private class HandlerAdapter {
        private Map<String, Integer> paramMappring;

        public HandlerAdapter(Map<String, Integer> paramMappring) {
            this.paramMappring = paramMappring;
        }
    }
}

