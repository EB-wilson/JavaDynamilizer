package dynamilize.classmaker;

public class Member{
  private final String name;
  private int modifiers;

  public Member(String name){
    this.name = name;
  }

  public String name(){
    return name;
  }

  public int modifiers(){
    return modifiers;
  }

  public void setModifiers(int modifiers){
    this.modifiers = modifiers;
  }
}
