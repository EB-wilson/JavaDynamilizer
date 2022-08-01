import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.TreeSet;

@AnnoTest(value = {8778493766284947234L, 7876556374L})
public class Demo{
    Object[][][] arr;

    static int i;

    Class<?> typ = Demo.class;
    public String set(){
      Object iny = null;

      Object[] i = (Object[]) iny;
      typ = (Class<?>) iny;

      return Arrays.toString(i);
    }

  public static void main(String[] args){
    for(String s: sort(i + args[2], "opo", "oo")){
      System.out.println(s.hashCode());
    }
  }

  static String[] sort(String... strs){
    TreeSet<String> set = new TreeSet<>((a, b) -> a.hashCode() - b.hashCode());
    set.addAll(Arrays.asList(strs));
    int i=0;
    for(String str: set){
      strs[i] = str;
      i++;
    }

    return strs;
  }

  static float avg(int... ints){
    float sum = 0, s = 0;
    for(int anInt: ints){
      sum += anInt;
    }

    float avg = sum/ints.length;
    for(int anInt: ints){
      s += Math.pow(anInt - avg, 2);
    }

    return s/(ints.length - 1)/avg;
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
