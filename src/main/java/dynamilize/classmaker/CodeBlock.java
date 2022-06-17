package dynamilize.classmaker;

import dynamilize.base.IllegalHandleException;
import dynamilize.classmaker.code.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CodeBlock<R> implements ICodeBlock{
  protected ArrayList<Code> statements = new ArrayList<>();

  protected ArrayList<Local<?>> parameter;

  @Override
  public List<Code> codes(){
    return statements;
  }

  @Override
  public int modifiers(){
    return 0;
  }

  @Override
  public void accept(CodeVisitor visitor){
    visitor.visitCodeBlock(this);
  }

  //*=============*//
  //* utilMethods *//
  //*=============*//
  private static final String VAR_DEFAULT = "var&";

  private int defVarCount = 0;

  public <T> Local<T> local(IClass<T> type, String name, int flags){
    Local<T> res = new Local<>(name, flags, type);
    codes().add(res);
    return res;
  }

  public <T> Local<T> local(IClass<T> type, int flags){
    return local(type, VAR_DEFAULT + defVarCount++, flags);
  }

  public <T> Local<T> local(IClass<T> type){
    return local(type, VAR_DEFAULT + defVarCount++, 0);
  }

  /**获取方法的参数的局部变量，索引为此参数在参数列表中的位置，对于非静态方法，索引0号位置为this指针
   *
   * @param index 此参数在形式参数列表中的位置，非静态方法的0索引处为this指针
   * @param <T> 参数的类型*/
  @SuppressWarnings("unchecked")
  public <T> Local<T> getParam(int index){
    return (Local<T>) parameter.get(index);
  }

  public <S, T extends S> void assignField(Local<?> target, FieldInfo<S> src, FieldInfo<T> tar){
    Local<S> srcTemp = local(src.type());
    assign(target, src, srcTemp);
    assign(target, srcTemp, tar);
  }

  public <S, T extends S> void assign(Local<S> src, Local<T> tar){
    codes().add(
        new LocalAssign<>(src, tar)
    );
  }

  public <S, T extends S> void assign(Local<?> tar, FieldInfo<S> src, Local<T> to){
    codes().add(
        new GetField<>(tar, src, to)
    );
  }

  public <S, T extends S> void assign(Local<?> tar, Local<S> src, FieldInfo<T> to){
    codes().add(
        new PutField<>(tar, src, to)
    );
  }

  public <S, T extends S> void assign(Local<?> tar, String src, Local<T> to){
    codes().add(
        new GetField<>(tar, tar.type().getField(to.type(), src), to)
    );
  }

  public <S> void assign(Local<?> tar, Local<S> src, String to){
    codes().add(
        new PutField<>(tar, src, tar.type().getField(src.type(), to))
    );
  }

  public <S, T extends S> void assignStatic(ClassInfo<?> clazz, String src, Local<T> to){
    codes().add(
        new GetField<>(null, clazz.getField(to.type(), src), to)
    );
  }

  public <S> void assignStatic(ClassInfo<?> clazz, Local<S> src, String to){
    codes().add(
        new PutField<>(null, src, clazz.getField(src.type(), to))
    );
  }

  public <Ret> void invoke(Local<?> target, MethodInfo<?, Ret> method, Local<Ret> returnTo, Local<?>... args){
    codes().add(
        new Invoke<>(target, method, returnTo, args)
    );
  }

  public <Ret> void invoke(Local<?> target, String method, Local<Ret> returnTo, Local<?>... args){
    codes().add(
        new Invoke<>(target, target.type().getMethod(returnTo.type(), method, Arrays.stream(args).map(Local::type).toArray(IClass<?>[]::new)), returnTo, args)
    );
  }

  public <Ret> void invokeStatic(ClassInfo<?> target, String method, Local<Ret> returnTo, Local<?>... args){
    codes().add(
        new Invoke<>(null, target.getMethod(returnTo.type(), method, Arrays.stream(args).map(Local::type).toArray(IClass<?>[]::new)), returnTo, args)
    );
  }

  public <T extends R> void returnValue(Local<T> local){
    codes().add(
        new Return<>(local)
    );
  }

  public void returnVoid(){
    codes().add(
        new Return<>(null)
    );
  }

  public <T> void operate(Local<T> lefOP, IOperate.OPCode opCode, Local<T> rigOP, Local<?> to){
    codes().add(
        new Operate<>(opCode, lefOP, rigOP, to)
    );
  }

  public <T> void operate(Local<T> lefOP, String symbol, Local<T> rigOP, Local<?> to){
    codes().add(
        new Operate<>(IOperate.OPCode.as(symbol), lefOP, rigOP, to)
    );
  }

  public void jump(Label label){
    codes().add(
        new Goto(label)
    );
  }

  public void condition(Local<Boolean> condition, Label ifJump){
    codes().add(
        new Condition(condition, ifJump)
    );
  }

  public <T> void compare(Local<T> lef, IOperate.OPCode opc, Local<T> rig, Label ifJump){
    if(!opc.resultType().equals(ClassInfo.BOOLEAN_TYPE))
      throw new IllegalHandleException("opcode " + opc + " result was not a boolean, require operator was a comparator");

    Local<Boolean> tmp = local(ClassInfo.BOOLEAN_TYPE);
    operate(lef, opc, rig, tmp);
    condition(tmp, ifJump);
  }

  public <T> void compare(Local<T> lef, String symbol, Local<T> rig, Label ifJump){
    compare(lef, IOperate.OPCode.as(symbol), rig, ifJump);
  }

  public void cast(Local<?> source, Local<?> target){
    codes().add(
        new Cast(source, target)
    );
  }

  public void instanceOf(Local<?> target, ClassInfo<?> type, Local<Boolean> result){
    codes().add(
        new InstanceOf(target, type, result)
    );
  }

  public <T> void newInstance(MethodInfo<T, Void> constructor, Local<? extends T> resultTo, Local<?>... params){
    codes().add(
        new NewInstance<>(constructor, resultTo, params)
    );
  }

  //*===============*//
  //*memberCodeTypes*//
  //*===============*//
  protected static class Local<T> implements ILocal<T>{
    String name;
    int modifiers;
    IClass<T> type;

    Object initial;

    public Local(String name, int modifiers, IClass<T> type){
      this.name = name;
      this.modifiers = modifiers;
      this.type = type;
    }

    @Override
    public String name(){
      return name;
    }

    @Override
    public int modifiers(){
      return modifiers;
    }

    @Override
    public IClass<T> type(){
      return type;
    }

    @Override
    public Object initial(){
      return initial;
    }
  }

  protected static class Operate<T> implements IOperate<T>{
    OPCode opc;

    ILocal<T> leftOP, rightOP;
    ILocal<?> result;

    public Operate(OPCode opc, ILocal<T> leftOP, ILocal<T> rightOP, ILocal<?> result){
      this.opc = opc;
      this.leftOP = leftOP;
      this.rightOP = rightOP;
      this.result = result;
    }

    @Override
    public OPCode opCode(){
      return opc;
    }

    @Override
    public ILocal<?> resultTo(){
      return result;
    }

    @Override
    public ILocal<T> leftOpNumber(){
      return leftOP;
    }

    @Override
    public ILocal<T> rightOpNumber(){
      return rightOP;
    }
  }

  protected static class LocalAssign<S, T extends S> implements ILocalAssign<S, T>{
    ILocal<S> src;
    ILocal<T> tar;

    public LocalAssign(ILocal<S> src, ILocal<T> tar){
      this.src = src;
      this.tar = tar;
    }

    @Override
    public ILocal<S> source(){
      return src;
    }

    @Override
    public ILocal<T> target(){
      return tar;
    }
  }

  protected static class PutField<S, T extends S> implements IPutField<S, T>{
    ILocal<?> inst;
    ILocal<S> source;
    IField<T> target;

    public PutField(ILocal<?> inst, ILocal<S> source, IField<T> target){
      this.inst = inst;
      this.source = source;
      this.target = target;
    }

    @Override
    public ILocal<?> inst(){
      return inst;
    }

    @Override
    public ILocal<S> source(){
      return source;
    }

    @Override
    public IField<T> target(){
      return target;
    }
  }

  protected static class GetField<S, T extends S> implements IGetField<S, T>{
    ILocal<?> inst;
    IField<S> source;
    ILocal<T> target;

    public GetField(ILocal<?> inst, IField<S> source, ILocal<T> target){
      this.inst = inst;
      this.source = source;
      this.target = target;
    }

    @Override
    public ILocal<?> inst(){
      return inst;
    }

    @Override
    public IField<S> source(){
      return source;
    }

    @Override
    public ILocal<T> target(){
      return target;
    }
  }

  protected static class Goto implements IGoto{
    ILabel label;

    public Goto(ILabel label){
      this.label = label;
    }

    @Override
    public ILabel target(){
      return label;
    }
  }

  protected static class Invoke<R> implements IInvoke<R>{
    IMethod<?, R> method;
    ILocal<? extends R> returnTo;
    List<ILocal<?>> args;
    ILocal<?> target;

    public Invoke(ILocal<?> target, IMethod<?, R> method, ILocal<? extends R> returnTo, ILocal<?>... args){
      this.method = method;
      this.returnTo = returnTo;
      this.target = target;
      this.args = List.of(args);
    }

    @Override
    public ILocal<?> target(){
      return target;
    }

    @Override
    public IMethod<?, R> method(){
      return method;
    }

    @Override
    public List<ILocal<?>> args(){
      return args;
    }

    @Override
    public ILocal<? extends R> returnTo(){
      return returnTo;
    }
  }

  protected static class Condition implements ICondition{
    ILocal<Boolean> condition;
    ILabel jumpTo;

    public Condition(ILocal<Boolean> condition, ILabel jumpTo){
      this.condition = condition;
      this.jumpTo = jumpTo;
    }

    @Override
    public ILocal<Boolean> condition(){
      return condition;
    }

    @Override
    public ILabel ifJump(){
      return jumpTo;
    }
  }

  protected static class Cast implements ICast{
    ILocal<?> src, tar;

    public Cast(ILocal<?> src, ILocal<?> tar){
      this.src = src;
      this.tar = tar;
    }

    @Override
    public ILocal<?> source(){
      return src;
    }

    @Override
    public ILocal<?> target(){
      return tar;
    }
  }

  protected static class Return<R> implements IReturn<R>{
    ILocal<R> local;

    public Return(ILocal<R> local){
      this.local = local;
    }

    @Override
    public ILocal<R> result(){
      return local;
    }
  }

  protected static class InstanceOf implements IInstanceOf{
    ILocal<?> target;
    IClass<?> type;
    ILocal<Boolean> result;

    public InstanceOf(ILocal<?> target, IClass<?> type, ILocal<Boolean> result){
      this.target = target;
      this.type = type;
      this.result = result;
    }

    @Override
    public ILocal<?> target(){
      return null;
    }

    @Override
    public IClass<?> type(){
      return null;
    }

    @Override
    public ILocal<Boolean> result(){
      return null;
    }
  }

  protected static class NewInstance<T> implements INewInstance<T>{
    IMethod<T, Void> constructor;
    ILocal<? extends T> resultTo;

    List<ILocal<?>> params;

    public NewInstance(IMethod<T, Void> constructor, ILocal<? extends T> resultTo, ILocal<?>... params){
      this.constructor = constructor;
      this.resultTo = resultTo;
      this.params = List.of(params);
    }

    @Override
    public IMethod<T, Void> constructor(){
      return constructor;
    }

    @Override
    public ILocal<? extends T> instanceTo(){
      return resultTo;
    }

    @Override
    public List<ILocal<?>> params(){
      return params;
    }
  }
}
