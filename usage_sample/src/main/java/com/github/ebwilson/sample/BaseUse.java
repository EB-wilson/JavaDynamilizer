package com.github.ebwilson.sample;

import dynamilize.*;
import dynamilize.annotation.AspectInterface;
import dynamilize.annotation.Super;
import dynamilize.annotation.This;

import java.util.Date;
import java.util.HashMap;

/**This is a basic usage example of dynamic objects, a workflow using dynamic objects is given below to demonstrate the basic usage of dynamic objects and dynamic types
 * <br>Please read this case first and master the basic usage of dynamic types and dynamic objects before reading the application case*/
public class BaseUse {
  @SuppressWarnings("unchecked")
  public static void main(String[] args) {
    //Obtain the default dynamic factory.
    // For custom dynamic factory implementation, please refer to Advanced Application
    DynamicMaker maker = DynamicFactory.getDefault();

    //Create a dynamic type, which is necessary,
    // regardless of whether the dynamic class has behavior
    //the get method returns the dynamic type with the given name, and creates a new dynamic type if the type does not already exist
    DynamicClass Sample = DynamicClass.get("Sample");

    /*====================================================================
     * The following is the basic usage of dynamic objects
     * This paragraph shows the behavior of the most basic dynamic objects
     * ==================================================================*/

    //Use a dynamic factory to instantiate a dynamic object, and pass an aspect interface to limit the scope of dynamic delegation.
    //If the limited aspect is null, then the dynamic instance will dynamically delegate all visible methods
    HashMap<String, String> map = maker.newInstance(HashMap.class, new Class[]{Example.class}, Sample).objSelf();

    //Strongly convert to a dynamic object to call the behavior
    //but in fact the type returned by the new Instance method of the maker is a DynamicObject
    //and the objSelf() method is just a strong type conversion of the object this
    //here because of the problem of generic conversion, there is no direct Use the returned DynamicObject instance
    DynamicObject<HashMap<String, String>> dyMap = (DynamicObject<HashMap<String, String>>) map;
    //Set the behavior and monitor the method 'put':
    dyMap.setFunc("put", (self, su, arg) -> {
      su.invokeFunc("put", arg);//Call the super method, equivalent to super.put(key, value)

      System.out.println("putting " + arg.<String>get(0) + " -> " + arg.<String>get(1));
    }, Object.class, Object.class);//Generic erasure, the upper bound of the type parameter needs to be used to limit the generic parameter.
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

    //You can also invoke methods of dynamic objects using reflective statements:
    System.out.println("reflectional invoke:");
    dyMap.invokeFunc("put", "g", "string 7");

    //Of course, you can also manipulate object field values in a similar way:
    System.out.println("current entries amount: " + dyMap.getVar("size"));

    /*=======================================================================
     * The above are the basic operations on dynamic objects
     * and here you may be a little confused about the role of dynamic types
     * You will learn the usage and meaning of dynamic type in this paragraph
     * =====================================================================*/

    System.out.println("\n===========sep line==========\n");
    //A dynamic type Sample has been defined above, to use it directly, first declare a function for the dynamic type:
    Sample.setFunction("put", (self, su, arg) -> {
      System.out.println("Dynamic class instance handle:");
      su.invokeFunc("put", arg);//The semantics here are consistent with the direct definition of dynamic types
    }, Object.class, Object.class);

    //Test printing, observe the printing results, you can find that the lowest level of calling has changed during the calling process
    //In fact, in the dynamic object, except for the reference of some methods to java methods
    //the function reference at the lowest level points to the dynamic type
    //Therefore, when you change the behavior described in the dynamic type
    //the behavior of this method will change accordingly for all instances of this dynamic type that do not break the upward reference
    map.put("i", "string 8");
    map.put("j", "string 9");
    map.put("k", "string 10");

    System.out.println("\n===========sep line==========\n");

    //Similarly, if the new function
    Sample.setFunction("putTime", (self, arg) -> {
      System.out.println("current time: " + new Date());
      self.invokeFunc("put", arg.get(0), new Date().toString());
    }, String.class);
    dyMap.invokeFunc("putTime", "currTime");

    //Get the curr Time from the map, it can be seen that the behavior set for the dynamic type is synchronized to its instance
    System.out.println("get time entry, res: " + map.get("currTime"));

    System.out.println("\n===========sep line==========\n");

    //Of course, using lambdas to set behaviors for objects is somewhat cumbersome, and there is a more convenient form of defining methods for dynamic types
    //Below, we define a type Template as a type template, where the template type takes effect
    Sample.visitClass(Template.class, maker.getHelper());

    //test print
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

    //Test printing, observe the results, the object inherits the behavior of the put method from its superclass
    //and the behavior defined by this type is executed immediately
    //it is easy to see that the behavior of dynamic type extends is very similar to the class of java itself extends behavior
    map1.put("abc", "extend sample 1");
    map1.put("def", "extend sample 2");
    map1.put("ghi", "extend sample 3");

    //At this point, the basic usage instructions on dynamic types are over. Next, some specific applications will be shown in other files in the case.
    //Please master the basic usage methods before reading the subsequent cases.
  }

  //Use the template of java to define the method, let the object directly refer to its method from the java type, so that the program semantics are clearer
  static class Template{
    //The method used to define the behavior must be static. The this pointer is passed in by adding the @This annotation to the first parameter, and super is the second parameter using the @Super annotation
    //The position cannot be exchanged, but it can be omitted, such as here self is actually unnecessary
    //The parameters after this and super are signed with the name of the method
    //The signature of this example is equivalent to public Object get(Object key), which will override the behavior of map

    //In addition, when using dynamic types, the type of this pointer is indeterminate
    //so generally speaking, its generic-qualified class is not specified
    //but for dynamic classes that will only be used in a legal java class hierarchy
    //you can specify for It assigns an exact type for easier use
    public static Object get(@This DynamicObject<?> self, @Super DataPool.ReadOnlyPool su, Object key){
      System.out.println("getting entries, key: " + key);
      return su.invokeFunc("get", key);//The type is not exact here, so use reflection to call the method
    }
  }

  /**Define the aspect interface to limit the processing range of dynamic delegation*/
  @AspectInterface
  public interface Example{
    void put(Object k, Object v);
    Object get(Object k);
  }
}