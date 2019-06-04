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
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Description:    java类作用描述
 * @Author:         Fant.J
 * @CreateDate:     2019/2/29 10:37
 */
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
     * 存放视图
     */
    private List<ViewResolver> viewResolvers = new ArrayList<>();


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
        // 初始化视图解析器
        initViewResolvers(context);

    }

    private void initViewResolvers(ApplicationContext context) {
        String templateRoot = context.getConfig().getProperty("templateRoot");
        String path = Objects.requireNonNull(this.getClass().getClassLoader().getResource(templateRoot)).getFile();
        File file = new File(path);
        for (File template: Objects.requireNonNull(file.listFiles())){
            viewResolvers.add(new ViewResolver(template.getName(), template));
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            doDispatch(req, resp);
        }catch (Exception e){
            resp.getWriter().write("500 Exception, Msg: "+ Arrays.toString(e.getStackTrace()));
        }
    }
    

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) {
        try {
            // 根据url 拿到handler
            Handler handler = getHandler(req.getRequestURI());
            if (handler == null){
                resp.getWriter().write("404 NOT Fount");
                return;
            }
            // 根据handler 获取 adapt 对象
            HandlerAdapter ha = getHandlerAdapter(handler);
            ModelAndView mv = ha.handle(req, resp, handler);
            // 模板解析 viewResolver
            applyViewResolve(resp, mv);

        } catch (IOException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    private void applyViewResolve(HttpServletResponse resp, ModelAndView mv) throws IOException {
        if (null == mv){
            return;
        }
        if (viewResolvers.isEmpty()){
            return;
        }
        
        for (ViewResolver viewResolver: viewResolvers){
            if (!mv.getView().equals(viewResolver.viewName)){
                continue;
            }
            // 解析模板
            String result = viewResolver.parse(mv);
            if (result != null){
                resp.getWriter().write(result);
                break;
            }
        }

    }

    private HandlerAdapter getHandlerAdapter(Handler handler) {
        if (handlerMapping.isEmpty()){
            return null;
        }
        return adapterMapping.get(handler);
    }

    /**
     * 根据请求url 获取 handler 对象
     */
    private Handler getHandler(String requestURI) {
        if (handlerMapping.isEmpty()){
            return null;
        }
        for (Handler handler: handlerMapping) {
            Matcher matcher = handler.pattern.matcher(requestURI);
            if (!matcher.matches()){
                continue;
            }
            return handler;
        }
        return null;
    }

    /**
     * 适配过程
     */
    private void initHandlerAdapters(ApplicationContext context) {
        if(handlerMapping.isEmpty()){
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

        /**
         *
         */
        public ModelAndView handle(HttpServletRequest req, HttpServletResponse resp, Handler handler) throws InvocationTargetException, IllegalAccessException {
            // 拿到request请求中的参数列表
            Map<String, String[]> reqParam = req.getParameterMap();
            // 拿到我们adapter处理完后的参数列表
            Class<?>[] parameterTypes = handler.method.getParameterTypes();
            // 定义一个Object数组来存放参数值
            Object[] paramValue = new Object[parameterTypes.length];
            // 遍历reqParam并赋值
            for (Map.Entry<String, String[]> entry: reqParam.entrySet()){
                String key = entry.getKey();
                String[] values = entry.getValue();
                // 将value进行格式化， 去掉空格,去掉[ ]
                String value = Arrays.toString(values).trim().replaceAll("[\\[]]","");
                // 判断 paramMapping 中是否有这个key，如果有：则赋值， 没有，则跳过
                if (!paramMappring.containsKey(key)){
                    continue;
                }
                // 赋值: 1. 拿到index
                int index = paramMappring.get(key);
                // 赋值：2. 存放到Object[], 这里要先判断参数类型
                paramValue[index] = paramTypeTransfer(key, parameterTypes[index]);
            }

            // 给HttpServletRequest和response参数赋值
            String reqName = HttpServletRequest.class.getName();
            if (this.paramMappring.containsKey(reqName)){
                int index = paramMappring.get(reqName);
                paramValue[index] = req;
            }
            String respName = HttpServletResponse.class.getName();
            if (paramMappring.containsKey(respName)){
                int index = paramMappring.get(respName);
                paramValue[index] = resp;
            }
            // 反射调用handler方法
            Object invoke = handler.method.invoke(handler.controller, paramValue);
            if (returnTypeIsModleAndView(handler)){
                return (ModelAndView)invoke;
            }else {
                return null;
            }

        }
    }

    private boolean returnTypeIsModleAndView(Handler handler) {
        return handler.method.getReturnType() == ModelAndView.class;
    }

    private Object paramTypeTransfer(String value, Class<?> key) {
       if (key == String.class){
           return value;
       }else if (key == Integer.class){
           return Integer.valueOf(value);
       }else if (key == int.class){
           return Integer.valueOf(value);
       }
       return null;
    }

    private class ViewResolver {
        private String viewName;
        private File file;

        public ViewResolver(String viewName, File file) {
            this.viewName = viewName;
            this.file = file;
        }

        public String parse(ModelAndView mv) throws IOException {
            StringBuffer buffer = new StringBuffer();
            RandomAccessFile file = new RandomAccessFile(this.file, "r");
            try {
                String line = null;
                while (null != (line = file.readLine())){
                    Matcher matcher = matcher(line);
                    while (matcher.find()){
                        for (int i = 0; i < matcher.groupCount(); i++ ){
                            // 拿到键
                            String key = matcher.group(i).replaceAll("\\$\\{|\\}","");
                            // 拿到值
                            Object value = mv.getModel().get(key);
                            if (null == value){ continue; }
                            // 将${name} 替换成 value值
                            line = line.replaceAll("\\$\\{" + key + "\\}", String.valueOf(value));
                        }
                    }
                    buffer.append(line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }finally {
                file.close();
            }
            return buffer.toString();
        }

        /**
         * 正则匹配
         */
        private Matcher matcher(String line) {
            Pattern pattern = Pattern.compile("\\$\\{(.+?)\\}", Pattern.CASE_INSENSITIVE);
            return pattern.matcher(line);
        }
    }
}

