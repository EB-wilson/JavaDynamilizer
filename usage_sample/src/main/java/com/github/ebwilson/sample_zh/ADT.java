package com.github.ebwilson.sample_zh;

import dynamilize.DynamicFactory;
import dynamilize.JavaHandleHelper;
import dynamilize.annotations.DynamilizeClass;

@DynamilizeClass
public abstract class ADT implements DynamilizeClass.DynamilizedClass<Object> {
  public static JavaHandleHelper $helper$ = DynamicFactory.getDefault().getHelper();

  public void run(){
    run2();
  }
  public void run1(String arg1){
    run3(arg1, "no");
  }
  public void run2(){}
  public void run3(String arg1, String arg2){
    run2();
  }

}
