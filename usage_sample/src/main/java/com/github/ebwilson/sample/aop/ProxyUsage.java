package com.github.ebwilson.sample.aop;

import dynamilize.DynamicClass;
import dynamilize.DynamicFactory;
import dynamilize.DynamicMaker;
import dynamilize.ProxyMaker;

import java.util.ArrayList;
import java.util.HashMap;

/**本篇向您演示该框架进行代理模式AOP的示例，这是一个类似cglib的行为，如果您不了解cglib，那么您可以参考{@link java.lang.reflect.Proxy}，此用法与反射的Proxy类似，但是这并不依赖于切面对象的公共接口*/
public class ProxyUsage {
    public static void main(String[] args) {
        //获取默认动态工厂
        DynamicMaker maker = DynamicFactory.getDefault();

        //创建一个代理工厂，此类工厂将用于创建代理实例，当实例的任意可重写方法被调用时，其都会被捕获并转入声明该工厂时给定的代理函数，如下
        ProxyMaker proxyMaker = ProxyMaker.getDefault(maker, (proxy, superFunction, arg) -> {
            //调用任何方法时都将调用信息打印到输出流
            System.out.println("invoking " + superFunction + ", args: " + arg);

            //向上调用被拦截的方法，如果不调用的话此方法会被截断使此次调用无效
            return superFunction.invoke(proxy, arg);
        });

        //利用proxyMaker创建代理实例，代理实例的行为都会被代理工厂代理委托到代理函数上
        ArrayList<String> list = proxyMaker.newProxyInstance(ArrayList.class).objSelf();
        HashMap<String, String> map = proxyMaker.newProxyInstance(HashMap.class).objSelf();

        //测试打印，您将会看到下面进行的每一次操作都会被截获并打印在输出流
        list.add("first");
        list.add("second");
        list.add("third");

        map.put("first", "a1");
        map.put("second", "b2");
        map.put("third", "c3");

        list.add(map.get(list.get(0)));

        System.out.println("\n===========sep line==========\n");

        //尽管这上面调用的几个方法都被声明在了集合框架的接口之中，但是不同于反射proxy，JDER代理是不需要被监视行为来自接口的，如下：
        //这一条语句会调用list的toString()方法，这个方法来自类Object，同样的，它也被监视了
        System.out.println(list);

        System.out.println("\n===========sep line==========\n");

        //另外，对于一个代理委托实例您依然可以在创建它的时候给它分配一个动态超类，动态类的行为则会被增加在此代理的行为之前

        //创建动态类
        DynamicClass Sample = DynamicClass.get("Sample");
        Sample.setFunction("add", (self, su, arg) -> {
            System.out.println("this is call after than proxy monitor");

            return su.invokeFunc("add", arg);
        }, Object.class);
        ArrayList<String> builder = proxyMaker.newProxyInstance(ArrayList.class, Sample).objSelf();

        //测试打印，观察打印结果，分配给动态对象的动态类行为被插入在监视器之前，代理处理器可以正确截断
        builder.add("hello ");
        builder.add("world");
        System.out.println(builder);
    }
}
