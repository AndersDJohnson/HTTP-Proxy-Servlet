package org.mitre.dsmiley.httpproxy;

/**
 * Copyright MITRE
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.AbortableHttpRequest;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * An HTTP reverse proxy/gateway servlet. It is designed to be extended for customization
 * if desired. Most of the work is handled by
 * <a href="http://hc.apache.org/httpcomponents-client-ga/">Apache HttpClient</a>.
 * <p>
 * There are alternatives to a servlet based proxy such as Apache mod_proxy if that is available to you. However
 * this servlet is easily customizable by Java, secure-able by your web application's security (e.g. spring-security),
 * portable across servlet engines, and is embeddable into another web application.
 * </p>
 * <p>
 * Inspiration: http://httpd.apache.org/docs/2.0/mod/mod_proxy.html
 * </p>
 *
 * @author David Smiley dsmiley@mitre.org
 */
public class MultiProxyServlet extends HttpServlet {

  /* INIT PARAMETER NAME CONSTANTS */

  /**
   * A boolean parameter name to enable logging of input and target URLs to the servlet log.
   */
  public static final String P_LOG = "log";

  /**
   * A boolean parameter name to enable forwarding of the client IP
   */
  public static final String P_FORWARDEDFOR = "forwardip";

  /* MISC */

  protected boolean doLog = false;
  protected boolean doForwardIP = true;

  private ServletHelper servletHelper;
  private HttpClient proxyClient;

  @Override
  public String getServletInfo() {
    return "A proxy servlet by David Smiley, dsmiley@apache.org";
  }

  @Override
  public void init() throws ServletException {
    servletHelper = new ServletHelper();
    servletHelper.setServletConfig(getServletConfig());
    String doLogStr = servletHelper.getConfigParam(P_LOG);
    if (doLogStr != null) {
      this.doLog = Boolean.parseBoolean(doLogStr);
    }

    String doForwardIPString = servletHelper.getConfigParam(P_FORWARDEDFOR);
    if (doForwardIPString != null) {
      this.doForwardIP = Boolean.parseBoolean(doForwardIPString);
    }

    HttpParams hcParams = new BasicHttpParams();
    hcParams.setParameter(ClientPNames.COOKIE_POLICY, CookiePolicy.IGNORE_COOKIES);
    servletHelper.readConfigParam(hcParams, ClientPNames.HANDLE_REDIRECTS, Boolean.class);
    proxyClient = servletHelper.createHttpClient(hcParams);
  }


  @Override
  public void destroy() {
    //As of HttpComponents v4.3, clients implement closeable
    if (proxyClient instanceof Closeable) {//TODO AutoCloseable in Java 1.6
      try {
        ((Closeable) proxyClient).close();
      } catch (IOException e) {
        log("While destroying servlet, shutting down HttpClient: " + e, e);
      }
    } else {
      //Older releases require we do this:
      if (proxyClient != null)
        proxyClient.getConnectionManager().shutdown();
    }
    super.destroy();
  }

  @Override
  protected void service(HttpServletRequest servletRequest, HttpServletResponse servletResponse)
      throws ServletException, IOException {

    ProxyHelper proxyHelper = new ProxyHelper();
    proxyHelper.setDoForwardIP(doForwardIP);
    proxyHelper.setServletName(getServletConfig().getServletName());


    String[] urls = servletRequest.getParameterValues("urls");

//    List<HttpResponse> proxyRequests = new ArrayList<HttpResponse>();
    List<HttpResponse> proxyResponses = new ArrayList<HttpResponse>();

    for (String proxyRequestUri : urls) {

      HttpRequest proxyRequest = null;
      HttpResponse proxyResponse = null;

      try {

        URI uri = new URI(proxyRequestUri);
        proxyRequest = proxyHelper.makeProxyRequest(servletRequest, uri);
        HttpHost host = URIUtils.extractHost(uri);

        proxyResponse = proxyClient.execute(host, proxyRequest);
        proxyResponses.add(proxyResponse);

      } catch (Exception e) {
        //abort request, according to best practice with HttpClient
        if (proxyRequest instanceof AbortableHttpRequest) {
          AbortableHttpRequest abortableHttpRequest = (AbortableHttpRequest) proxyRequest;
          abortableHttpRequest.abort();
        }
        if (e instanceof RuntimeException)
          throw (RuntimeException) e;
        if (e instanceof ServletException)
          throw (ServletException) e;
        //noinspection ConstantConditions
        if (e instanceof IOException)
          throw (IOException) e;
        throw new RuntimeException(e);

      } finally {
        // make sure the entire entity was consumed, so the connection is released
        if (proxyResponse != null)
          proxyHelper.consumeQuietly(proxyResponse.getEntity());
        //Note: Don't need to close servlet outputStream:
        // http://stackoverflow.com/questions/1159168/should-one-call-close-on-httpservletresponse-getoutputstream-getwriter
      }
    }

    StatusLine dominantStatusLine = findDominantStatusLine(proxyResponses);

    // Pass the dominant response code. This method with the "reason phrase" is deprecated but it's the only way to pass the
    //  reason along too.
    if (dominantStatusLine != null) {
      servletResponse.setStatus(dominantStatusLine.getStatusCode(), dominantStatusLine.getReasonPhrase());
    }

  }

  /**
   * Find the dominant response code - currently, the highest number.
   *
   * @param responses
   * @return
   */
  protected StatusLine findDominantStatusLine(List<HttpResponse> responses) {
    StatusLine dominantStatusLine = null;
    for (HttpResponse proxyResponse : responses) {
      StatusLine statusLine = proxyResponse.getStatusLine();
      if (dominantStatusLine == null) {
        dominantStatusLine = statusLine;
      } else {
        Integer ds = dominantStatusLine.getStatusCode();
        Integer s = statusLine.getStatusCode();
        if (ds < s) {
          dominantStatusLine = statusLine;
        }
      }
    }
    return dominantStatusLine;
  }

}
