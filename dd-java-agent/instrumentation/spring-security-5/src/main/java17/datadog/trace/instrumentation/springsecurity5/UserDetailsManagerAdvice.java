package datadog.trace.instrumentation.springsecurity5;

import net.bytebuddy.asm.Advice;
import org.springframework.security.core.userdetails.UserDetails;

public class UserDetailsManagerAdvice {

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void onExit(@Advice.Argument(value = 0, readOnly = false) UserDetails user) {
    SpringSecurityUserEventDecorator.DECORATE.onSignup(user);
  }
}
