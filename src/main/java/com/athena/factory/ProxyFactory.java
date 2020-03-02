package com.athena.factory;

import com.athena.annotation.MyAutowired;
import com.athena.annotation.MyComponent;
import com.athena.utils.TransactionManager;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.SQLException;

/**
 * @Author: xiaoxiang.zhang
 * @Description:代理对象创建工厂
 * @Date: Create in 9:09 PM 2020/3/2
 */
@MyComponent
public class ProxyFactory {

    @MyAutowired
    private TransactionManager transactionManager;

    public Object getJdkProxy(Object target, boolean isMethodProxy) {
        return Proxy.newProxyInstance(target.getClass().getClassLoader(), target.getClass().getInterfaces(), new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                return dealWithMethodProxy(method, args, isMethodProxy, target);
            }
        });
    }

    public Object getCglibProxy(Object target, boolean isMethodProxy) {
        return Enhancer.create(target.getClass(), new MethodInterceptor() {
            @Override
            public Object intercept(Object o, Method method, Object[] objects, MethodProxy methodProxy) throws Throwable {
                return dealWithMethodProxy(method, objects, isMethodProxy, target);
            }
        });
    }

    /**
     * @return java.lang.Object
     * @Author xiangxz
     * @Description 处理代理方法
     * @Date 9:42 PM 2020/3/2
     * @Param [method, args, isMethodProxy, target]
     */
    private Object dealWithMethodProxy(Method method, Object[] args, boolean isMethodProxy, Object target) throws SQLException, IllegalAccessException, InvocationTargetException {
        Object result = null;
        if (isMethodProxy) {
            //需要代理，那么直接执行事务
            try {
                // 开启事务(关闭事务的自动提交)
                transactionManager.beginTransaction();
                result = method.invoke(target, args);

                // 提交事务
                transactionManager.commit();
            } catch (Exception e) {
                e.printStackTrace();
                // 回滚事务
                transactionManager.rollback();
                // 抛出异常便于上层servlet捕获
                throw e;
            }
        } else {
            result = method.invoke(target, args);
        }
        return result;
    }
}
