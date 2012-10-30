/*
 * All content copyright Terracotta, Inc., unless otherwise indicated. All rights reserved.
 */
package com.tc.platform;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * @author Abhishek Sanoujam
 */
public class InterceptedProxy {

  public static interface Interceptor {
    void intercept(Object proxy, Method method, Object[] args) throws Exception;
  }

  /**
   * Returns an intercepted proxy. All method invocations on the proxy is intercepted and goes through the
   * interceptor.intercept() method. The method is then delegated to the actual delegate
   */
  public static <T> T createInterceptedProxy(final T actualDelegate, Class<T> interfaceClass,
                                                      final Interceptor interceptor) {
    InvocationHandler handler = new InvocationHandler() {

      @Override
      public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        interceptor.intercept(proxy, method, args);
        return method.invoke(actualDelegate, args);
      }
    };
    return (T) Proxy.newProxyInstance(interfaceClass.getClassLoader(), new Class[] { interfaceClass }, handler);
  }
}
