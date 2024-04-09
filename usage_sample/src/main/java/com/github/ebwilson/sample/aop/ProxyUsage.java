package com.github.ebwilson.sample.aop;

import dynamilize.DynamicClass;
import dynamilize.DynamicFactory;
import dynamilize.DynamicMaker;
import dynamilize.ProxyMaker;

import java.util.ArrayList;
import java.util.HashMap;

/**This case shows usage of proxy-mode AOP, which is similar to `cglib`. If you do not know about it, you can also refer to @link java.lang.reflect.Proxy}. The usage of Proxy in JDer is similar to ones in Reflection, but this does not depend on the common interface*/
public class ProxyUsage {
    public static void main(String[] args) {
        //Gets the default DynamicMaker
        DynamicMaker maker = DynamicFactory.getDefault();

        //Creates a ProxyMaker, which is used to create Proxy Instance. When any overridable methods in the proxy instance is invoked, the proxy function defined with the maker will be invoked instead.
        ProxyMaker proxyMaker = ProxyMaker.getDefault(maker, (proxy, func, superFunction, arg) -> {
            //Message will be printed whenever methods are invoked
            System.out.println("invoking " + superFunction + ", args: " + arg);

            //Invokes the `super` method. If not, the invocation will stop here instead of invoking the method od the object
            return superFunction.invoke(proxy, arg);
        });

        //Creates a proxy instance
        ArrayList<String> list = proxyMaker.newProxyInstance(ArrayList.class).objSelf();
        HashMap<String, String> map = proxyMaker.newProxyInstance(HashMap.class).objSelf();

        //Test printing, and then you will find that the message is always be printed before any invocation
        list.add("first");
        list.add("second");
        list.add("third");

        map.put("first", "a1");
        map.put("second", "b2");
        map.put("third", "c3");

        list.add(map.get(list.get(0)));

        System.out.println("\n===========sep line==========\n");

        //While the methods below is defined in the interface, but JDer does not require that.
        // When invoking the method `toString()`, which is from `Object`, the message is still be printed.
        System.out.println(list);

        System.out.println("\n===========sep line==========\n");

        //In addition, you can provide a dynamic type as its super dynamic class

        //Creates a dynamic type
        DynamicClass Sample = DynamicClass.get("Sample");
        Sample.setFunction("add", (self, su, arg) -> {
            System.out.println("this is call after than proxy monitor");

            return su.invokeFunc("add", arg);
        }, Object.class);
        ArrayList<String> builder = proxyMaker.newProxyInstance(ArrayList.class, Sample).objSelf();

        //Test printing. The dynamic function is inserted after the proxy monitor.
        builder.add("hello ");
        builder.add("world");
        System.out.println(builder);
    }
}
