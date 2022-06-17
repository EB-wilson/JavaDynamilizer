public class Demo{
  public String name;

  public Demo(String name){
    this.name = name;
  }

  public void run(int i){
    super.toString();
    t();
    long time = System.nanoTime();
    if(i > time){
      System.out.println(i);
    }
    else System.out.println(time);

    Object t = time;
    if(t instanceof Long){
      System.out.println("io");
    }
  }

  private void t(){}
}
