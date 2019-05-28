# Easy-MVC
>一个简单的springmvc的源码实现，参考springMVC的核心功能及源码实现。

项目中只有一个`Servlet`的依赖：
```$xslt
        <dependency>
            <groupId>javax.servlet</groupId>
            <artifactId>servlet-api</artifactId>
            <version>2.4</version>
            <scope>provided</scope>
        </dependency>
```
# How to use?

与spring&spring-mvc用法相同

# How to work ?

### IOC容器的实现
>我们都知道，mvc围绕着DispatchServlet来展开工作，但是也需要IOC容器的支撑。

`DispatchServlet.init()` 来实现IOC容器的加载。



### mvc功能实现
`DispatchServlet.doPost/doGet()` 来实现从客户端发起请求到页面渲染的工作，其工作流程图如下：
![](https://github.com/fantj2016/easy-springmvc/blob/master/mvc-workflow.png)


