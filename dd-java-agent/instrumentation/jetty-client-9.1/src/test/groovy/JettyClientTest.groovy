import datadog.trace.agent.test.base.HttpClientTest
import datadog.trace.agent.test.checkpoints.CheckpointValidator
import datadog.trace.agent.test.checkpoints.CheckpointValidationMode
import org.eclipse.jetty.client.HttpClient
import org.eclipse.jetty.client.HttpProxy
import org.eclipse.jetty.client.api.Request
import org.eclipse.jetty.client.api.Response
import org.eclipse.jetty.client.api.Result
import org.eclipse.jetty.client.util.StringContentProvider
import org.eclipse.jetty.util.ssl.SslContextFactory
import spock.lang.Shared
import spock.lang.Subject

class JettyClientTest extends HttpClientTest {

  @Shared
  @Subject
  HttpClient client = new HttpClient(new SslContextFactory(true))

  @Shared
  HttpClient proxiedClient = new HttpClient(new SslContextFactory(true))

  def setupSpec() {
    client.connectTimeout = CONNECT_TIMEOUT_MS
    client.addressResolutionTimeout = CONNECT_TIMEOUT_MS
    client.idleTimeout = READ_TIMEOUT_MS
    client.stopTimeout = READ_TIMEOUT_MS
    client.start()

    proxiedClient.proxyConfiguration.proxies.add(new HttpProxy("localhost", proxy.port))
    proxiedClient.connectTimeout = CONNECT_TIMEOUT_MS
    proxiedClient.addressResolutionTimeout = CONNECT_TIMEOUT_MS
    proxiedClient.idleTimeout = READ_TIMEOUT_MS
    proxiedClient.stopTimeout = READ_TIMEOUT_MS
    proxiedClient.start()
  }

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, String body, Closure callback) {
    def proxy = uri.fragment != null && uri.fragment.equals("proxy")
    Request req = (proxy ? proxiedClient : client).newRequest(uri).method(method)
    headers.entrySet().each {
      req.header(it.key, it.value)
    }
    if (body) {
      req.content(new StringContentProvider(body))
    }
    if (callback) {
      req.onComplete(new Response.CompleteListener() {
          @Override
          void onComplete(Result result) {
            callback.call()
          }
        })
    }
    def resp = req.send()
    blockUntilChildSpansFinished(1)
    return resp.status
  }

  @Override
  CharSequence component() {
    return "jetty-client"
  }

  @Override
  boolean testRedirects() {
    false
  }

  @Override
  boolean testRemoteConnection() {
    false
  }

  @Override
  boolean testSecure() {
    true
  }

  @Override
  boolean testProxy() {
    false // doesn't produce CONNECT span.
  }

  @Override
  def setup() {
    CheckpointValidator.excludeValidations_DONOTUSE_I_REPEAT_DO_NOT_USE(
      CheckpointValidationMode.INTERVALS,
      CheckpointValidationMode.THREAD_SEQUENCE)
  }
}
