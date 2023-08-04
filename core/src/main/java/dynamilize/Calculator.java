package dynamilize;

/**变量计算器，函数式接口，用于对变量的当前值进行一系列处理后提供回调
 *
 * @author EBwilson */
@FunctionalInterface
public interface Calculator<Type>{
  Type calculate(Type input);

  @FunctionalInterface interface BoolCalculator{ boolean calculate(boolean input); }
  @FunctionalInterface interface ByteCalculator{ byte calculate(byte input); }
  @FunctionalInterface interface ShortCalculator{ short calculate(short input); }
  @FunctionalInterface interface IntCalculator{ int calculate(int input); }
  @FunctionalInterface interface LongCalculator{ long calculate(long input); }
  @FunctionalInterface interface FloatCalculator{ float calculate(float input); }
  @FunctionalInterface interface DoubleCalculator{ double calculate(double input); }
}
