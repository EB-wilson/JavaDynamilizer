package dynamilize;

public interface IFunctionEntry{
  /**获取入口的名称*/
  String getName();

  /**获取此方法入口定义的引用匿名函数*/
  <S, R> Function<S, R> getFunction();

  /**获取此方法的形式参数表类型*/
  FunctionType getType();
}
