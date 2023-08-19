package com.github.ebwilson.sample;

import dynamilize.*;
import dynamilize.runtimeannos.AspectInterface;
import dynamilize.runtimeannos.Super;
import dynamilize.runtimeannos.This;

import java.util.Date;
import java.util.HashMap;

/**Here is a basic usage example of Dynamic Object. Workflow with dynamic objects is given below to demonstrate the basic usage of dynamic objects and dynamic types
 * <br>Please read this usage first and get to know the basic usage of dynamic objects and dynamic types before reading the application usage*/
public class BaseUse {
  @SuppressWarnings("unchecked")
  public static void main(String[] args) {
    //Gets the default dynamic factory.
    //For custom dynamic factory implementation, please refer to Advanced Application
    DynamicMaker maker = DynamicFactory.getDefault();

    //Create a dynamic type, which is necessary, regardless of whether the dynamic class has function.
    //The getter returns the dynamic type with the given name. A new dynamic type will be created if not existing
    DynamicClass Sample = DynamicClass.get("Sample");

    /*====================================================================
     * The following is the basic usage of Dynamic Object.
     * This paragraph shows the behavior of the most basic dynamic objects
     * ==================================================================*/

    // Instantiate a dynamic object with a dynamic factory, and provide a argument of aspect interface to limit the scope of the dynamic delegation.
    //If the aspect is `null`, then the dynamic instance will delegate all visible methods
    HashMap<String, String> map = maker.newInstance(HashMap.class, new Class[]{Example.class}, Sample).objSelf();

    //Cast the map to dynamic object to invoke the methods.
    //In fact, the type returned by `newInstance` is a Dynamic Object.
    //The `objSelf()` method is just a cast of `this`.
    //For the generic, the type was not directly presented as A DynamicObject
    DynamicObject<HashMap<String, String>> dyMap = (DynamicObject<HashMap<String, String>>) map;
    //Sets the function and monitors the method `put`
    dyMap.setFunc("put", (self, su, arg) -> {
      su.invokeFunc("put", arg);
      //Call the super method, equivalent to super.put(key, value)

      System.out.println("putting " + arg.<String>get(0) + " -> " + arg.<String>get(1));
    }, Object.class, Object.class);
    //Generic erasure. The upper bound of the type parameter needs to be used to limit the generic parameter.
    //The upper bound of this method is undetermined, that is, Object

    //test print
    map.put("a", "string 1");
    map.put("b", "string 2");
    map.put("c", "string 3");

    System.out.println("\n===========sep line==========\n");

    //For dynamic objects, hook functions can be used like scripting languages:
    Function<HashMap<String, String>, Object> hook = dyMap.getFunc("put", Object.class, Object.class);
    dyMap.setFunc("put", (self, arg) -> {
      hook.invoke(self, arg);//call hook
      System.out.println("putting now, current entries amount: " + self.objSelf().size());
    }, Object.class, Object.class);

    //test print
    map.put("d", "string 4");
    map.put("e", "string 5");
    map.put("f", "string 6");

    System.out.println("\n===========sep line==========\n");

    //You can also invoke methods of dynamic objects with reflection statements:
    System.out.println("reflectional invoke:");
    dyMap.invokeFunc("put", "g", "string 7");

    //Of course, you can also deal with object's field in such a way:
    System.out.println("current entries amount: " + dyMap.getVar("size"));

    /*=======================================================================
     * The above are the basic operations on dynamic objects
     * Here you may be a little confused about the role of dynamic types
     * In this paragraph, you will learn the usage and meaning of dynamic type
     * =====================================================================*/

    System.out.println("\n===========sep line==========\n");
    //The dynamic type Sample has been defined above, so use it directly. First declares a function for it:
    Sample.setFunction("put", (self, su, arg) -> {
      System.out.println("Dynamic class instance handle:");
      su.invokeFunc("put", arg);
      //The meaning here are the same as the direct definition of dynamic types
    }, Object.class, Object.class);

    //Test printing. Seeing the printing results, you will find that the lowest-level invocation has changed during the invoke
    //In fact, apart from some methods that refer to those in Java, the lowest-level function reference refers to those in the dynamic object, where the default function is to call the higher level or do nothing.
    //Therefore, when you change the function described in the dynamic type, all the instance of this dynamic type which is still invoke the higher level will function as it changed.
    map.put("i", "string 8");
    map.put("j", "string 9");
    map.put("k", "string 10");

    System.out.println("\n===========sep line==========\n");

    //Similarly, if a new function is created
    Sample.setFunction("putTime", (self, arg) -> {
      System.out.println("current time: " + new Date());
      self.invokeFunc("put", arg.get(0), new Date().toString());
    }, String.class);
    dyMap.invokeFunc("putTime", "currTime");

    //Successfully get the current time from the map. So we find that the function we set to the dynamic type will come into its instances.
    System.out.println("get time entry, res: " + map.get("currTime"));

    System.out.println("\n===========sep line==========\n");

    //Of course, it is a little cumbersome to set function with lambda., and there is a more convenient form of defining methods for dynamic types
    //In the following, we define a type `Template` as a Type Template. Here we make the template type takes effect
    Sample.visitClass(Template.class, maker.getHelper());

    //Test print
    System.out.println("get: " + map.get("a"));
    System.out.println("get: " + map.get("b"));
    System.out.println("get: " + map.get("c"));

    System.out.println("\n===========sep line==========\n");

    //In addition, dynamic types can also be extended, as shown below
    DynamicClass SampleChild = DynamicClass.declare("SampleChild", Sample);
    HashMap<String, String> map1 = maker.newInstance(HashMap.class, new Class[]{Example.class}, SampleChild).objSelf();

    SampleChild.setFunction("put", (self, su, arg) -> {
      su.invokeFunc("put", arg);
      System.out.println("current map: " + self);
    }, Object.class, Object.class);

    //Test printing and see the results, the object inherits the function `put` from its superclass.
    //And the function defined by this type is executed immediately.
    //It is easy to see that the inherit of dynamic type is very similar to the class inherit in Java.
    map1.put("abc", "extend sample 1");
    map1.put("def", "extend sample 2");
    map1.put("ghi", "extend sample 3");

    //At this point, the basic usage instructions on dynamic types are over. Next, some specific applications will be shown in other files in the case.
    //Please learn the basic usage methods before reading the subsequent cases.
  }

  //The Type Template defined with Java, to make the dynamic object to directly get function from a Java class, which is more clear.
  static class Template{
    //The method used to define the function must be `static`. The `this` pointer is provided in by adding the @This annotation to the first parameter, and `super` is the second parameter using the @Super annotation
    //The position cannot be swapped, but it can be omitted, such as the `self` here is actually unnecessary
    //The parameters after this and super are signed with the name of the method
    //The signature of this example is equivalent to public Object get(Object key), which will override the function of map

    //In addition, when using dynamic types, the type of this pointer is indeterminate, so its generic argument is not specified
    //But for dynamic classes that will only be used in a legal java class hierarchy, you can specify a exact type.
    public static Object get(@This DynamicObject<?> self, @Super DataPool.ReadOnlyPool su, Object key){
      System.out.println("getting entries, key: " + key);
      return su.invokeFunc("get", key);
      //The type is not exact here, so use reflection to call the method
    }
  }

  /**Define an aspect interface to limit the dynamic delegation*/
  @AspectInterface
  public interface Example{
    void put(Object k, Object v);
    Object get(Object k);
  }
}