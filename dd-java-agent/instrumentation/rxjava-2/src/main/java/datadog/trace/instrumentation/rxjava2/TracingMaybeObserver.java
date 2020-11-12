package datadog.trace.instrumentation.rxjava2;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;

import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.context.TraceScope;
import io.reactivex.MaybeObserver;
import io.reactivex.disposables.Disposable;

public final class TracingMaybeObserver<T> implements MaybeObserver<T> {
  private final MaybeObserver<T> observer;
  private final AgentSpan parentSpan;

  public TracingMaybeObserver(final MaybeObserver<T> observer, final AgentSpan parentSpan) {
    this.observer = observer;
    this.parentSpan = parentSpan;
  }

  @Override
  public void onSubscribe(final Disposable d) {
    observer.onSubscribe(d);
  }

  @Override
  public void onSuccess(final T value) {
    try (final TraceScope scope = activateSpan(parentSpan)) {
      observer.onSuccess(value);
    }
  }

  @Override
  public void onError(final Throwable e) {
    try (final TraceScope scope = activateSpan(parentSpan)) {
      observer.onError(e);
    }
  }

  @Override
  public void onComplete() {
    try (final TraceScope scope = activateSpan(parentSpan)) {
      observer.onComplete();
    }
  }
}
