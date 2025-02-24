package com.datadog.iast.sink;

import static com.datadog.iast.taint.Ranges.rangesProviderFor;
import static com.datadog.iast.taint.Tainteds.canBeTainted;

import com.datadog.iast.IastRequestContext;
import com.datadog.iast.model.VulnerabilityType;
import com.datadog.iast.taint.TaintedObjects;
import datadog.trace.api.iast.sink.XssModule;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.AgentTracer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class XssModuleImpl extends SinkModuleBase implements XssModule {

  @Override
  public void onXss(@Nonnull String s) {
    if (!canBeTainted(s)) {
      return;
    }
    final AgentSpan span = AgentTracer.activeSpan();
    final IastRequestContext ctx = IastRequestContext.get(span);
    if (ctx == null) {
      return;
    }
    checkInjection(span, ctx, VulnerabilityType.XSS, s);
  }

  @Override
  public void onXss(@Nonnull char[] array) {
    if (array == null || array.length == 0) {
      return;
    }
    final AgentSpan span = AgentTracer.activeSpan();
    final IastRequestContext ctx = IastRequestContext.get(span);
    if (ctx == null) {
      return;
    }
    checkInjection(span, ctx, VulnerabilityType.XSS, array);
  }

  @Override
  public void onXss(@Nonnull String format, @Nullable Object[] args) {
    if ((args == null || args.length == 0) && !canBeTainted(format)) {
      return;
    }
    final AgentSpan span = AgentTracer.activeSpan();
    final IastRequestContext ctx = IastRequestContext.get(span);
    if (ctx == null) {
      return;
    }
    final TaintedObjects to = ctx.getTaintedObjects();
    checkInjection(
        span, VulnerabilityType.XSS, rangesProviderFor(to, format), rangesProviderFor(to, args));
  }
}
