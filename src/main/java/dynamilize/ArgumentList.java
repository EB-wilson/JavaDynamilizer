package dynamilize;

import java.util.Stack;

/**实参列表的封装对象，记录了一份实际参数列表，提供了一个泛型获取参数的方法，用于减少函数引用时所需的冗余类型转换。
 * <p>参数表对象为可复用对象，引用完毕后请使用{@link ArgumentList#recycle(ArgumentList)}回收对象以减少堆内存更新频率
 *
 * @author EBwilson */
public class ArgumentList{
  public static final Object[][] argLenArray = new Object[32][];

  static {
    for(int i = 0; i < argLenArray.length; i++){
      argLenArray[i] = new Object[i];
    }
  }

  private static final int MAX_INSTANCE_STACK = 2048;

  private static final Stack<ArgumentList> INSTANCES = new Stack<>();

  private Object[] args;

  /**私有构造器，不允许外部直接使用*/
  private ArgumentList(){}

  /**使用一组实参列表获取一个封装参数列表，优先从实例堆栈中弹出，若堆栈中没有实例才会构造一个新的
   *
   * @param args 实参列表
   * @return 封装参数对象*/
  public static ArgumentList as(Object... args){
    ArgumentList res = INSTANCES.isEmpty()? new ArgumentList(): INSTANCES.pop();
    res.args = args;

    return res;
  }

  /**回收实例，使实例重新入栈，若堆栈已到达最大容量，则不会继续插入实例堆栈中
   *
   * @param list 封装参数对象*/
  public static void recycle(ArgumentList list){
    list.args = null;
    if(INSTANCES.size() >= MAX_INSTANCE_STACK) return;

    INSTANCES.add(list);
  }

  /**获取给定索引处的实参值，类型根据引用推断，若类型不可用会抛出类型转换异常
   *
   * @param index 参数在列表中的索引位置
   * @param <T> 参数类型，通常可以根据引用推断
   * @return 实参的值
   * @throws ClassCastException 若接受者所需的类型与参数类型不可分配*/
  @SuppressWarnings("unchecked")
  public <T> T get(int index){
    return (T) args[index];
  }

  /**获取实参列表的数组
   *
   * @return 实参构成的数组*/
  public Object[] args(){
    return args;
  }
}
