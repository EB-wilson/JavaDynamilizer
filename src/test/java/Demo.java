import java.lang.annotation.*;

@AnnoTest(value = {8778493766284947234L, 7876556374L})
public class Demo{
    Object[][][] arr;

    public void set(){
      arr = new Object[1][3][6];
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
