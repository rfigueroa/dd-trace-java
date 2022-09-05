package datadog.trace.agent.tooling.context;

import static datadog.trace.agent.tooling.bytebuddy.matcher.HierarchyMatchers.hasSuperType;
import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;

import datadog.trace.agent.tooling.AgentTransformerBuilder;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.bytebuddy.matcher.ShouldInjectFieldsRawMatcher;
import datadog.trace.api.Config;
import datadog.trace.bootstrap.InstrumentationContext;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stores context in a field injected into a class by redefining it; falls back to tracking the
 * context association in a global weak-map if the field wasn't injected, e.g. if the class was
 * already loaded.
 *
 * <p>This is accomplished by
 *
 * <ol>
 *   <li>Rewriting calls to {@link InstrumentationContext} to access stores based on numeric ids
 *   <li>Injecting fields in the earliest holder class that matches the context key
 *   <li>Injecting a getter and setter that retrieves context stored in the injected fields
 *   <li>Delegating to the superclass getter and setter if a superclass is also a context holder
 *   <li>Delegating to weak-map if neither this class or superclass have a field for the context
 * </ol>
 */
public final class FieldBackedContextProvider {

  private static final Logger log = LoggerFactory.getLogger(FieldBackedContextProvider.class);

  /*
   * Mapping from the instrumentations contextClassLoaderMatcher to a set of pairs (context holder, context class)
   * for which we have matchers installed. We use this to make sure we do not install matchers repeatedly for cases
   * when same context class is used by multiple instrumentations.
   */
  private static final HashMap<ElementMatcher<ClassLoader>, Set<Map.Entry<String, String>>>
      INSTALLED_CONTEXT_MATCHERS = new HashMap<>();

  private final String instrumenterName;
  private final ElementMatcher<ClassLoader> classLoaderMatcher;
  private final Map<String, String> contextStore;
  private final boolean fieldInjectionEnabled;

  public FieldBackedContextProvider(
      final Instrumenter.Default instrumenter,
      final ElementMatcher<ClassLoader> classLoaderMatcher,
      final Map<String, String> contextStore) {
    this.instrumenterName = instrumenter.getClass().getName();
    this.classLoaderMatcher = classLoaderMatcher;
    this.contextStore = contextStore;
    this.fieldInjectionEnabled = Config.get().isRuntimeContextFieldInjection();
  }

  public AgentBuilder.Identified.Extendable instrumentationTransformer(
      AgentBuilder.Identified.Extendable builder) {
    if (!contextStore.isEmpty()) {
      /*
       * Install transformer that rewrites accesses to context store with specialized bytecode that
       * invokes appropriate storage implementation.
       */
      builder =
          builder.transform(
              wrapVisitor(new FieldBackedContextRequestRewriter(contextStore, instrumenterName)));
    }
    return builder;
  }

  /** Clear set that prevents multiple matchers for same context class */
  public static void resetContextMatchers() {
    synchronized (INSTALLED_CONTEXT_MATCHERS) {
      INSTALLED_CONTEXT_MATCHERS.clear();
    }
  }

  public AgentBuilder.Identified.Extendable additionalInstrumentation(
      AgentBuilder.Identified.Extendable builder) {

    if (fieldInjectionEnabled) {
      for (Map.Entry<String, String> entry : contextStore.entrySet()) {
        /*
         * For each context store defined in a current instrumentation we create an agent builder
         * that injects necessary fields.
         * Note: this synchronization should not have any impact on performance
         * since this is done when agent builder is being made, it doesn't affect actual
         * class transformation.
         */
        Set<Map.Entry<String, String>> installedContextMatchers;
        synchronized (INSTALLED_CONTEXT_MATCHERS) {
          installedContextMatchers = INSTALLED_CONTEXT_MATCHERS.get(classLoaderMatcher);
          if (installedContextMatchers == null) {
            installedContextMatchers = new HashSet<>();
            INSTALLED_CONTEXT_MATCHERS.put(classLoaderMatcher, installedContextMatchers);
          }
        }
        synchronized (installedContextMatchers) {
          final String keyClassName = entry.getKey();
          final String contextClassName = entry.getValue();

          if (!installedContextMatchers.add(entry)) {
            // skip duplicate builder as we've already got one for this context store
            continue;
          }

          /*
           * For each context store defined in a current instrumentation we create an agent builder
           * that injects necessary fields.
           */
          builder =
              builder
                  .type(hasSuperType(named(keyClassName)), classLoaderMatcher)
                  .and(new ShouldInjectFieldsRawMatcher(keyClassName, contextClassName))
                  .and(AgentTransformerBuilder.NOT_DECORATOR_MATCHER)
                  .transform(
                      wrapVisitor(new FieldBackedContextInjector(keyClassName, contextClassName)));
        }
      }
    }
    return builder;
  }

  static AgentBuilder.Transformer wrapVisitor(final AsmVisitorWrapper visitor) {
    return new AgentBuilder.Transformer() {
      @Override
      public DynamicType.Builder<?> transform(
          final DynamicType.Builder<?> builder,
          final TypeDescription typeDescription,
          final ClassLoader classLoader,
          final JavaModule module) {
        return builder.visit(visitor);
      }
    };
  }
}
