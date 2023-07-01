package tesaaa;

import test.Sample;

public class TBA extends Sample {

  void run2(){
    System.out.println("ordinal");
  }

  public void run1(){
    super.run1();
    run2();
  }
}
