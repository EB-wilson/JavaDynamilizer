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

import static dynamilize.classmaker.ClassInfo.CLASS_TYPE;
import static dynamilize.classmaker.ClassInfo.STRING_TYPE;

/**基于ASM字节码操作框架实现的默认类型生成器，*/
public class ASMGenerator extends AbstractClassGenerator implements Opcodes{
  protected static final Map<Character, Map<Character, Integer>> CASTTABLE = new HashMap<>();

  static {
    HashMap<Character, Integer> map;
    map = new HashMap<>();
    CASTTABLE.put('I', map);
    map.put('I', I2L);
    map.put('F', I2F);
    map.put('D', I2D);
    map.put('B', I2B);
    map.put('C', I2C);
    map.put('S', I2S);
    map = new HashMap<>();
    CASTTABLE.put('L', map);
    map.put('I', L2I);
    map.put('F', L2F);
    map.put('D', L2D);
    map = new HashMap<>();
    CASTTABLE.put('F', map);
    map.put('I', F2I);
    map.put('L', F2L);
    map.put('D', F2D);
    map = new HashMap<>();
    CASTTABLE.put('D', map);
    map.put('I', D2I);
    map.put('L', D2L);
    map.put('F', D2F);
  }

  protected final ByteClassLoader classLoader;
  protected final int codeVersion;

  protected ClassWriter writer;

