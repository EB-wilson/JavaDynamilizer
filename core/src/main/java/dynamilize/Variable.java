package dynamilize;

public class Variable implements IVariable{
  private final String name;

  public Variable(String name){
    this.name = name;
  }

  @Override
  public String name(){
    return name;
  }

  @Override
  public <T> T get(DynamicObject<?> obj){
    return obj.varValueGet(name);
  }

  @Override
  public void set(DynamicObject<?> obj, Object value){
    obj.varValueSet(name, value);
  }

  @Override
  public boolean get(DynamicObject<?> obj, boolean def) {
    Object b = get(obj);
    boolean res = def;
    if (b instanceof BooleanRef ref){
      res = ref.value;
    }
    else if (b != null)
      throw new IllegalHandleException("variable " + name + " in object " + obj + " was not a boolean");

    return res;
  }

  @Override
  public byte get(DynamicObject<?> obj, byte def) {
    Object b = get(obj);
    byte res = def;
    if (b instanceof ByteRef ref){
      res = ref.value;
    }
    else if (b != null)
      throw new IllegalHandleException("variable " + name + " in object " + obj + " was not a byte");

    return res;
  }

  @Override
  public short get(DynamicObject<?> obj, short def) {
    Object b = get(obj);
    short res = def;
    if (b instanceof ShortRef ref){
      res = ref.value;
    }
    else if (b != null)
      throw new IllegalHandleException("variable " + name + " in object " + obj + " was not a short");

    return res;
  }

  @Override
  public int get(DynamicObject<?> obj, int def) {
    Object b = get(obj);
    int res = def;
    if (b instanceof IntRef ref){
      res = ref.value;
    }
    else if (b != null)
      throw new IllegalHandleException("variable " + name + " in object " + obj + " was not a integer");

    return res;
  }

  @Override
  public long get(DynamicObject<?> obj, long def) {
    Object b = get(obj);
    long res = def;
    if (b instanceof LongRef ref){
      res = ref.value;
    }
    else if (b != null)
      throw new IllegalHandleException("variable " + name + " in object " + obj + " was not a long");

    return res;
  }

  @Override
  public float get(DynamicObject<?> obj, float def) {
    Object b = get(obj);
    float res = def;
    if (b instanceof FloatRef ref){
      res = ref.value;
    }
    else if (b != null)
      throw new IllegalHandleException("variable " + name + " in object " + obj + " was not a float");

    return res;
  }

  @Override
  public double get(DynamicObject<?> obj, double def) {
    Object b = get(obj);
    double res = def;
    if (b instanceof DoubleRef ref){
      res = ref.value;
    }
    else if (b != null)
      throw new IllegalHandleException("variable " + name + " in object " + obj + " was not a double");

    return res;
  }

  @Override
  public void set(DynamicObject<?> obj, boolean value) {
    if (get(obj) instanceof BooleanRef ref){
      ref.value = value;
    }
    else{
      BooleanRef ref = new BooleanRef();
      ref.value = value;
      set(obj, ref);
    }
  }

  @Override
  public void set(DynamicObject<?> obj, byte value) {
    if (get(obj) instanceof ByteRef ref){
      ref.value = value;
    }
    else{
      ByteRef ref = new ByteRef();
      ref.value = value;
      set(obj, ref);
    }
  }

  @Override
  public void set(DynamicObject<?> obj, short value) {
    if (get(obj) instanceof ShortRef ref){
      ref.value = value;
    }
    else{
      ShortRef ref = new ShortRef();
      ref.value = value;
      set(obj, ref);
    }
  }

  @Override
  public void set(DynamicObject<?> obj, int value) {
    if (get(obj) instanceof IntRef ref){
      ref.value = value;
    }
    else{
      IntRef ref = new IntRef();
      ref.value = value;
      set(obj, ref);
    }
  }

  @Override
  public void set(DynamicObject<?> obj, long value) {
    if (get(obj) instanceof LongRef ref){
      ref.value = value;
    }
    else{
      LongRef ref = new LongRef();
      ref.value = value;
      set(obj, ref);
    }
  }

  @Override
  public void set(DynamicObject<?> obj, float value) {
    if (get(obj) instanceof FloatRef ref){
      ref.value = value;
    }
    else{
      FloatRef ref = new FloatRef();
      ref.value = value;
      set(obj, ref);
    }
  }

  @Override
  public void set(DynamicObject<?> obj, double value) {
    if (get(obj) instanceof DoubleRef ref){
      ref.value = value;
    }
    else{
      DoubleRef ref = new DoubleRef();
      ref.value = value;
      set(obj, ref);
    }
  }

  private static class BooleanRef{ boolean value; }
  private static class ByteRef{ byte value; }
  private static class ShortRef{ Short value; }
  private static class IntRef{ int value; }
  private static class LongRef{ long value; }
  private static class FloatRef{ float value; }
  private static class DoubleRef{ double value; }
}
