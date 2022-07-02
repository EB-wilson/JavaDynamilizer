import java.lang.annotation.*;

@AnnoTest(value = {8778493766284947234L, 7876556374L})
public class Demo{
  private static Types t = Types.aS;
  private static Types[] ts = {Types.aS, Types.bS};

  @AnnoTest(9)
  private String IN;

  Demo o;

  String[] a = {"78", "77"};

  public Demo(final String name, Object data){}

  public static void main(String[] args){

  }

  public void test(){
    a[0] = "uju";

    int a = (int) o.annotationType();

    System.out.println("i");
  }

  public Object annotationType(Object... o){
    super.toString();
    return 8;
  }
}

@Target({ElementType.TYPE,ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@interface AnnoTest{
  long[] value();
}

enum Types{
  aS, bS, cS
}
