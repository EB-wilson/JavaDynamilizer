package dynamilize.base;

@FunctionalInterface
public interface Calculator<Type>{
  Type calculate(Type input);
}
