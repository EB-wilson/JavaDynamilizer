# JavaDynamilizer
一个java语言的动态化支持框架，允许对java类型进行动态委托并构造动态实例，以类似脚本语言的“函数”和“变量”来替代“方法”和“字段”，它们是可以变化的，动态实例可以委托自任意可用的java类（非final且具有至少对子类可见的构造器），动态实例可以分配给被委托的java类。
****
要引用JavaDynamilizer部署项目，需要在项目依赖中添加对此仓库的依赖：

    dependencies {
      ......
	  implementation 'com.github.EB-wilson.JavaDynamilizer:core:$version'
	  implementation 'com.github.EB-wilson.JavaDynamilizer:baseimpl:$version'//基础的内部实现，若您需要自定义实现抽象层的话，这个模块就并不是必要的
      ......
	}

> 当前这个项目还尚不成熟和健壮，需要更多的使用与建议来进一步完善和优化，如果您有能力还请不吝参与此框架的开发。

您可以参照项目中给出的一些使用[案例](https://github.com/EB-wilson/JavaDynamilizer/tree/master/usage_sample/src/main/java/com/github/ebwilson/sample)和javadoc了解更多关于这个仓库的使用方法。下面是基本使用方法简述：

## 基本使用
创建一个最简单的全委托动态实例，它委托自java类HashMap:

    DynamicMaker maker = DynamicFactory.getDefault();
    DynamicClass Sample = DynamicClass.get("Sample"); 

    HashMap<String, String> map = maker.newInstance(HashMap.class, Sample).objSelf();

其中，map对象具有所有hashMap的行为并且可以分配给HashMap字段或者参数。而本工具的重点在于动态化实例，上面创建的map实例已经被动态化，它具有`dynamilize.DynamicObject`的特征，对于此对象已经可以进行行为变更和代码插桩了。

例如，接下来我们对上面的map的`put`方法添加一个监视，使任何时候向该映射中添加映射都会将这个键值对打印出来：

    DynamicObject<HashMap<?, ?>> dyMap = (DynamicObject<HashMap<?,?>>)map;
    dyMap.setFunc("put", (self, args) -> {
      self.superPointer().invokeFunc("put", args);
      System.out.println("map putted, key: " + args.get(0) + ", value: " + args.get(1) + ".");
    }, Object.class, Object.class);

这时，如果执行`map.put("first", "hello world")`，那么会在系统输出流中收到如下信息：

    map putted, key: first, value: hello world.

另外，在构建一个动态实例时，您还可以令实例实现一组接口，同样的，实例可以被正确的分配给这个接口的类型：

    DynamicClass Demo = DynamicClass.get("Demo");
    DynamicObject<?> dyObj = DynamicFactory.getDefault().newInstance(new Class[]{Map.class}, Demo);
    Map<?, ?> map = (Map<?, ?>)dyObj;

但是在实现接口时，您必须使用`setFunc`方法来对所有的接口抽象方法进行实现，否则对此方法的引用会抛出致命异常。

## 动态类型

> 前面提到了关于实现接口时，需要对方法进行逐一实现的情况，如果对每一个抽象方法都需要逐一引用`setFunc`进行设置实现的话，无疑代码会变得尤其复杂，为此我们引入**动态类型**，以便更加有效的利用动态实例。

每一个动态实例，都会有一个关联的动态类型，这类似于java本身的**类**与**对象**的关系，不同的是，动态类型描述了其实例的默认行为，如该类型的实例构建后所具有的默认函数和变量。从之前的例子我们已经可以知道，创建一个动态实例必须要来自一个动态类型：

    DynamicClass Demo = DynamicClass.get("Demo");

动态类型也有它的超类，距离这个动态类型最近的一个超类称为此类的**直接超类**，对于上述例子的`Demo`类，它的直接超类没有被明确确定，则它的直接超类为构造实例进行委托的java类；若有确定的直接超类：

    DynamicClass DemoChild = DynamicClass.declare("DemoChild", Demo);

直接超类与java类的类层次结构行为基本一致，即遵循原则：**引用一个函数或者变量时，以最接近此类型的超类优先被访问**。

下面，将讨论如何设置动态类型的行为。  
动态类型的行为主要由函数和变量构成，对于函数设置有**访问样版模式**和**lambda模式**，对于变量，则有**访问样版模式**和**函数模式**以及**常量模式**，不同的模式有各自的应用场景和使用方法：

先创建一个新的动态类：

    DynamicClass Sample = DynamicLcass.get("Sample");
****
- **函数**

**访问样版模式**  
即使用java类型描述一定的行为，用动态类型去访问这个java类型。  
例如声明一个类型用作样版：

    public class Template{
      public static void run(){
        System.out.println("hello world");
      }
    }

那么用动态类型去访问：

    DynamicMaker maker = DynamicFactory.getDefault();
    Sample.visitClass(Template.class, maker.getHelper());
    DynamicObject<?> dyObj = maker.newInstance(Sample);
    dyObj.invokeFunc("run");

这将会在系统输出流中打印`hello world`。  
访问类型样版会描述类型中声明的所有未排除的public static方法，如果你需要针对某一方法访问，则可以通过反射获取方法对象，使用`Sample.visitMethod(*reflection method*)`方法对方法进行单独访问。
****
**lambda模式**  
即匿名函数模式，意在使用lambda表达式声明的匿名函数来描述动态类型中函数的行为。

    Sample.setFunction("run", (self, args) -> System.out.println("hello world"));
    DynamicObject<?> dyObj = DynamicMaker.getDefault().newInstance(Sample);
    dyObj.invokeFunc("run");

这一段代码与前一种方法描述的对象行为是一致的。
****
- **变量**

**访问样板模式**  
与函数的访问样板模式类似，只是函数访问的是方法，而变量要访问的是字段  
例如，定义样板类型：

    public class Template{
      public static String var1 = "hello world";
    }

如果访问动态对象的变量`var1`：

    DynamicMaker maker = DynamicFactory.getDefault();
    Sample.visitClass(Template.class, maker.getHelper());
    DynamicObject<?> dyObj = maker.newInstance(Sample);
    System.out.println(dyObject.getVar("var1"));

这会在系统输出流种打印`hello world`。  
与方法相同，访问一个类型样板会访问其中的所有public static字段，若需要就某一字段进行访问，则可以对反射获取的字段对象使用`Sample.visitField(*reflection field*)`方法来访问指定的字段。  
另外，字段的访问样版可以被设置为函数：

    public class Template{
      public static Initializer.Producer<Long> var1 = () -> System.nanoTime();
    }

这与后文函数模式的效果一致。
****
**常量模式**  
即直接用一个常量作为变量的初始值：

    Sample.setVariable("var1", "hello world", false);
    DynamicObject<?> dyObj = DynamicMaker.getDefault().newInstance(Sample);
    System.out.println(dyObject.getVar("var1"));

这将获得与前一种方法同样的效果。
****
**函数模式**  
这种方式使用一个工厂函数生产变量初始值，每一个函数初始化都会调用给出的函数来生产变量的初始值，例如：

    Sample.setVariable("time", () -> System.nanoTime(), false);

则会使得对象的`time`变量与对象被创建时的系统纳秒计数一致。  
函数与常量模式参数尾部的布尔值是确定此变量是否为**常量**，若为true，则此变量不可被改变。
****
- 动态类型声明的变量初始值只在对象被创建时有效，任何时候改变类的变量初始值都不会对已有实例造成影响
