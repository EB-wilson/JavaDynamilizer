package dynamilize.classmaker;

import dynamilize.IllegalHandleException;
import dynamilize.classmaker.code.*;
import dynamilize.classmaker.code.annotation.AnnotatedElement;
import dynamilize.classmaker.code.annotation.IAnnotation;
import org.objectweb.asm.Label;
import org.objectweb.asm.*;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.util.*;

import static dynamilize.classmaker.ClassInfo.*;

/**基于ASM字节码操作框架实现的默认类型生成器
 *
 * <br><i>该类存在一个尚未修复的性能问题，即对部分可堆栈化的局部变量的优化</i>
 *
 * @author EBwilson */
public class ASMGenerator extends AbstractClassGenerator implements Opcodes{
  protected static final Map<Character, Map<Character, Integer>> CASTTABLE = new HashMap<>();

  static {
    HashMap<Character, Integer> map;
    map = new HashMap<>();
    CASTTABLE.put('I', map);
    map.put('I', Opcodes.I2L);
    map.put('F', Opcodes.I2F);
    map.put('D', Opcodes.I2D);
    map.put('B', Opcodes.I2B);
    map.put('C', Opcodes.I2C);
    map.put('S', Opcodes.I2S);
    map = new HashMap<>();
    CASTTABLE.put('L', map);
    map.put('I', Opcodes.L2I);
    map.put('F', Opcodes.L2F);
    map.put('D', Opcodes.L2D);
    map = new HashMap<>();
    CASTTABLE.put('F', map);
    map.put('I', Opcodes.F2I);
    map.put('L', Opcodes.F2L);
    map.put('D', Opcodes.F2D);
    map = new HashMap<>();
    CASTTABLE.put('D', map);
    map.put('I', Opcodes.D2I);
    map.put('L', Opcodes.D2L);
    map.put('F', Opcodes.D2F);
  }

  protected final ByteClassLoader classLoader;
  protected final int codeVersion;

  protected ClassWriter writer;

  protected MethodVisitor methodVisitor;
  protected FieldVisitor fieldVisitor;

  protected final Map<String, IField<?>> fieldMap = new HashMap<>();
  protected final Map<String, Object> staticInitial = new HashMap<>();

  protected final Map<String, Integer> localIndex = new HashMap<>();
  protected final Map<dynamilize.classmaker.code.Label, Label> labelMap = new HashMap<>();

  protected final Set<dynamilize.classmaker.code.Label> frameLabels = new HashSet<>();

  public ASMGenerator(ByteClassLoader classLoader, int codeVersion){
    this.classLoader = classLoader;
    this.codeVersion = codeVersion;
  }

  protected void initial(){
    writer = null;

    methodVisitor = null;
    fieldVisitor = null;
  }

  @Override
  public byte[] genByteCode(ClassInfo<?> classInfo){
    initial();
    writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);

    visitClass(classInfo);

