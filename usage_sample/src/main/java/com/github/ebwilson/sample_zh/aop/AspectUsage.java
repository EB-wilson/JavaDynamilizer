package com.github.ebwilson.sample_zh.aop;

/**本篇案例演示的是更好的面向切面用法，在您阅读本篇前请务必了解动态类型的使用及行为效果，以理解本篇中各行为的效果*/
public class AspectUsage {
  static class ClassA{
    public void update(){}
    public void draw(){}
    public void add(Object obj){}
    public void remove(Object obj){}
  }

  static class ClassB{
    public void update(){}
    public void draw(){}
    public void showInfo(){}
  }

  static class ClassC{
    public void reset(){}
    public void update(){}
    public void showInfo(){}
  }
}