  protected IClass<?> currGenerating;

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
  protected  <T> Class<T> generateClass(ClassInfo<T> classInfo){
    classLoader.declareClass(classInfo.name(), genByteCode(classInfo));
    currGenerating = classInfo;

    try{
      return (Class<T>) classLoader.loadClass(classInfo.name(), false);
    }catch(ClassNotFoundException e){
      throw new IllegalHandleException(e);
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
  }

  @Override
  public void visitMethod(IMethod<?, ?> method){
    methodVisitor = writer.visitMethod(
        method.modifiers(),
        method.name(),
        method.typeDescription(),
        null,
        null
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
      }

      super.visitMethod(method);

      Label endLine = new Label();
      methodVisitor.visitLabel(endLine);

      int localCount = 0;
      for(Map.Entry<String, ILocal<?>> entry: localMap.entrySet()){
        methodVisitor.visitLocalVariable(
            entry.getKey(),
            entry.getValue().type().realName(),
            null,
            firstLine,
            endLine,
            localCount
        );

        IClass<?> type = entry.getValue().type();
        localCount += type.equals(ClassInfo.LONG_TYPE) || type.equals(ClassInfo.DOUBLE_TYPE)? 2: 1;
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
            PUTSTATIC,
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

    if(block.owner().name().equals("<init>") && (!(block.codes().get(0) instanceof IInvoke<?>)
    || !((IInvoke<?>)block.codes().get(0)).method().name().equals("<init>"))){
      block.owner().owner().superClass().getConstructor();

      methodVisitor.visitMethodInsn(
          INVOKESPECIAL,
          block.owner().owner().superClass().internalName(),
          "<init>",
          "()V",
          false
      );
    }

    super.visitCodeBlock(block);

    Element end = block.codes().isEmpty()? null: block.codes().get(block.codes().size() - 1);
    if(end != null && end.kind() == ElementKind.RETURN){
      IReturn<?> ret = (IReturn<?>) end;
      if((ret.returnValue() == null && block.owner().returnType().equals(ClassInfo.VOID_TYPE))
      || (block.owner().returnType().isAssignableFrom(ret.returnValue().type()))) return;
    }
    else if(end != null && end.kind() == ElementKind.THROW) return;

    if(block.owner().returnType() == ClassInfo.VOID_TYPE){
      methodVisitor.visitInsn(RETURN);
    }
    else throw new IllegalHandleException("method return a non-null value, but the method is not returning correctly");
  }

  @Override
  public void visitField(IField<?> field){
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
    int invokeType = Modifier.isStatic(invoke.method().modifiers())? INVOKESTATIC:
        Modifier.isInterface(invoke.method().owner().modifiers())? INVOKEINTERFACE:
        invoke.method().name().equals("<init>") || invoke.callSuper()? INVOKESPECIAL:
        INVOKEVIRTUAL;
    
    if(!Modifier.isStatic(invoke.method().modifiers())){
      methodVisitor.visitVarInsn(ALOAD, localIndex.get(invoke.target().name()));
    }

    for(ILocal<?> arg: invoke.args()){
      methodVisitor.visitVarInsn(
          getLoadType(arg.type()),
          localIndex.get(arg.name())
      );
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
        methodVisitor.visitInsn(POP);
      }
    }
    else{
      castAssign(type, invoke.returnTo().type());

      methodVisitor.visitVarInsn(
          getStoreType(invoke.returnTo().type()),
          localIndex.get(invoke.returnTo().name())
      );
    }
  }

  @Override
  public void visitGetField(IGetField<?, ?> getField){
    if(!Modifier.isStatic(getField.source().modifiers())){
      methodVisitor.visitVarInsn(ALOAD, localIndex.get(getField.inst().name()));
    }

    methodVisitor.visitFieldInsn(
        Modifier.isStatic(getField.source().modifiers())? GETSTATIC: GETFIELD,
        getField.source().owner().internalName(),
        getField.source().name(),
        getField.source().type().realName()
    );

    castAssign(getField.source().type(), getField.target().type());

    methodVisitor.visitVarInsn(
        getStoreType(getField.source().type()),
        localIndex.get(getField.target().name())
    );
  }

  @Override
  public void visitPutField(IPutField<?, ?> putField){
    if(!Modifier.isStatic(putField.target().modifiers())){
      methodVisitor.visitVarInsn(ALOAD, localIndex.get(putField.inst().name()));
    }

    methodVisitor.visitVarInsn(
        getLoadType(putField.source().type()),
        localIndex.get(putField.source().name())
    );

    castAssign(putField.source().type(), putField.target().type());

    methodVisitor.visitFieldInsn(
        Modifier.isStatic(putField.target().modifiers())? PUTSTATIC: PUTFIELD,
        putField.target().owner().internalName(),
        putField.target().name(),
        putField.target().type().realName()
    );
  }

  @Override
  public void visitLocalSet(ILocalAssign<?, ?> localSet){
    methodVisitor.visitVarInsn(
        getLoadType(localSet.source().type()),
        localIndex.get(localSet.source().name())
    );

    castAssign(localSet.source().type(), localSet.target().type());

    methodVisitor.visitVarInsn(
        getStoreType(localSet.target().type()),
        localIndex.get(localSet.target().name())
    );
  }

  @Override
  public void visitOperate(IOperate<?> operate){
    methodVisitor.visitVarInsn(
        getLoadType(operate.leftOpNumber().type()),
        localIndex.get(operate.leftOpNumber().name())
    );

    methodVisitor.visitVarInsn(
        getLoadType(operate.rightOpNumber().type()),
        localIndex.get(operate.rightOpNumber().name())
    );

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

      return;
    }

    IClass<?> type = operate.leftOpNumber().type();
    int ilfd = getILFD(type);

    int opc = switch(operate.opCode()){
      case ADD -> selectILFD(ilfd, IADD, LADD, FADD, DADD);
      case SUBSTRUCTION -> selectILFD(ilfd, ISUB, LSUB, FSUB, DSUB);
      case MULTI -> selectILFD(ilfd, IMUL, LMUL, FMUL, DMUL);
      case DIVISION -> selectILFD(ilfd, IDIV, LDIV, FDIV, DDIV);
      case REMAINING -> selectILFD(ilfd, IREM, LREM, FREM, DREM);
      case LEFTMOVE -> selectIL(ilfd, ISHL, LSHL);
      case RIGHTMOVE -> selectIL(ilfd, ISHR, LSHR);
      case UNSIGNMOVE -> selectIL(ilfd, IUSHR, LUSHR);
      case BITSAME -> selectIL(ilfd, IAND, LAND);
      case BITOR -> selectIL(ilfd, IOR, LOR);
      case BITXOR -> selectIL(ilfd, IXOR, LXOR);
    };

    methodVisitor.visitInsn(opc);
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
    methodVisitor.visitVarInsn(
        getLoadType(cast.source().type()),
        localIndex.get(cast.source().name())
    );

    castAssign(cast.source().type(), cast.target().type());

    methodVisitor.visitVarInsn(
        getStoreType(cast.target().type()),
        localIndex.get(cast.target().name())
    );
  }

  @Override
  public void visitGoto(IGoto iGoto){
    methodVisitor.visitJumpInsn(GOTO, labelMap.get(iGoto.target()));
  }
  
  @Override
  public void visitLabel(IMarkLabel label){
    methodVisitor.visitLabel(labelMap.get(label.label()));
  }

