/*
 * Created on 04-May-2004
 *
 * (c) 2003-2005 ThoughtWorks Ltd
 *
 * See license.txt for license details
 */
package com.thoughtworks.proxy.toys.decorate;

import com.thoughtworks.proxy.Invoker;

import java.lang.reflect.Method;

/**
 * A simple Invoker implementation, that routes any call to a target object. A <code>null</code> value as target
 * can be handled.
 * @author Aslak Helles&oslash;y
 * @since 0.1
 */
class SimpleInvoker implements Invoker {
    private final Object target;

    /**
     * Construct a SimpleInvoker.
     * 
     * @param target The invocation target.
     */
    public SimpleInvoker(Object target) {
        this.target = target;
    }

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        return (target == null ? null : method.invoke(target, args));
    }
}
