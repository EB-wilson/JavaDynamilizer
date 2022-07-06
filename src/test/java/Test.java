public class Test{
  public static void main(final String[] args) throws NoSuchMethodException{
    new T1().run();
    new T2().run();
  }

  public static class T1{
    public void run(){
      System.out.println(T1.class.hashCode());
    }
  }

  public static class T2{
    public void run(){
      System.out.println(T2.class.hashCode());
    }
  }
}
