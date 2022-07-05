package dynamilize;

@FunctionalInterface
public interface Function<S, R>{
  R invoke(S self, ArgumentList args);
}
