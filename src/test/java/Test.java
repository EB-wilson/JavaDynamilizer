import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

public class Test{
  public static void main(final String[] args) throws NoSuchMethodException{
    Method main = Test.class.getDeclaredMethod("main", String[].class);

    for(Parameter parameter: main.getParameters()){
      System.out.println(parameter);
    }
  }
}
