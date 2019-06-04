package com.fantj.mvc.framework.context;

import com.fantj.mvc.framework.annotation.Autowired;
import com.fantj.mvc.framework.annotation.Controller;
import com.fantj.mvc.framework.annotation.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.*;

public class ApplicationContext {
    /**
     * 存放示例对象的map
     */
    private Map<String, Object> instanceMapping = new HashMap<>();
    /**
     * 存放class信息的list
     */
    private List<String> classCache = new ArrayList<>();
    /**
     * 读取配置文件的properties
     */
    Properties config = new Properties();

    /**
     * 构造方法：
     * 传入一个配置文件路径，对IOC进行初始化
     */
    public ApplicationContext(String location) {
        InputStream is = null;
        try {
            // 1. 载入配置文件
//            InputStream is = this.getClass().getClassLoader().getResourceAsStream(location);
            is = new FileInputStream("D:\\workspace\\easy-springmvc\\src\\main\\resources\\application.properties");
            config.load(is);
            // 2. 获取配置属性-- 扫描的包
            String packageName = config.getProperty("packageScan");
            // 3. 注册
            doRegister(packageName);
            // 4. 初始化IOC
            doCreateBean();
            // 5. 依赖注入
            populate();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("IOC 容器已经初始化");
    }

    private void populate() {
        // 1. 判断IOC容器是否为空
        if (instanceMapping.isEmpty()){
            return;
        }
        // 2. 遍历 每个bean的字段
        for (Map.Entry<String,Object> entry: instanceMapping.entrySet()){
            // 获取每个bean的fields
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            // 遍历判断是否需要依赖注入
            for (Field field: fields){

                if (field.isAnnotationPresent(Autowired.class)){
                    Autowired autowired = field.getAnnotation(Autowired.class);
                    String id = autowired.value().trim();
                    if (id.equals("")){
                        // 如果用户没有自定义bean名， 则默认用类型来注入
                        id = field.getType().getName();
                    }
                    field.setAccessible(true);

                    try {
                        // entry.getValue() 代表的就是当前的类对象
                        field.set(entry.getValue(), instanceMapping.get(id));
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void doCreateBean() {
        // 1. 检查是否有类信息注册缓存
        if (classCache == null) {
            return;
        }
        // 2. 遍历classCache 并创建实例 存放到 instanceMapping
        try {
            for (String className : classCache) {
                Class<?> clazz = Class.forName(className);
                // 加了需要加入IOC容器的注解，才进行初始化
                if (clazz.isAnnotationPresent(Controller.class)){
                    // 类的首字母小写，并存入 instanceMapping
                    String id = firstCharToLower(clazz.getSimpleName());
                    instanceMapping.put(id, clazz.newInstance());
                }else if (clazz.isAnnotationPresent(Service.class)){
                    // service注解就有了 用户自定义名字的处理
                    Service service = clazz.getAnnotation(Service.class);
                    if (!service.value().equals("")){
                        // 如果用户未自定义名
                        instanceMapping.put(service.value(), clazz.newInstance());
                        continue;
                    }
                    // 再加载其接口类
                    Class<?>[] interfaces = clazz.getInterfaces();
                    for (Class<?> i: interfaces){
                        instanceMapping.put(i.getName(), clazz.newInstance());
                    }
                }else {
                    continue;
                }
            }
         }catch(ClassNotFoundException | IllegalAccessException | InstantiationException e){
                e.printStackTrace();
         }
    }

    private String firstCharToLower(String simpleName) {
        char[] chars = simpleName.toCharArray();
        chars[0] += 32;
        return chars.toString();
    }

    private void doRegister(String packageName) {
        // 1. 根据包名获取到 资源路径， 以便递归加载类信息
        URL resource = this.getClass().getClassLoader().getResource("/" + packageName.replaceAll("\\.", "/"));
        // 2. 递归加载类信息
        File files = new File(resource.getFile());
        for (File file : files.listFiles()) {
            // 判断是否是文件夹
            if (file.isDirectory()) {
                // 如果是文件夹，递归调用
                doRegister(packageName + "." + file.getName());
            } else {
                // 如果是文件， 将class去掉 ，把全限定文件名 放入classCache
                classCache.add(packageName + "." + file.getName().replaceAll(".class", "").trim());
            }
        }
    }

    /**
     * 获取所有实例对象
     */
    public Map<String,Object> getAll(){
        return instanceMapping;
    }


    /**
     * 获取配置类对象
     */
    public Properties getConfig() {
        return config;
    }
}
