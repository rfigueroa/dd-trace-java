package datadog.trace.instrumentation.jacoco;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.nameEndsWith;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.Config;
import java.util.Set;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class InstrumenterInstrumentation extends Instrumenter.CiVisibility
    implements Instrumenter.ForTypeHierarchy {
  public InstrumenterInstrumentation() {
    super("jacoco");
  }

  @Override
  public boolean isApplicable(Set<TargetSystem> enabledSystems) {
    return super.isApplicable(enabledSystems) && Config.get().isCiVisibilityCodeCoverageEnabled();
  }

  @Override
  public String hierarchyMarkerType() {
    return "org.jacoco.agent.rt.IAgent";
  }

  @Override
  public ElementMatcher<TypeDescription> hierarchyMatcher() {
    // The jacoco javaagent jar that is published relocates internal classes to an "obfuscated"
    // package name ex. org.jacoco.agent.rt.internal_72ddf3b.core.instr.Instrumenter
    return nameStartsWith("org.jacoco.agent.rt.internal")
        .and(nameEndsWith(".core.instr.Instrumenter"));
  }

  @Override
  public void adviceTransformations(AdviceTransformation transformation) {
    transformation.applyAdvice(
        isMethod().and(named("instrument")).and(takesArguments(byte[].class)),
        getClass().getName() + "$InstrumentAdvice");
  }

  public static class InstrumentAdvice {
    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    static byte[] enter(@Advice.Argument(0) byte[] bytes) {
      // we cannot insert our custom Jacoco probes into classes compiled with Java older than 1.5
      // because there is no support for pushing types onto the stack
      int majorVersion = ((bytes[6] & 0xFF) << 8) | (bytes[7] & 0xFF);
      if (majorVersion < 49) {
        return bytes; // skip class instrumentation
      } else {
        return null; // go ahead and instrument
      }
    }
  }
}
