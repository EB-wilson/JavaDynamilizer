package dynamilize.annotations;

import dynamilize.DynamicClass;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**动态上下文标记，携带此注解的类型会被视为一个具有动态化语义的上下文
 * <p>具体来说，这个注解是类型内局部动态语义的最外层标记，所有局部动态语义声明的注解都需要其所在的环境具有此注解标记的外层
 * <p>本注解本身没有实际作用，关于这个注解支持的局部语义注解可参见：：
 * <ul>
 *   <li><strong>@{@link ContextMaker}</strong> - 上下文动态工厂标记</li>
 *   <li><strong>@{@link NewDynamic}</strong> - new语句动态化委托</li>
 * </ul>
 *
 * 关于注解的详细行为请参阅各个受支持的注解*/
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface DynamicContext {

  interface DynamilizedContext{
    DynamilizedContextHandles $ = new DynamilizedContextHandles();
  }

  class DynamilizedContextHandles{
    public void by(DynamicClass dynamicClass){}
    public void with(Class<?>... aspects){}
    public void impl(Class<?>... interfaces){}
  }
}
