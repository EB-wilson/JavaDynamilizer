package dynamilize;

public interface IVariable{
  default void poolAdded(DataPool<?> pool){}

  String name();

  boolean isConst();

  <T> T get();

  void set(Object value);
}