  @Override
  public void visitCompare(ICompare<?> compare){
    int opc = switch(compare.comparison()){
      case EQUAL -> IF_ACMPEQ;
      case UNEQUAL -> IF_ACMPNE;
      case LESS -> IF_ICMPLT;
      case LESSOREQUAL -> IF_ICMPLE;
      case MORE -> IF_ICMPGT;
      case MOREOREQUAL -> IF_ICMPGE;
    };

    methodVisitor.visitVarInsn(
        getLoadType(compare.leftNumber().type()),
        localIndex.get(compare.leftNumber().name())
    );

    methodVisitor.visitVarInsn(
        getLoadType(compare.rightNumber().type()),
        localIndex.get(compare.rightNumber().name())
    );

    methodVisitor.visitJumpInsn(opc, labelMap.get(compare.ifJump()));
  }

  @Override
  public void visitCondition(ICondition condition){
    int opc = switch(condition.condCode()){
      case EQUAL -> IFEQ;
      case UNEQUAL -> IFNE;
      case LESS -> IFLT;
      case LESSOREQUAL -> IFLE;
      case MORE -> IFGT;
      case MOREOREQUAL -> IFGE;
    };

    methodVisitor.visitVarInsn(
        getLoadType(condition.condition().type()),
        localIndex.get(condition.condition().name())
    );

    methodVisitor.visitJumpInsn(opc, labelMap.get(condition.ifJump()));
  }

  @Override
  public void visitArrayGet(IArrayGet<?> arrayGet){
    methodVisitor.visitVarInsn(
        ALOAD,
        localIndex.get(arrayGet.array().name())
    );

    IClass<?> componentType = arrayGet.array().type().componentType();

    int loadType;
    if(componentType == ClassInfo.INT_TYPE){loadType = IALOAD;}
    else if(componentType == ClassInfo.FLOAT_TYPE){loadType = FALOAD;}
    else if(componentType == ClassInfo.LONG_TYPE){loadType = LALOAD;}
    else if(componentType == ClassInfo.DOUBLE_TYPE){loadType = DALOAD;}
    else if(componentType == ClassInfo.BYTE_TYPE){loadType = BALOAD;}
    else if(componentType == ClassInfo.SHORT_TYPE){loadType = SALOAD;}
    else if(componentType == ClassInfo.BOOLEAN_TYPE){loadType = BALOAD;}
    else if(componentType == ClassInfo.CHAR_TYPE){loadType = CALOAD;}
    else {loadType = AALOAD;}

    methodVisitor.visitVarInsn(
        ILOAD,
        localIndex.get(arrayGet.index().name())
    );

    methodVisitor.visitInsn(loadType);

    castAssign(arrayGet.array().type().componentType(), arrayGet.getTo().type());

    methodVisitor.visitVarInsn(
        getStoreType(componentType),
        localIndex.get(arrayGet.getTo().name())
    );
  }

  @Override
  public void visitArrayPut(IArrayPut<?> arrayPut){
    methodVisitor.visitVarInsn(
        ALOAD,
        localIndex.get(arrayPut.array().name())
    );

    methodVisitor.visitVarInsn(
        ILOAD,
        localIndex.get(arrayPut.index().name())
    );

    IClass<?> componentType = arrayPut.array().type().componentType();

    int storeType;
    if(componentType == ClassInfo.INT_TYPE){storeType = IASTORE;}
    else if(componentType == ClassInfo.FLOAT_TYPE){storeType = FASTORE;}
    else if(componentType == ClassInfo.LONG_TYPE){storeType = LASTORE;}
    else if(componentType == ClassInfo.DOUBLE_TYPE){storeType = DASTORE;}
    else if(componentType == ClassInfo.BYTE_TYPE){storeType = BASTORE;}
    else if(componentType == ClassInfo.SHORT_TYPE){storeType = SASTORE;}
    else if(componentType == ClassInfo.BOOLEAN_TYPE){storeType = BASTORE;}
    else if(componentType == ClassInfo.CHAR_TYPE){storeType = CASTORE;}
    else {storeType = AASTORE;}

    methodVisitor.visitVarInsn(
        getLoadType(arrayPut.value().type()),
        localIndex.get(arrayPut.value().name())
    );

    castAssign(arrayPut.value().type(), arrayPut.array().type().componentType());

    methodVisitor.visitInsn(storeType);
  }

  @Override
  public void visitSwitch(ISwitch<?> zwitch){
    methodVisitor.visitVarInsn(
        getLoadType(zwitch.target().type()),
        localIndex.get(zwitch.target().name())
    );

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
            INVOKESTATIC,
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
    methodVisitor.visitVarInsn(
        getLoadType(thr.thr().type()),
        localIndex.get(thr.thr().name())
    );

    methodVisitor.visitInsn(ATHROW);
  }

