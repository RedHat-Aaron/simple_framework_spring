package com.athena.factory;

import com.alibaba.druid.util.StringUtils;
import com.athena.annotation.*;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import java.io.File;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author: xiaoxiang.zhang
 * @Description:真正区创建对象的工厂
 * @Date: Create in 10:14 PM 2020/3/1
 */
public class BeanFactory {

    /**
     * 定义需要解析的配置文件名称
     */
    private static final String CONFIG_FILE_NAME = "ApplicationContext.xml";

    /**
     * 单例对象集合
     */
    private static Map<String, Object> singletonBeanMap = new HashMap<>(256);

    static {
        //在这个类的字节码加载进入虚拟机的时候直接对applicationContext.xml进行解析
        try {
            InputStream resourceAsStream = BeanFactory.class.getClassLoader().getResourceAsStream(CONFIG_FILE_NAME);
            if (null == resourceAsStream) {
                throw new RuntimeException("请提供ApplicationContext.xml配置文件!");
            }
            SAXReader saxReader = new SAXReader();
            Document document = saxReader.read(resourceAsStream);
            Element rootElement = document.getRootElement();

            //解析注解Bean
            parserAnnotationBean(rootElement);

            //解析xmlBean
            parserXmlBean(rootElement);

            //对创建的Bean进行注解属性注入
            populateAnnotionPropertyForBean();

            //对创建的Bean进行setter属性注入
            populateXmlPropertyForBean(rootElement);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    /**
     * @return void
     * @Author xiangxz
     * @Description 解析注解Bean
     * @Date 11:04 PM 2020/3/1
     * @Param [rootElement]
     */
    private static void parserAnnotationBean(Element rootElement) throws Exception {
        //解析扫描注解路径(这个只对第一个配置生效)
        List<Element> basePackages = rootElement.selectNodes("//component-scan");

        String scanPath = basePackages.get(0).attributeValue("base-package");

        if (StringUtils.isEmpty(scanPath)) {
            throw new RuntimeException("component-scan is error");
        }
        scanPath = scanPath.replace(".", File.separator);
        String rootPath = Thread.currentThread().getContextClassLoader().getResource(scanPath).getPath();
        scanAndCreateBean(rootPath);
    }

    /**
     * @return void
     * @Author xiangxz
     * @Description 扫描并且创建Bean
     * @Date 11:09 PM 2020/3/1
     * @Param [scanPath]
     */
    private static void scanAndCreateBean(String filePath) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        File parentFile = new File(filePath);
        File[] childs = parentFile.listFiles();
        for (File child : childs) {
            //开始判断是否为普通文件，否则继续递归
            if (child.isDirectory()) {
                scanAndCreateBean(child.getPath());
            } else {
                String name = child.getAbsolutePath();
                String[] splits = name.split("\\.");
                String fileName = splits[0];
                //从com下面开始截取包路径
                int index = fileName.indexOf("com");
                String basePackage = fileName.substring(index);

                //生成类路径
                String classPath = basePackage.replace(File.separator, ".");
                Class fileClazz = Class.forName(classPath);

                //如果当前字节码类型不为普通类，那么直接跳过
                if (fileClazz.isAnnotation() || fileClazz.isInterface()) {
                    continue;
                }

                //开始判断当前类文件上是否存在@Service注解,向单例map中注册
                parserBeanAnnotation(classPath, fileClazz);
            }
        }
    }

    private static void parserBeanAnnotation(String classPath, Class fileClazz) throws InstantiationException, IllegalAccessException {
        Annotation[] annotations = fileClazz.getAnnotations();
        if (0 == annotations.length) {
            return;
        }
        for (Annotation annotation : annotations) {
            String beanName = null;
            if (annotation instanceof MyService) {
                //循环到@Service注解以后进行初始化注册(名称相同进行覆盖)
                MyService myService = (MyService) annotation;
                beanName = myService.value();
            } else if (annotation instanceof MyRepository) {
                MyRepository myRepository = (MyRepository) annotation;
                beanName = myRepository.value();
            } else if (annotation instanceof MyComponent) {
                MyComponent myComponent = (MyComponent) annotation;
                beanName = myComponent.value();
            } else {
                //三者都不是就直接略过不进行创建
                continue;
            }
            //判断是否设置实例化名称
            if (StringUtils.isEmpty(beanName)) {
                //若未设置当前初始化名称，则使用当前类名首字母小写来进行注入
                beanName = dealWithBeanNameToKey(classPath);
            }
            singletonBeanMap.put(beanName, fileClazz.newInstance());
        }
    }

    /**
     * @return void
     * @Author xiangxz
     * @Description 解析xmlBean
     * @Date 11:04 PM 2020/3/1
     * @Param [rootElement]
     */
    private static void parserXmlBean(Element rootElement) throws Exception {
        List<Element> beanTags = rootElement.selectNodes("//bean");
        //开始初始化对应的配置bean
        beanTags.forEach(ele -> {
            //获取对应的id
            String beanName = ele.attributeValue("id");
            //获取对应的class
            String className = ele.attributeValue("class");
            Class fileClazz = null;
            try {
                fileClazz = Class.forName(className);
                //向单例集合中注册对应的实例对象
                Object obj = fileClazz.newInstance();
                singletonBeanMap.put(beanName, obj);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * @return void
     * @Author xiangxz
     * @Description 对创建的Bean进行属性自动注入
     * @Date 11:45 PM 2020/3/1
     * @Param []
     */
    private static void populateAnnotionPropertyForBean() throws IllegalAccessException {
        for (Object newInstance : singletonBeanMap.values()) {
            //获得真实类型
            Class<?> realType = newInstance.getClass();
            //获得当前类中所有的字段
            Field[] declaredFields = realType.getDeclaredFields();
            //对当前字段进行扫描，查看是否存在MyAutowired注解
            for (Field field : declaredFields) {
                Annotation[] fieldAnnotations = field.getAnnotations();
                if (null == fieldAnnotations || 0 == fieldAnnotations.length) {
                    continue;
                }
                for (Annotation annotation : fieldAnnotations) {
                    if (!(annotation instanceof MyAutowired)) {
                        continue;
                    }
                    //对属性进行注入
                    MyAutowired myAutowired = (MyAutowired) annotation;
                    String beanName = myAutowired.value();
                    if (StringUtils.isEmpty(beanName)) {
                        beanName = dealWithBeanNameToKey(field.getType().getSimpleName());
                    }
                    Object o = singletonBeanMap.get(beanName);
                    field.setAccessible(true);
                    field.set(newInstance, o);
                }
            }
        }
    }

    /**
     * @return void
     * @Author xiangxz
     * @Description 对创建的Bean进行xml属性注入
     * @Date 11:45 PM 2020/3/1
     * @Param []
     */
    private static void populateXmlPropertyForBean(Element rootElement) throws IllegalAccessException {
        List<Element> beanTags = rootElement.selectNodes("//bean");
        //开始初始化对应的配置bean
        for (Element ele : beanTags) {
            //获取对应的id
            String beanName = ele.attributeValue("id");
            //查看是否拥有子标签
            List<Element> properties = ele.selectNodes("property");
            if (null == properties || 0 == properties.size()) {
                continue;
            }
            properties.forEach(childEle -> {
                String fieldName = childEle.attributeValue("name");
                String refClassName = childEle.attributeValue("ref");
                //查看当前类中是否存在一个set方法
                Object parentObj = singletonBeanMap.get(beanName);
                String methodName = dealWithBeanNameToMethod(fieldName);
                Method[] methods = parentObj.getClass().getMethods();
                for (Method method : methods) {
                    if (!methodName.equals(method.getName())) {
                        continue;
                    }
                    try {
                        Object refBean = singletonBeanMap.get(refClassName);
                        if (null == refBean) {
                            throw new RuntimeException(beanName + "配置引用对象未被注册！");
                        }
                        method.invoke(parentObj, refBean);
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }

    public static Object getBean(String name) {
        if (StringUtils.isEmpty(name)) {
            throw new RuntimeException("需要获取的对象不能为空");
        }
        Object target = singletonBeanMap.get(name);
        if (null != target) {
            return dealWithProxyBean(target);
        }
        return null;
    }

    /**
     * @return java.lang.Object
     * @Author xiangxz
     * @Description 处理动态代理Bean
     * @Date 9:38 PM 2020/3/2
     * @Param [target]
     */
    private static Object dealWithProxyBean(Object target) {
        ProxyFactory proxyFactory = (ProxyFactory) singletonBeanMap.get("proxyFactory");
        //代理对象的判断必须要在这里进行
        boolean isGlobleProxy = false;
        boolean isMethodProxy = false;
        Annotation[] classAnnotations = target.getClass().getAnnotations();
        for (Annotation annotation : classAnnotations) {
            if (annotation instanceof MyTransactional) {
                //存在全局事务注解,此对象创建时需要创建代理对象
                isGlobleProxy = true;
                continue;
            }
        }

        //只要有一个方法上存在，那就说明需要代理
        Method[] declaredMethods = target.getClass().getDeclaredMethods();
        outLoop:
        for (Method method : declaredMethods) {
            Annotation[] methodAnnotations = method.getAnnotations();
            for (Annotation annotation : methodAnnotations) {
                if (annotation instanceof MyTransactional) {
                    isMethodProxy = true;
                    break outLoop;
                }
            }
        }
        if (isGlobleProxy || isMethodProxy) {
            //判断当前目标对象是否存在接口
            if (target.getClass().getInterfaces().length > 0) {
                //存在接口使用jdk
                return proxyFactory.getJdkProxy(target, isMethodProxy);
            } else {
                //使用cglib
                return proxyFactory.getCglibProxy(target, isMethodProxy);
            }
        }
        return target;
    }

    /**
     * @return java.lang.String
     * @Author xiangxz
     * @Description 处理对象名称生成key
     * @Date 10:02 PM 2020/3/2
     * @Param [name]
     */
    private static String dealWithBeanNameToKey(String name) {
        int dotIndex = name.lastIndexOf(".");
        String dealnName = name.substring(dotIndex + 1);
        String firstChar = dealnName.substring(0, 1);
        return firstChar.toLowerCase() + dealnName.substring(1);
    }

    /**
     * @return java.lang.String
     * @Author xiangxz
     * @Description 将当前名称转化为方法名
     * @Date 10:56 PM 2020/3/2
     * @Param [name]
     */
    private static String dealWithBeanNameToMethod(String name) {
        int dotIndex = name.lastIndexOf(".");
        String dealnName = name.substring(dotIndex + 1);
        String firstChar = dealnName.substring(0, 1);
        return "set" + firstChar.toUpperCase() + dealnName.substring(1);
    }

    public static void main(String[] args) {
        Object transferService = BeanFactory.getBean("transferService");
        System.out.println(transferService);
    }
}
