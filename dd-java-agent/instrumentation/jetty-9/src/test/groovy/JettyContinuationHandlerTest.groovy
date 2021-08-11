import datadog.trace.agent.test.checkpoints.CheckpointValidator
import datadog.trace.agent.test.checkpoints.CheckpointValidationMode
import org.eclipse.jetty.continuation.Continuation
import org.eclipse.jetty.continuation.ContinuationSupport
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.handler.AbstractHandler

import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class JettyContinuationHandlerTest extends Jetty9Test {

  @Override
  def setup() {
    CheckpointValidator.excludeValidations_DONOTUSE_I_REPEAT_DO_NOT_USE(
      CheckpointValidationMode.INTERVALS,
      CheckpointValidationMode.SUSPEND_RESUME,
      CheckpointValidationMode.THREAD_SEQUENCE)
  }

  @Override
  AbstractHandler handler() {
    ContinuationTestHandler.INSTANCE
  }

  static class ContinuationTestHandler extends AbstractHandler {
    static final ContinuationTestHandler INSTANCE = new ContinuationTestHandler()
    final ExecutorService executorService = Executors.newSingleThreadExecutor()

    @Override
    void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
      final Continuation continuation = ContinuationSupport.getContinuation(request)
      if (continuation.initial) {
        continuation.suspend()
        executorService.execute {
          continuation.resume()
        }
      } else {
        handleRequest(baseRequest, response)
      }
      baseRequest.handled = true
    }
  }
}