  @Override
  public void visitReturn(IReturn<?> iReturn){
    if(iReturn.returnValue() == null){
      methodVisitor.visitInsn(RETURN);
    }
    else{
      int opc = switch(getTypeChar(iReturn.returnValue().type(), true)){
        case 'I' -> IRETURN;
        case 'L' -> LRETURN;
        case 'F' -> FRETURN;
        case 'D' -> DRETURN;
        case 'A' -> ARETURN;
        default -> -1;
      };
      ;

      methodVisitor.visitVarInsn(
          getLoadType(iReturn.returnValue().type()),
          localIndex.get(iReturn.returnValue().name())
      );

      if(opc == ARETURN){
        methodVisitor.visitTypeInsn(
            CHECKCAST,
            iReturn.returnValue().type().internalName()
        );
      }

      methodVisitor.visitInsn(opc);
    }
  }

  @Override
  public void visitInstanceOf(IInstanceOf instanceOf){
    methodVisitor.visitVarInsn(
        getLoadType(instanceOf.target().type()),
        localIndex.get(instanceOf.target().name())
    );

    methodVisitor.visitTypeInsn(
        INSTANCEOF,
        instanceOf.type().internalName()
    );

    methodVisitor.visitVarInsn(
        getStoreType(instanceOf.result().type()),
        localIndex.get(instanceOf.result().name())
    );
  }

  @Override
  public void visitNewInstance(INewInstance<?> newInstance){
    methodVisitor.visitTypeInsn(NEW, newInstance.type().internalName());
    methodVisitor.visitInsn(DUP);

    for(ILocal<?> local: newInstance.params()){
      methodVisitor.visitVarInsn(
          getLoadType(local.type()),
          localIndex.get(local.name())
      );
    }

    methodVisitor.visitMethodInsn(
        INVOKESPECIAL,
        newInstance.type().internalName(),
        newInstance.constructor().name(),
        newInstance.constructor().typeDescription(),
        false
    );

    methodVisitor.visitVarInsn(
        getStoreType(newInstance.type()),
        localIndex.get(newInstance.instanceTo().name())
    );
  }

  @Override
  public void visitOddOperate(IOddOperate<?> operate){
    methodVisitor.visitVarInsn(
        getLoadType(operate.operateNumber().type()),
        localIndex.get(operate.operateNumber().name())
    );

    IClass<?> type = operate.operateNumber().type();
    int ilfd = getILFD(type);

    int opc = switch(operate.opCode()){
      case NEGATIVE -> selectILFD(ilfd, INEG, LNEG, FNEG, DNEG);
      case BITNOR -> selectIL(ilfd, IXOR, LXOR);
    };

    if(opc == IXOR){
      methodVisitor.visitInsn(ICONST_M1);
      methodVisitor.visitInsn(IXOR);
    }
    else if(opc == LXOR){
      methodVisitor.visitLdcInsn(-1L);
      methodVisitor.visitInsn(LXOR);
    }
    else{
      methodVisitor.visitInsn(opc);
    }
  }

  @Override
  public void visitConstant(ILoadConstant<?> loadConstant){
    visitConstant(loadConstant.constant());

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
      methodVisitor.visitVarInsn(
          getLoadType(len.type()),
          localIndex.get(len.name())
      );

      methodVisitor.visitTypeInsn(
          newArray.arrayEleType().isPrimitive()? NEWARRAY: ANEWARRAY,
          newArray.arrayEleType().internalName()
      );
    }
    else if(dimension > 1){
      IClass<?> arrType = newArray.arrayEleType();
      for(int i = 0; i < dimension; i++){
        arrType = arrType.asArray();

        ILocal<?> len = newArray.arrayLength().get(i);
        methodVisitor.visitVarInsn(
            getLoadType(len.type()),
            localIndex.get(len.name())
        );
      }

      methodVisitor.visitMultiANewArrayInsn(arrType.realName(), dimension);
    }
    else throw new IllegalHandleException("illegal array dimension " + dimension);

    methodVisitor.visitVarInsn(
        ASTORE,
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
    return typeSelect(type, ASTORE, ISTORE, LSTORE, DSTORE, FSTORE);
  }

