package dynamilize.base;

import javax.xml.crypto.Data;

public interface IVariable{
  default void poolAdded(DataPool<?> pool){}

  String name();

  boolean isConst();

  <T> T get();

  void set(Object value);
}
