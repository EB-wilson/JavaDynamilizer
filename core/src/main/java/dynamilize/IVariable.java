package dynamilize;

public interface IVariable{
  String name();

  void init(DynamicObject<?> object);
  <T> T get(DynamicObject<?> obj);

  void set(DynamicObject<?> obj, Object value);

  //primitive getters
  boolean get(DynamicObject<?> obj, boolean def);
  byte get(DynamicObject<?> obj, byte def);
  short get(DynamicObject<?> obj, short def);
  int get(DynamicObject<?> obj, int def);
  long get(DynamicObject<?> obj, long def);
  float get(DynamicObject<?> obj, float def);
  double get(DynamicObject<?> obj, double def);

  //primitive setters
  void set(DynamicObject<?> obj, boolean value);
  void set(DynamicObject<?> obj, byte value);
  void set(DynamicObject<?> obj, short value);
  void set(DynamicObject<?> obj, int value);
  void set(DynamicObject<?> obj, long value);
  void set(DynamicObject<?> obj, float value);
  void set(DynamicObject<?> obj, double value);
}
