package dynamilize.classmaker.code;

import dynamilize.classmaker.ClassInfo;
import dynamilize.classmaker.CodeVisitor;

import java.util.HashMap;
import java.util.Map;

public interface IOperate<T> extends Code{
  @Override
  default void accept(CodeVisitor visitor){
    visitor.visitOperate(this);
  }

  OPCode opCode();

  ILocal<?> resultTo();

  ILocal<T> leftOpNumber();

  ILocal<T> rightOpNumber();

  enum OPCode{
    ADD("+"),
    SUBSTRUCTION("-"),
    MULTI("*"),
    DIVISION("/"),
    REMAINING("%"),

    LEFTMOVE("<<"),
    RIGHTMOVE(">>"),
    UNSIGNMOVE(">>>"),
    BITSAME("&"),
    BITOR("|"),
    BITNOR("~"),
    BITXOR("^"),

    AND("&&", ClassInfo.BOOLEAN_TYPE),
    OR("||", ClassInfo.BOOLEAN_TYPE),
    NOT("!", ClassInfo.BOOLEAN_TYPE),

    MORE(">", ClassInfo.BOOLEAN_TYPE),
    LESS("<", ClassInfo.BOOLEAN_TYPE),
    EQUAL("==", ClassInfo.BOOLEAN_TYPE),
    MOREOREQUAL(">=", ClassInfo.BOOLEAN_TYPE),
    LESSOREQUAL("<=", ClassInfo.BOOLEAN_TYPE);

    private static final Map<String, OPCode> symbolMap = new HashMap<>();

    static{
      for(OPCode opc: values()){
        symbolMap.put(opc.symbol, opc);
      }
    }

    private final String symbol;
    private final ClassInfo<?> resultType;

    OPCode(String sym){
      this.symbol = sym;
      this.resultType = null;
    }

    OPCode(String sym, ClassInfo<?> resultType){
      this.symbol = sym;
      this.resultType = resultType;
    }

    public static OPCode as(String symbol){
      return symbolMap.computeIfAbsent(symbol, e -> {throw new IllegalArgumentException("unknown operator symbol: " + e);});
    }

    public ClassInfo<?> resultType(){
      return resultType;
    }
  }
}
