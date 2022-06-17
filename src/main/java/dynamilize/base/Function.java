package dynamilize.base;

@FunctionalInterface
public interface Function<S, R>{
  R invoke(S self, Object... args);
}
