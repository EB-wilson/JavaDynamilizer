package dynamilize;

public class Initializer<T>{
  private final Producer<T> init;

  public Initializer(Producer<T> init){
    this.init = init;
  }

  public Object getInit(){
    return init.get();
  }

  interface Producer<T>{
    T get();


  }
}