    return writer.toByteArray();
  }

  @Override
  @SuppressWarnings("unchecked")
  protected <T> Class<T> generateClass(ClassInfo<T> classInfo) throws ClassNotFoundException{
    try{
      return (Class<T>) classLoader.loadClass(classInfo.name(), false);
    }catch(ClassNotFoundException e){
      classLoader.declareClass(classInfo.name(), genByteCode(classInfo));

      return (Class<T>) classLoader.loadClass(classInfo.name(), false);
    }
  }

  @Override
  public void visitClass(IClass<?> clazz){
    fieldMap.clear();
    staticInitial.clear();

    writer.visit(
        codeVersion,
        clazz.modifiers(),
        clazz.internalName(),
        null,
        clazz.superClass().internalName(),
        clazz.interfaces().stream().map(IClass::internalName).toArray(String[]::new)
    );

    visitAnnotation(clazz);

    IMethod<?, ?> clinit = null;
    for(Element element: clazz.elements()){
      //<clinit>块的处理最后进行
      if(element instanceof IMethod<?,?> method && method.name().equals("<clinit>")){
        clinit = method;
        continue;
      }
      element.accept(this);
    }

    //<clinit>初始化静态变量
    if(clinit != null){
      visitMethod(clinit);
    }

    writer.visitEnd();
  }

  @Override
  public void visitLocal(ILocal<?> local){
    super.visitLocal(local);
    localIndex.put(local.name(), localIndex.size());
    if(local.type() == LONG_TYPE || local.type() == DOUBLE_TYPE){
      localIndex.put(local.name() + "$high", localIndex.size());
    }
  }

  @Override
  public void visitMethod(IMethod<?, ?> method){
    String[] thrs = new String[method.throwTypes().size()];
    for (int i = 0; i < method.throwTypes().size(); i++) {
      thrs[i] = method.throwTypes().get(i).internalName();
    }

    methodVisitor = writer.visitMethod(
        method.modifiers(),
        method.name(),
        method.typeDescription(),
        null,
        thrs
    );
    visitAnnotation(method);

    localMap = new LinkedHashMap<>();

    labelMap.clear();
    frameLabels.clear();
    localIndex.clear();
    if(!Modifier.isAbstract(method.modifiers())){
      Label firstLine = new Label();
      methodVisitor.visitLabel(firstLine);

      for(ILocal<?> parameter: method.block().getParamAll()){
        localMap.put(parameter.name(), parameter);
        localIndex.put(parameter.name(), localIndex.size());
        if(parameter.type() == LONG_TYPE || parameter.type() == DOUBLE_TYPE){
          localIndex.put(parameter.name() + "$high", localIndex.size());
        }
      }

      super.visitMethod(method);

      Label endLine = new Label();
      methodVisitor.visitLabel(endLine);

      for(Map.Entry<String, ILocal<?>> entry: localMap.entrySet()){
        methodVisitor.visitLocalVariable(
            entry.getKey(),
            entry.getValue().type().realName(),
            null,
            firstLine,
            endLine,
            localIndex.get(entry.getValue().name())
        );
      }

      methodVisitor.visitMaxs(0, 0);//已设置自动计算模式，参数无意义
    }

    for(Parameter<?> parameter: method.parameters()){
      visitAnnotation(parameter);
    }

    //在处理clinit块时，初始化静态变量默认值
    if(method.name().equals("<clinit>")){
      for(Map.Entry<String, Object> entry: staticInitial.entrySet()){
        visitConstant(entry.getValue());

        IField<?> field = fieldMap.get(entry.getKey());
        methodVisitor.visitFieldInsn(
            Opcodes.PUTSTATIC,
            field.owner().internalName(),
            field.name(),
            field.type().realName());
      }
    }

    methodVisitor.visitEnd();
  }

  @Override
  public void visitCodeBlock(ICodeBlock<?> block){
    for(dynamilize.classmaker.code.Label label: block.labelList()){
      labelMap.put(label, new Label());
    }

    //if(block.owner().name().equals("<init>") && (!(block.codes().get(0) instanceof IInvoke<?>)
    //|| !((IInvoke<?>)block.codes().get(0)).method().name().equals("<init>"))){
    //  block.owner().owner().superClass().getConstructor();
    //
    //  methodVisitor.visitMethodInsn(
    //      INVOKESPECIAL,
    //      block.owner().owner().superClass().internalName(),
    //      "<init>",
    //      "()V",
    //      false
    //  );
    //}

    currCodeBlock = block;
    for(Element element: block.codes()){
      element.accept(this);
    }

    Element end = block.codes().isEmpty()? null: block.codes().get(block.codes().size() - 1);
    if(end != null && end.kind() == ElementKind.RETURN){
      IReturn<?> ret = (IReturn<?>) end;
      if((ret.returnValue() == null && block.owner().returnType().equals(ClassInfo.VOID_TYPE))
      || (block.owner().returnType().isAssignableFrom(ret.returnValue().type()))) return;
    }
    else if(end != null && end.kind() == ElementKind.THROW) return;

    if(block.owner().returnType() == ClassInfo.VOID_TYPE){
      methodVisitor.visitInsn(Opcodes.RETURN);
    }
    else throw new IllegalHandleException("method return a non-null value, but the method is not returning correctly");
  }

  @Override
  public void visitField(IField<?> field){
    super.visitField(field);

    fieldVisitor = writer.visitField(
        field.modifiers(),
        field.name(),
        field.type().realName(),
        null,
        field.initial() instanceof Number || field.initial() instanceof String? field.initial(): null
    );

    fieldMap.put(field.name(), field);
    if(field.initial() instanceof Array || field.initial() instanceof Enum<?>)
      staticInitial.put(field.name(), field.initial());

    visitAnnotation(field);
    fieldVisitor.visitEnd();
  }

  @Override
  public void visitInvoke(IInvoke<?> invoke){
    int invokeType = Modifier.isStatic(invoke.method().modifiers())? Opcodes.INVOKESTATIC:
        Modifier.isInterface(invoke.method().owner().modifiers())? Opcodes.INVOKEINTERFACE:
        invoke.method().name().equals("<init>") || invoke.callSuper()? Opcodes.INVOKESPECIAL:
        Opcodes.INVOKEVIRTUAL;
    
    if(!Modifier.isStatic(invoke.method().modifiers())){
      if(!(invoke.target() instanceof CodeBlock.StackElem)){
        methodVisitor.visitVarInsn(Opcodes.ALOAD, localIndex.get(invoke.target().name()));
      }
    }

    if(!invoke.args().isEmpty() && !(invoke.args().get(0) instanceof CodeBlock.StackElem)){
     for(ILocal<?> arg: invoke.args()){
        methodVisitor.visitVarInsn(
            getLoadType(arg.type()),
            localIndex.get(arg.name())
        );
      }
    }

    methodVisitor.visitMethodInsn(
        invokeType,
        invoke.method().owner().internalName(),
        invoke.method().name(),
        invoke.method().typeDescription(),
        Modifier.isInterface(invoke.method().owner().modifiers())
    );

    IClass<?> type = invoke.method().returnType();

    if(invoke.returnTo() == null){
      if(invoke.method().returnType() != ClassInfo.VOID_TYPE){
        methodVisitor.visitInsn(Opcodes.POP);
      }
    }
    else{
      castAssign(type, invoke.returnTo().type());

      if(invoke.returnTo() instanceof CodeBlock.StackElem) return;
      methodVisitor.visitVarInsn(
          getStoreType(invoke.returnTo().type()),
          localIndex.get(invoke.returnTo().name())
      );
    }
  }

  @Override
  public void visitGetField(IGetField<?, ?> getField){
    if(!Modifier.isStatic(getField.source().modifiers())){
      if(!(getField.inst() instanceof CodeBlock.StackElem)){
        methodVisitor.visitVarInsn(Opcodes.ALOAD, localIndex.get(getField.inst().name()));
      }
    }

    methodVisitor.visitFieldInsn(
        Modifier.isStatic(getField.source().modifiers())? Opcodes.GETSTATIC: Opcodes.GETFIELD,
        getField.source().owner().internalName(),
        getField.source().name(),
        getField.source().type().realName()
    );

    castAssign(getField.source().type(), getField.target().type());

    if(!(getField.target() instanceof CodeBlock.StackElem)){
      methodVisitor.visitVarInsn(
          getStoreType(getField.source().type()),
          localIndex.get(getField.target().name())
      );
    }
  }

  @Override
  public void visitPutField(IPutField<?, ?> putField){
    if(!Modifier.isStatic(putField.target().modifiers())){
      if(!(putField.inst() instanceof CodeBlock.StackElem)){
        methodVisitor.visitVarInsn(Opcodes.ALOAD, localIndex.get(putField.inst().name()));
      }
    }

    if(!(putField.source() instanceof CodeBlock.StackElem)){
      methodVisitor.visitVarInsn(
          getLoadType(putField.source().type()),
          localIndex.get(putField.source().name())
      );
    }

    castAssign(putField.source().type(), putField.target().type());

    methodVisitor.visitFieldInsn(
        Modifier.isStatic(putField.target().modifiers())? Opcodes.PUTSTATIC: Opcodes.PUTFIELD,
        putField.target().owner().internalName(),
        putField.target().name(),
        putField.target().type().realName()
    );
  }

  @Override
  public void visitLocalSet(ILocalAssign<?, ?> localSet){
    if(!(localSet.source() instanceof CodeBlock.StackElem)){
      methodVisitor.visitVarInsn(
          getLoadType(localSet.source().type()),
          localIndex.get(localSet.source().name())
      );
    }
    else if (localSet.target() instanceof CodeBlock.StackElem){
      methodVisitor.visitInsn(Opcodes.DUP);
      return;
    }

    castAssign(localSet.source().type(), localSet.target().type());
    if (localSet.target() instanceof CodeBlock.StackElem) return;

    methodVisitor.visitVarInsn(
        getStoreType(localSet.target().type()),
        localIndex.get(localSet.target().name())
    );
  }

  @Override
  public void visitOperate(IOperate<?> operate){
    if(!(operate.leftOpNumber() instanceof CodeBlock.StackElem)){
      methodVisitor.visitVarInsn(
          getLoadType(operate.leftOpNumber().type()),
          localIndex.get(operate.leftOpNumber().name())
      );
    }

    if(!(operate.rightOpNumber() instanceof CodeBlock.StackElem)){
      methodVisitor.visitVarInsn(
          getLoadType(operate.rightOpNumber().type()),
          localIndex.get(operate.rightOpNumber().name())
      );
    }

    if(operate.leftOpNumber().type() == STRING_TYPE || operate.rightOpNumber().type() == STRING_TYPE){
      if(operate.opCode() != IOperate.OPCode.ADD)
        throw new IllegalHandleException("operate on string must only add");

      methodVisitor.visitInvokeDynamicInsn(
          "makeConcatWithConstants",
          "("  + operate.leftOpNumber().type().realName() + operate.rightOpNumber().type().realName() + ")Ljava/lang/String;",
          new Handle(
              Opcodes.H_INVOKESTATIC, "java/lang/invoke/StringConcatFactory",
              "makeConcatWithConstants",
              "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
              false),
          "\u0001\u0001"
      );
    }
    else{
      IClass<?> type = operate.leftOpNumber().type();
      int ilfd = getILFD(type);

      int opc = switch(operate.opCode()){
        case ADD -> selectILFD(ilfd, Opcodes.IADD, Opcodes.LADD, Opcodes.FADD, Opcodes.DADD);
        case SUBSTRUCTION -> selectILFD(ilfd, Opcodes.ISUB, Opcodes.LSUB, Opcodes.FSUB, Opcodes.DSUB);
        case MULTI -> selectILFD(ilfd, Opcodes.IMUL, Opcodes.LMUL, Opcodes.FMUL, Opcodes.DMUL);
        case DIVISION -> selectILFD(ilfd, Opcodes.IDIV, Opcodes.LDIV, Opcodes.FDIV, Opcodes.DDIV);
        case REMAINING -> selectILFD(ilfd, Opcodes.IREM, Opcodes.LREM, Opcodes.FREM, Opcodes.DREM);
        case LEFTMOVE -> selectIL(ilfd, Opcodes.ISHL, Opcodes.LSHL);
        case RIGHTMOVE -> selectIL(ilfd, Opcodes.ISHR, Opcodes.LSHR);
        case UNSIGNMOVE -> selectIL(ilfd, Opcodes.IUSHR, Opcodes.LUSHR);
        case BITSAME -> selectIL(ilfd, Opcodes.IAND, Opcodes.LAND);
        case BITOR -> selectIL(ilfd, Opcodes.IOR, Opcodes.LOR);
        case BITXOR -> selectIL(ilfd, Opcodes.IXOR, Opcodes.LXOR);
      };

      methodVisitor.visitInsn(opc);
    }

    if(operate.resultTo() instanceof CodeBlock.StackElem) return;
    methodVisitor.visitVarInsn(
        getStoreType(operate.resultTo().type()),
        localIndex.get(operate.resultTo().name())
    );
  }

  private int selectIL(int ilfd, int ishl, int lshl){
    return switch(ilfd){
      case 1 -> ishl;
      case 2 -> lshl;
      default -> throw new IllegalStateException("Unexpected value: " + ilfd);
    };
  }

  private int selectILFD(int ilfd, int iadd, int ladd, int fadd, int dadd){
    return switch(ilfd){
      case 1 -> iadd;
      case 2 -> ladd;
      case 3 -> fadd;
      case 4 -> dadd;
      default -> throw new IllegalStateException("Unexpected value: " + ilfd);
    };
  }

  private int getILFD(IClass<?> type){
    return type == ClassInfo.INT_TYPE || type == ClassInfo.BYTE_TYPE
            || type == ClassInfo.SHORT_TYPE || type == ClassInfo.BOOLEAN_TYPE
            || type == ClassInfo.CHAR_TYPE? 1:
        type == ClassInfo.LONG_TYPE? 2:
        type == ClassInfo.FLOAT_TYPE? 3:
        type == ClassInfo.DOUBLE_TYPE? 4: -1;
  }

  @Override
  public void visitCast(ICast cast){
    if(!(cast.source() instanceof CodeBlock.StackElem)){
      methodVisitor.visitVarInsn(
          getLoadType(cast.source().type()),
          localIndex.get(cast.source().name())
      );
    }

    castAssign(cast.source().type(), cast.target().type());

    if(cast.target() instanceof CodeBlock.StackElem) return;
    methodVisitor.visitVarInsn(
        getStoreType(cast.target().type()),
        localIndex.get(cast.target().name())
    );
  }

  @Override
  public void visitGoto(IGoto iGoto){
    methodVisitor.visitJumpInsn(Opcodes.GOTO, labelMap.get(iGoto.target()));
  }
  
  @Override
  public void visitLabel(IMarkLabel label){
    methodVisitor.visitLabel(labelMap.get(label.label()));
  }

  @Override
  public void visitCompare(ICompare<?> compare){
    int opc = switch(compare.comparison()){
      case EQUAL -> Opcodes.IF_ACMPEQ;
      case UNEQUAL -> Opcodes.IF_ACMPNE;
      case LESS -> Opcodes.IF_ICMPLT;
      case LESSOREQUAL -> Opcodes.IF_ICMPLE;
      case MORE -> Opcodes.IF_ICMPGT;
      case MOREOREQUAL -> Opcodes.IF_ICMPGE;
    };

    if(!(compare.leftNumber() instanceof CodeBlock.StackElem)){
      methodVisitor.visitVarInsn(
          getLoadType(compare.leftNumber().type()),
          localIndex.get(compare.leftNumber().name())
      );
    }

    if(!(compare.rightNumber() instanceof CodeBlock.StackElem)){
      methodVisitor.visitVarInsn(
          getLoadType(compare.rightNumber().type()),
          localIndex.get(compare.rightNumber().name())
      );
    }

    methodVisitor.visitJumpInsn(opc, labelMap.get(compare.ifJump()));
  }

  @Override
  public void visitCondition(ICondition condition){
    int opc = switch(condition.condCode()){
      case EQUAL -> Opcodes.IFEQ;
      case UNEQUAL -> Opcodes.IFNE;
      case LESS -> Opcodes.IFLT;
      case LESSOREQUAL -> Opcodes.IFLE;
      case MORE -> Opcodes.IFGT;
      case MOREOREQUAL -> Opcodes.IFGE;
    };

    if(!(condition.condition() instanceof CodeBlock.StackElem)){
      methodVisitor.visitVarInsn(
          getLoadType(condition.condition().type()),
          localIndex.get(condition.condition().name())
      );
    }

    methodVisitor.visitJumpInsn(opc, labelMap.get(condition.ifJump()));
  }

  @Override
  public void visitArrayGet(IArrayGet<?> arrayGet){
    if(!(arrayGet.array() instanceof CodeBlock.StackElem)){
      methodVisitor.visitVarInsn(
          Opcodes.ALOAD,
          localIndex.get(arrayGet.array().name())
      );
    }

    IClass<?> componentType = arrayGet.array().type().componentType();

    int loadType;
    if(componentType == ClassInfo.INT_TYPE){loadType = Opcodes.IALOAD;}
    else if(componentType == ClassInfo.FLOAT_TYPE){loadType = Opcodes.FALOAD;}
    else if(componentType == ClassInfo.LONG_TYPE){loadType = Opcodes.LALOAD;}
    else if(componentType == ClassInfo.DOUBLE_TYPE){loadType = Opcodes.DALOAD;}
    else if(componentType == ClassInfo.BYTE_TYPE){loadType = Opcodes.BALOAD;}
    else if(componentType == ClassInfo.SHORT_TYPE){loadType = Opcodes.SALOAD;}
    else if(componentType == ClassInfo.BOOLEAN_TYPE){loadType = Opcodes.BALOAD;}
    else if(componentType == ClassInfo.CHAR_TYPE){loadType = Opcodes.CALOAD;}
    else {loadType = Opcodes.AALOAD;}

    if(!(arrayGet.index() instanceof CodeBlock.StackElem)){
      methodVisitor.visitVarInsn(
          Opcodes.ILOAD,
          localIndex.get(arrayGet.index().name())
      );
    }

    methodVisitor.visitInsn(loadType);

    castAssign(arrayGet.array().type().componentType(), arrayGet.getTo().type());

    if(arrayGet.getTo() instanceof CodeBlock.StackElem) return;
    methodVisitor.visitVarInsn(
        getStoreType(componentType),
        localIndex.get(arrayGet.getTo().name())
    );
  }

  @Override
  public void visitArrayPut(IArrayPut<?> arrayPut){
    if(!(arrayPut.array() instanceof CodeBlock.StackElem)){
      methodVisitor.visitVarInsn(
          Opcodes.ALOAD,
          localIndex.get(arrayPut.array().name())
      );
    }

    if(!(arrayPut.index() instanceof CodeBlock.StackElem)){
      methodVisitor.visitVarInsn(
          Opcodes.ILOAD,
          localIndex.get(arrayPut.index().name())
      );
    }

    IClass<?> componentType = arrayPut.array().type().componentType();

    int storeType;
    if(componentType == ClassInfo.INT_TYPE){storeType = Opcodes.IASTORE;}
    else if(componentType == ClassInfo.FLOAT_TYPE){storeType = Opcodes.FASTORE;}
    else if(componentType == ClassInfo.LONG_TYPE){storeType = Opcodes.LASTORE;}
    else if(componentType == ClassInfo.DOUBLE_TYPE){storeType = Opcodes.DASTORE;}
    else if(componentType == ClassInfo.BYTE_TYPE){storeType = Opcodes.BASTORE;}
    else if(componentType == ClassInfo.SHORT_TYPE){storeType = Opcodes.SASTORE;}
    else if(componentType == ClassInfo.BOOLEAN_TYPE){storeType = Opcodes.BASTORE;}
    else if(componentType == ClassInfo.CHAR_TYPE){storeType = Opcodes.CASTORE;}
    else {storeType = Opcodes.AASTORE;}

    if(!(arrayPut.value() instanceof CodeBlock.StackElem)){
      methodVisitor.visitVarInsn(
          getLoadType(arrayPut.value().type()),
          localIndex.get(arrayPut.value().name())
      );
    }

    castAssign(arrayPut.value().type(), arrayPut.array().type().componentType());

    methodVisitor.visitInsn(storeType);
  }

  @Override
  public void visitSwitch(ISwitch<?> zwitch){
    if(!(zwitch.target() instanceof CodeBlock.StackElem)){
      methodVisitor.visitVarInsn(
          getLoadType(zwitch.target().type()),
          localIndex.get(zwitch.target().name())
      );
    }

    if(zwitch.isTable()){
      int max = Integer.MIN_VALUE, min = Integer.MAX_VALUE;
      for(Map.Entry<?, dynamilize.classmaker.code.Label> entry: zwitch.cases().entrySet()){
        int i;
        if(entry.getKey() instanceof Number n){
          i = n.intValue();
        }
        else if(entry.getKey() instanceof Character c){
          i = c;
        }
        else throw new IllegalHandleException("unsupported type");

        min = Math.min(min, i);
        max = Math.max(max, i);
      }

      Label[] labels = new Label[max - min + 1];
      int off = -min;
      for(int i = min; i <= max; i++){
        dynamilize.classmaker.code.Label l = zwitch.cases().get(i);

        labels[i + off] = labelMap.get(l == null? zwitch.end(): l);
      }

      methodVisitor.visitTableSwitchInsn(
          min,
          max,
          labelMap.get(zwitch.end()),
          labels
      );
    }
    else{
      int[] keys = new int[zwitch.cases().size()];
      Label[] labels = new Label[zwitch.cases().size()];

      int i = 0;
      for(Map.Entry<?, dynamilize.classmaker.code.Label> entry: zwitch.cases().entrySet()){
        keys[i] = entry.getKey().hashCode();
        labels[i] = labelMap.get(entry.getValue());
        i++;
      }

      if(!zwitch.target().type().isPrimitive()){
        methodVisitor.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "java/lang/Objects",
            "hashCode",
            "(Ljava/lang/Object;)I",
            false
        );
      }
      methodVisitor.visitLookupSwitchInsn(labelMap.get(zwitch.end()), keys, labels);
    }
  }

  @Override
  public void visitThrow(IThrow<?> thr){
    if(!(thr.thr() instanceof CodeBlock.StackElem)){
      methodVisitor.visitVarInsn(
          getLoadType(thr.thr().type()),
          localIndex.get(thr.thr().name())
      );
    }

    methodVisitor.visitInsn(Opcodes.ATHROW);
  }

  @Override
  public void visitReturn(IReturn<?> iReturn){
    if(iReturn.returnValue() == null){
      methodVisitor.visitInsn(Opcodes.RETURN);
    }
    else{
      IClass<?> retType = iReturn.returnValue().type();
      int opc = switch(getTypeChar(retType, true)){
        case 'I' -> Opcodes.IRETURN;
        case 'L' -> Opcodes.LRETURN;
        case 'F' -> Opcodes.FRETURN;
        case 'D' -> Opcodes.DRETURN;
        case 'A' -> Opcodes.ARETURN;
        default -> -1;
      };

      if(!(iReturn.returnValue() instanceof CodeBlock.StackElem)){
        methodVisitor.visitVarInsn(
            getLoadType(retType),
            localIndex.get(iReturn.returnValue().name())
        );
      }

      castAssign(iReturn.returnValue().type(), currMethod.returnType());

      methodVisitor.visitInsn(opc);
    }
  }

  @Override
  public void visitInstanceOf(IInstanceOf instanceOf){
    if(!(instanceOf.target() instanceof CodeBlock.StackElem)){
      methodVisitor.visitVarInsn(
          getLoadType(instanceOf.target().type()),
          localIndex.get(instanceOf.target().name())
      );
    }

    methodVisitor.visitTypeInsn(
        Opcodes.INSTANCEOF,
        instanceOf.type().internalName()
    );

    if(instanceOf.result() instanceof CodeBlock.StackElem) return;
    methodVisitor.visitVarInsn(
        getStoreType(instanceOf.result().type()),
        localIndex.get(instanceOf.result().name())
    );
  }

  @Override
  public void visitNewInstance(INewInstance<?> newInstance){
    methodVisitor.visitTypeInsn(Opcodes.NEW, newInstance.type().internalName());
    methodVisitor.visitInsn(Opcodes.DUP);

    if(!newInstance.params().isEmpty() && !(newInstance.params().get(0) instanceof CodeBlock.StackElem)){
     for(ILocal<?> local: newInstance.params()){
        methodVisitor.visitVarInsn(
            getLoadType(local.type()),
            localIndex.get(local.name())
        );
      }
    }

    methodVisitor.visitMethodInsn(
        Opcodes.INVOKESPECIAL,
        newInstance.type().internalName(),
        newInstance.constructor().name(),
        newInstance.constructor().typeDescription(),
        false
    );

    if(newInstance.instanceTo() instanceof CodeBlock.StackElem) return;
    methodVisitor.visitVarInsn(
        getStoreType(newInstance.type()),
        localIndex.get(newInstance.instanceTo().name())
    );
  }

  @Override
  public void visitOddOperate(IOddOperate<?> operate){
    if(!(operate.operateNumber() instanceof CodeBlock.StackElem)){
      methodVisitor.visitVarInsn(
          getLoadType(operate.operateNumber().type()),
          localIndex.get(operate.operateNumber().name())
      );
    }

    IClass<?> type = operate.operateNumber().type();
    int ilfd = getILFD(type);

    int opc = switch(operate.opCode()){
      case NEGATIVE -> selectILFD(ilfd, Opcodes.INEG, Opcodes.LNEG, Opcodes.FNEG, Opcodes.DNEG);
      case BITNOR -> selectIL(ilfd, Opcodes.IXOR, Opcodes.LXOR);
    };

    if(opc == Opcodes.IXOR){
      methodVisitor.visitInsn(Opcodes.ICONST_M1);
      methodVisitor.visitInsn(Opcodes.IXOR);
    }
    else if(opc == Opcodes.LXOR){
      methodVisitor.visitLdcInsn(-1L);
      methodVisitor.visitInsn(Opcodes.LXOR);
    }
    else{
      methodVisitor.visitInsn(opc);
    }

    if(operate.resultTo() instanceof CodeBlock.StackElem) return;
    methodVisitor.visitVarInsn(
        Opcodes.ISTORE,
        localIndex.get(operate.resultTo().name())
    );
  }

  @Override
  public void visitConstant(ILoadConstant<?> loadConstant){
    visitConstant(loadConstant.constant());

    if(loadConstant.constTo() instanceof CodeBlock.StackElem) return;
    methodVisitor.visitVarInsn(
        getStoreType(loadConstant.constTo().type()),
        localIndex.get(loadConstant.constTo().name())
    );
  }

  @Override
  public void visitNewArray(INewArray<?> newArray){
    int dimension = newArray.arrayLength().size();
    if(dimension == 1){
      ILocal<?> len = newArray.arrayLength().get(0);
      if(!(len instanceof CodeBlock.StackElem)){
        methodVisitor.visitVarInsn(
            getLoadType(len.type()),
            localIndex.get(len.name())
        );
      }

      methodVisitor.visitTypeInsn(
          newArray.arrayEleType().isPrimitive()? Opcodes.NEWARRAY: Opcodes.ANEWARRAY,
          newArray.arrayEleType().internalName()
      );
    }
    else if(dimension > 1){
      IClass<?> arrType = newArray.arrayEleType();
      if(!(newArray.arrayLength().get(0) instanceof CodeBlock.StackElem)){
        for(int i = 0; i < dimension; i++){
          arrType = arrType.asArray();

          ILocal<?> len = newArray.arrayLength().get(i);
          methodVisitor.visitVarInsn(
              getLoadType(len.type()),
              localIndex.get(len.name())
          );
        }
      }

      methodVisitor.visitMultiANewArrayInsn(arrType.realName(), dimension);
    }
    else throw new IllegalHandleException("illegal array dimension " + dimension);

    if(newArray.resultTo() instanceof CodeBlock.StackElem) return;
    methodVisitor.visitVarInsn(
        Opcodes.ASTORE,
        localIndex.get(newArray.resultTo().name())
    );
  }

  protected void visitAnnotation(AnnotatedElement element){
    AnnotationVisitor annoVisitor;

    for(IAnnotation<?> annotation: element.getAnnotations()){
      IClass<?> annoType = annotation.annotationType().typeClass();
      Retention retention = annoType.getAnnotation(Retention.class);
      if(retention != null && retention.value() == RetentionPolicy.SOURCE) continue;

      boolean ret = retention != null && retention.value() == RetentionPolicy.RUNTIME;

      annoVisitor = element.isType(ElementType.TYPE) || element.isType(ElementType.ANNOTATION_TYPE)? writer.visitAnnotation(
          annoType.realName(), ret
      ): element.isType(ElementType.FIELD)? fieldVisitor.visitAnnotation(
          annoType.realName(), ret
      ): element.isType(ElementType.METHOD) || element.isType(ElementType.CONSTRUCTOR)? methodVisitor.visitAnnotation(
          annoType.realName(), ret
      ): element.isType(ElementType.PARAMETER) && element instanceof Parameter<?>? methodVisitor.visitParameterAnnotation(
          localIndex.get(((Parameter<?>)element).name()),
          annoType.realName(),
          ret
      ): null;

      if(annoVisitor == null)
        throw new IllegalHandleException("unknown annotation retention type");

      for(Map.Entry<String, Object> entry: annotation.pairs().entrySet()){
        Object value = entry.getValue();
        if(value.getClass().isEnum()){
          annoVisitor.visitEnum(
              entry.getKey(),
              ClassInfo.asType(value.getClass()).realName(),
              ((Enum<?>) value).name()
          );
        }
        else annoVisitor.visit(entry.getKey(), value);
      }

      annoVisitor.visitEnd();
    }
  }

  protected int getStoreType(IClass<?> type){
    return typeSelect(type, Opcodes.ASTORE, Opcodes.ISTORE, Opcodes.LSTORE, Opcodes.DSTORE, Opcodes.FSTORE);
  }

  protected int getLoadType(IClass<?> type){
    return typeSelect(type, Opcodes.ALOAD, Opcodes.ILOAD, Opcodes.LLOAD, Opcodes.DLOAD, Opcodes.FLOAD);
  }

  private static int typeSelect(IClass<?> type, int aload, int iload, int lload, int dload, int fload){
    return !type.isPrimitive()? aload :
        type == ClassInfo.INT_TYPE || type == ClassInfo.BYTE_TYPE
            || type == ClassInfo.SHORT_TYPE || type == ClassInfo.BOOLEAN_TYPE
            || type == ClassInfo.CHAR_TYPE? iload :
        type == ClassInfo.LONG_TYPE? lload :
        type == ClassInfo.DOUBLE_TYPE? dload :
        type == ClassInfo.FLOAT_TYPE? fload : -1;
  }

  protected void visitConstant(Object value){
    if(value == null){
      methodVisitor.visitInsn(Opcodes.ACONST_NULL);
    }
    else if(value instanceof Class c){
      if(c.isPrimitive()){
        IClass<?> type;
        type = c == int.class? ClassInfo.asType(Integer.class):
        c == float.class? ClassInfo.asType(Float.class):
        c == long.class? ClassInfo.asType(Long.class):
        c == double.class? ClassInfo.asType(Double.class):
        c == byte.class? ClassInfo.asType(Byte.class):
        c == short.class? ClassInfo.asType(Short.class):
        c == boolean.class? ClassInfo.asType(Boolean.class):
        c == char.class? ClassInfo.asType(Character.class): null;

        if(type == null)
          throw new IllegalHandleException("how do you run to this?");

        methodVisitor.visitFieldInsn(
            Opcodes.GETSTATIC,
            type.internalName(),
            "TYPE",
            CLASS_TYPE.realName()
        );
      }
      else methodVisitor.visitLdcInsn(Type.getType(ClassInfo.asType((Class<?>) value).realName()));
    }
    else if(value.getClass().isArray()){
      Class<?> componentType = value.getClass().getComponentType();

      if(componentType.isPrimitive() || componentType == String.class || componentType.isEnum() || componentType.isArray()){
        int opc;
        int storeType;
        if(componentType == int.class){opc = Opcodes.T_INT; storeType = Opcodes.IASTORE;}
        else if(componentType == float.class){opc = Opcodes.T_FLOAT; storeType = Opcodes.FASTORE;}
        else if(componentType == long.class){opc = Opcodes.T_LONG; storeType = Opcodes.LASTORE;}
        else if(componentType == double.class){opc = Opcodes.T_DOUBLE; storeType = Opcodes.DASTORE;}
        else if(componentType == byte.class){opc = Opcodes.T_BYTE; storeType = Opcodes.BASTORE;}
        else if(componentType == short.class){opc = Opcodes.T_SHORT; storeType = Opcodes.SASTORE;}
        else if(componentType == boolean.class){opc = Opcodes.T_BOOLEAN; storeType = Opcodes.BASTORE;}
        else if(componentType == char.class){opc = Opcodes.T_CHAR; storeType = Opcodes.CASTORE;}
        else {opc = 0; storeType = Opcodes.AASTORE;}

        int len = Array.getLength(value);
        visitConstant(len);

        if(componentType.isPrimitive()){
          methodVisitor.visitIntInsn(Opcodes.NEWARRAY, opc);
        }
        else methodVisitor.visitTypeInsn(Opcodes.ANEWARRAY, ClassInfo.asType(value.getClass()).internalName());

        for(int i = 0; i < len; i++){
          methodVisitor.visitInsn(Opcodes.DUP);
          visitConstant(i);
          visitConstant(Array.get(value, i));

          methodVisitor.visitInsn(storeType);
        }
      }
    }
    else if(value instanceof String || value instanceof Float
    || value instanceof Long || value instanceof Double){
      if(value instanceof Long){
        long l = (long) value;
        if(l == 0){methodVisitor.visitInsn(Opcodes.LCONST_0); return;}
        else if(l == 1){methodVisitor.visitInsn(Opcodes.LCONST_1); return;}
      }
      else if(value instanceof Float){
        float f = (float) value;
        if(f == 0){methodVisitor.visitInsn(Opcodes.FCONST_0); return;}
        else if(f == 1){methodVisitor.visitInsn(Opcodes.FCONST_1); return;}
        else if(f == 2){methodVisitor.visitInsn(Opcodes.FCONST_2); return;}
      }
      else if(value instanceof Double){
        double d = (double) value;
        if(d == 0){methodVisitor.visitInsn(Opcodes.DCONST_0); return;}
        else if(d == 1){methodVisitor.visitInsn(Opcodes.DCONST_1); return;}
      }

      methodVisitor.visitLdcInsn(value);
    }
    else if(value instanceof Integer || value instanceof Byte
    || value instanceof Short || value instanceof Character){
      int v = (int) value;
      switch(v){
        case 0 -> {
          methodVisitor.visitInsn(Opcodes.ICONST_0);
          return;
        }
        case 1 -> {
          methodVisitor.visitInsn(Opcodes.ICONST_1);
          return;
        }
        case 2 -> {
          methodVisitor.visitInsn(Opcodes.ICONST_2);
          return;
        }
        case 3 -> {
          methodVisitor.visitInsn(Opcodes.ICONST_3);
          return;
        }
        case 4 -> {
          methodVisitor.visitInsn(Opcodes.ICONST_4);
          return;
        }
        case 5 -> {
          methodVisitor.visitInsn(Opcodes.ICONST_5);
          return;
        }
      }

      if(v <= Byte.MAX_VALUE && v >= Byte.MIN_VALUE){
        methodVisitor.visitIntInsn(Opcodes.BIPUSH, v);
      }
      else if(v <= Short.MAX_VALUE && v >= Short.MIN_VALUE){
        methodVisitor.visitIntInsn(Opcodes.SIPUSH, v);
      }
      else{
        methodVisitor.visitLdcInsn(value);
      }
    }
    else if(value instanceof Enum<?>){
      IClass<?> type = ClassInfo.asType(value.getClass());
      methodVisitor.visitFieldInsn(
          Opcodes.GETSTATIC,
          type.internalName(),
          ((Enum<?>)value).name(),
          type.realName()
      );
    }
  }

  protected void castAssign(IClass<?> source, IClass<?> target){
    if(!target.isAssignableFrom(source)){
      if(!source.isPrimitive() && target.isPrimitive()){
        unwrapAssign(target);
      }
      else if(source.isPrimitive() && !target.isPrimitive()){
        wrapAssign(source);
      }
      else if(source.isPrimitive() && target.isPrimitive()){
        int opc = CASTTABLE.get(getTypeChar(source, true)).get(getTypeChar(target, false));
        methodVisitor.visitInsn(opc);
      }
      else methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, target.isArray()? target.realName(): target.internalName());
    }
  }

  protected void unwrapAssign(IClass<?> target){
    if(!target.isPrimitive())
      throw new IllegalHandleException("cannot unwrap non-primitive");

    if(target == ClassInfo.INT_TYPE || target == ClassInfo.FLOAT_TYPE
        || target == ClassInfo.LONG_TYPE || target == ClassInfo.DOUBLE_TYPE
        || target == ClassInfo.BYTE_TYPE || target == ClassInfo.SHORT_TYPE){
      methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Number");
    }

    if(target == ClassInfo.INT_TYPE){
      methodVisitor.visitMethodInsn(
          Opcodes.INVOKEVIRTUAL, "java/lang/Number", "intValue", "()I", false
      );
    }
    if(target == ClassInfo.FLOAT_TYPE){
      methodVisitor.visitMethodInsn(
          Opcodes.INVOKEVIRTUAL, "java/lang/Number", "floatValue", "()F", false
      );
    }
    if(target == ClassInfo.BYTE_TYPE){
      methodVisitor.visitMethodInsn(
          Opcodes.INVOKEVIRTUAL, "java/lang/Number", "byteValue", "()B", false
      );
    }
    if(target == ClassInfo.SHORT_TYPE){
      methodVisitor.visitMethodInsn(
          Opcodes.INVOKEVIRTUAL, "java/lang/Number", "shortValue", "()S", false
      );
    }
    if(target == ClassInfo.LONG_TYPE){
      methodVisitor.visitMethodInsn(
          Opcodes.INVOKEVIRTUAL, "java/lang/Number", "longValue", "()J", false
      );
    }
    if(target == ClassInfo.DOUBLE_TYPE){
      methodVisitor.visitMethodInsn(
          Opcodes.INVOKEVIRTUAL, "java/lang/Number", "doubleValue", "()D", false
      );
    }
    if(target == ClassInfo.BOOLEAN_TYPE){
      methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Boolean");
      methodVisitor.visitMethodInsn(
          Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false
      );
    }
    if(target == ClassInfo.CHAR_TYPE){
      methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Character");
      methodVisitor.visitMethodInsn(
          Opcodes.INVOKEVIRTUAL, "java/lang/Character", "charValue", "()C", false
      );
    }
  }

  protected void wrapAssign(IClass<?> source){
    if(!source.isPrimitive())
      throw new IllegalHandleException("cannot wrap non-primitive");

    String typeDisc = null, methodDisc = null;

    if(source == ClassInfo.INT_TYPE){typeDisc = "java/lang/Integer"; methodDisc = "(I)Ljava/lang/Integer;";}
    else if(source == ClassInfo.FLOAT_TYPE){typeDisc = "java/lang/Float"; methodDisc = "(F)Ljava/lang/Float;";}
    else if(source == ClassInfo.LONG_TYPE){typeDisc = "java/lang/Long"; methodDisc = "(J)Ljava/lang/Long;";}
    else if(source == ClassInfo.DOUBLE_TYPE){typeDisc = "java/lang/Double"; methodDisc = "(D)Ljava/lang/Double;";}
    else if(source == ClassInfo.BYTE_TYPE){typeDisc = "java/lang/Byte"; methodDisc = "(B)Ljava/lang/Byte;";}
    else if(source == ClassInfo.SHORT_TYPE){typeDisc = "java/lang/Short"; methodDisc = "(S)Ljava/lang/Short;";}
    else if(source == ClassInfo.BOOLEAN_TYPE){typeDisc = "java/lang/Boolean"; methodDisc = "(Z)Ljava/lang/Boolean;";}
    else if(source == ClassInfo.CHAR_TYPE){typeDisc = "java/lang/Character"; methodDisc = "(C)Ljava/lang/Character;";}

    methodVisitor.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        typeDisc,
        "valueOf",
        methodDisc,
        false
    );
  }

  protected static char getTypeChar(IClass<?> primitive, boolean foldInt){
    if(!primitive.isPrimitive()) return 'A';

    return switch(primitive.realName()){
      case "C" -> foldInt ? 'I' : 'C';
      case "B" -> foldInt ? 'I' : 'B';
      case "S" -> foldInt ? 'I' : 'S';
      case "Z", "I" -> 'I';
      case "J" -> 'L';
      case "F" -> 'F';
      case "D" -> 'D';
      default -> throw new IllegalHandleException("unknown type name " + primitive.realName());
    };
  }
}
