package dynamilize;

public class Variable implements IVariable{
  private final String name;
  private final boolean isConst;

  private Object value;

  public Variable(String name, boolean isConst){
    this.name = name;
    this.isConst = isConst;
  }

  @Override
  public String name(){
    return name;
  }

  @Override
  public boolean isConst(){
    return isConst;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> T get(){
    return (T) value;
  }

  @Override
  public void set(Object value){
    if(isConst)
      throw new IllegalHandleException("cannot modifier a const variable");

    this.value = value;
  }
}
