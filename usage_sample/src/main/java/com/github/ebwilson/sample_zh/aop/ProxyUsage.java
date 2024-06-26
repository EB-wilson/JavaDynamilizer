package com.github.ebwilson.sample_zh.aop;

import dynamilize.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**本篇向您演示该框架进行代理模式AOP的示例，这是一个类似cglib的行为，如果您不了解cglib，那么您可以参考{@link java.lang.reflect.Proxy}，此用法与反射的Proxy类似，但是这并不依赖于切面对象的公共接口*/
public class ProxyUsage {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        //获取默认动态工厂
        DynamicMaker maker = DynamicFactory.getDefault();

        //创建一个代理工厂，此类工厂将用于创建代理实例，当实例的任意可重写方法被调用时，其都会被捕获并转入声明该工厂时给定的代理函数，如下
        ProxyMaker proxyMaker = ProxyMaker.getDefault(maker, (proxy, func, superFunc, arg) -> {
            //调用任何方法时都将调用信息打印到输出流
            System.out.println("invoking " + superFunc + ", args: " + arg);

            //向上调用被拦截的方法，如果不调用的话此方法会被截断使此次调用无效
            return superFunc.invoke(proxy, arg);
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

        System.out.println("\n===========sep line==========\n");

        //当然，就像代理的实际使用方法一样，我们可以使用一个代理对象来代为处理对被代理对象的行为调用，例如，我们用前文生成的builder作为代理对象：
        DynamicObject<ArrayList<String>> dyBuilder = (DynamicObject<ArrayList<String>>) builder;
        ProxyMaker proxy = ProxyMaker.getDefault(maker, (self, func, superFunc, arg) -> {
            System.out.println("invoke to proxy object");
            return func.invoke(dyBuilder, arg);
        });//直接将所有方法调用重定向到dyBuilder
        List<String> listProxied = proxy.newProxyInstance(new Class[]{List.class}).objSelf();//创建的被代理的对象不应该超出委托目标的范围，这里使用List接口

        //测试打印
        listProxied.add("hello world again");
        System.out.println(builder);
        listProxied.clear();
        System.out.println(builder);

        System.out.println("\n===========sep line==========\n");

        //有时候，会需要对一些非动态化的对象作委托，这种时候需要将被代理的非动态对象进行动态包装，在DynamicMaker当中提供了一个包装方法：
        ArrayList<String> delegate = new ArrayList<>();
        DynamicObject<ArrayList<String>> wrapped = maker.wrapInstance(delegate);

        //这个对象与来自newInstance的动态对象不同，这是一个受限制的动态对象，例如它只能用于动态调用被包装对象的已有方法，而不能创建新函数
        wrapped.invokeFunc("add", "hello world");
        System.out.println(delegate);

        //并且尝试转换到被包装对象的类型会发生异常：
        //ArrayList<String> test = (ArrayList<String>)wrapped;//抛出异常
        //应使用:
        ArrayList<String> test = wrapped.objSelf();

        //该包装可被用在类型代理上，当调用需要的被代理对象是一个非动态对象，而又不希望对它动态化，则可将其进行包装：
        ProxyMaker wrapProxy = ProxyMaker.getDefault(maker, (self, func, superFunc, arg) -> {
            System.out.println("invoke to proxy object, delegate method: " + func.signature());
            return func.invoke(wrapped, arg);
        });
        List<String> proxied = wrapProxy.newProxyInstance(new Class[]{List.class}).objSelf();

        //测试打印
        proxied.add("i am a robot");
        System.out.println(delegate);
        proxied.clear();
        System.out.println(delegate);
    }
}