  protected int getLoadType(IClass<?> type){
    return typeSelect(type, ALOAD, ILOAD, LLOAD, DLOAD, FLOAD);
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
      methodVisitor.visitInsn(ACONST_NULL);
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
            GETSTATIC,
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
        if(componentType == int.class){opc = T_INT; storeType = IASTORE;}
        else if(componentType == float.class){opc = T_FLOAT; storeType = FASTORE;}
        else if(componentType == long.class){opc = T_LONG; storeType = LASTORE;}
        else if(componentType == double.class){opc = T_DOUBLE; storeType = DASTORE;}
        else if(componentType == byte.class){opc = T_BYTE; storeType = BASTORE;}
        else if(componentType == short.class){opc = T_SHORT; storeType = SASTORE;}
        else if(componentType == boolean.class){opc = T_BOOLEAN; storeType = BASTORE;}
        else if(componentType == char.class){opc = T_CHAR; storeType = CASTORE;}
        else {opc = 0; storeType = AASTORE;}

        int len = Array.getLength(value);
        visitConstant(len);

        if(componentType.isPrimitive()){
          methodVisitor.visitIntInsn(NEWARRAY, opc);
        }
        else methodVisitor.visitTypeInsn(ANEWARRAY, ClassInfo.asType(value.getClass()).internalName());

        for(int i = 0; i < len; i++){
          methodVisitor.visitInsn(DUP);
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
        if(l == 0){methodVisitor.visitInsn(LCONST_0); return;}
        else if(l == 1){methodVisitor.visitInsn(LCONST_1); return;}
      }
      else if(value instanceof Float){
        float f = (float) value;
        if(f == 0){methodVisitor.visitInsn(FCONST_0); return;}
        else if(f == 1){methodVisitor.visitInsn(FCONST_1); return;}
        else if(f == 2){methodVisitor.visitInsn(FCONST_2); return;}
      }
      else if(value instanceof Double){
        double d = (double) value;
        if(d == 0){methodVisitor.visitInsn(DCONST_0); return;}
        else if(d == 1){methodVisitor.visitInsn(DCONST_1); return;}
      }

      methodVisitor.visitLdcInsn(value);
    }
    else if(value instanceof Integer || value instanceof Byte
    || value instanceof Short || value instanceof Character){
      int v = (int) value;
      switch(v){
        case 0 -> {
          methodVisitor.visitInsn(ICONST_0);
          return;
        }
        case 1 -> {
          methodVisitor.visitInsn(ICONST_1);
          return;
        }
        case 2 -> {
          methodVisitor.visitInsn(ICONST_2);
          return;
        }
        case 3 -> {
          methodVisitor.visitInsn(ICONST_3);
          return;
        }
        case 4 -> {
          methodVisitor.visitInsn(ICONST_4);
          return;
        }
        case 5 -> {
          methodVisitor.visitInsn(ICONST_5);
          return;
        }
      }

      if(v <= Byte.MAX_VALUE && v >= Byte.MIN_VALUE){
        methodVisitor.visitIntInsn(BIPUSH, v);
      }
      else if(v <= Short.MAX_VALUE && v >= Short.MIN_VALUE){
        methodVisitor.visitIntInsn(SIPUSH, v);
      }
      else{
        methodVisitor.visitLdcInsn(value);
      }
    }
    else if(value instanceof Enum<?>){
      IClass<?> type = ClassInfo.asType(value.getClass());
      methodVisitor.visitFieldInsn(
          GETSTATIC,
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
      else methodVisitor.visitTypeInsn(CHECKCAST, target.internalName());
    }
  }

  protected void unwrapAssign(IClass<?> target){
    if(!target.isPrimitive())
      throw new IllegalHandleException("cannot unwrap non-primitive");

    if(target == ClassInfo.INT_TYPE || target == ClassInfo.FLOAT_TYPE
        || target == ClassInfo.LONG_TYPE || target == ClassInfo.DOUBLE_TYPE
        || target == ClassInfo.BYTE_TYPE || target == ClassInfo.SHORT_TYPE){
      methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Number");
    }

    if(target == ClassInfo.INT_TYPE){
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Number", "intValue", "()I", false
      );
    }
    if(target == ClassInfo.FLOAT_TYPE){
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Number", "floatValue", "()F", false
      );
    }
    if(target == ClassInfo.BYTE_TYPE){
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Number", "byteValue", "()B", false
      );
    }
    if(target == ClassInfo.SHORT_TYPE){
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Number", "shortValue", "()S", false
      );
    }
    if(target == ClassInfo.LONG_TYPE){
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Number", "longValue", "()J", false
      );
    }
    if(target == ClassInfo.DOUBLE_TYPE){
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Number", "doubleValue", "()D", false
      );
    }
    if(target == ClassInfo.BOOLEAN_TYPE){
      methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Boolean");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false
      );
    }
    if(target == ClassInfo.CHAR_TYPE){
      methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Character");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Character", "charValue", "()C", false
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
        INVOKESTATIC,
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
