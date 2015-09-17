package org.mitre.dsmiley.httpproxy;

import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.HeaderGroup;
import org.apache.http.util.EntityUtils;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Enumeration;

/**
 *
 */
public class ProxyHelper {

  /**
   * These are the "hop-by-hop" headers that should not be copied.
   * http://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html
   * I use an HttpClient HeaderGroup class instead of Set<String> because this
   * approach does case insensitive lookup faster.
   */
  protected static final HeaderGroup hopByHopHeaders;

  static {
    hopByHopHeaders = new HeaderGroup();
    String[] headers = new String[]{
        "Connection", "Keep-Alive", "Proxy-Authenticate", "Proxy-Authorization",
        "TE", "Trailers", "Transfer-Encoding", "Upgrade"};
    for (String header : headers) {
      hopByHopHeaders.addHeader(new BasicHeader(header, null));
    }
  }

  protected boolean doForwardIP = true;
  String servletName;

  public boolean isDoForwardIP() {
    return doForwardIP;
  }

  public void setDoForwardIP(boolean doForwardIP) {
    this.doForwardIP = doForwardIP;
  }

  public String getServletName() {
    return servletName;
  }

  public void setServletName(String servletName) {
    this.servletName = servletName;
  }

  private void log(String message, Throwable e) {
    System.out.println(message);
    e.printStackTrace();
  }

  public HttpRequest makeProxyRequest(HttpServletRequest servletRequest, URI uri) throws URISyntaxException, IOException {
    HttpRequest proxyRequest;
    String proxyRequestUri = uri.toString();

    // Make the Request
    //note: we won't transfer the protocol version because I'm not sure it would truly be compatible
    String method = servletRequest.getMethod();
    //spec: RFC 2616, sec 4.3: either of these two headers signal that there is a message body.
    if (servletRequest.getHeader(HttpHeaders.CONTENT_LENGTH) != null ||
        servletRequest.getHeader(HttpHeaders.TRANSFER_ENCODING) != null) {
      HttpEntityEnclosingRequest eProxyRequest = new BasicHttpEntityEnclosingRequest(method, proxyRequestUri);
      // Add the input entity (streamed)
      //  note: we don't bother ensuring we close the servletInputStream since the container handles it
      eProxyRequest.setEntity(new InputStreamEntity(servletRequest.getInputStream(), servletRequest.getContentLength()));
      proxyRequest = eProxyRequest;
    } else
      proxyRequest = new BasicHttpRequest(method, proxyRequestUri);

    HttpHost host = URIUtils.extractHost(uri);

    copyRequestHeaders(servletRequest, proxyRequest, host);

    setXForwardedForHeader(servletRequest, proxyRequest);

    return proxyRequest;
  }


  /**
   * Copy request headers from the servlet client to the proxy request.
   */
  protected void copyRequestHeaders(HttpServletRequest servletRequest, HttpRequest proxyRequest, HttpHost host) {
    // Get an Enumeration of all of the header names sent by the client
    Enumeration enumerationOfHeaderNames = servletRequest.getHeaderNames();
    while (enumerationOfHeaderNames.hasMoreElements()) {
      String headerName = (String) enumerationOfHeaderNames.nextElement();
      //Instead the content-length is effectively set via InputStreamEntity
      if (headerName.equalsIgnoreCase(HttpHeaders.CONTENT_LENGTH))
        continue;
      if (hopByHopHeaders.containsHeader(headerName))
        continue;

      Enumeration headers = servletRequest.getHeaders(headerName);
      while (headers.hasMoreElements()) {//sometimes more than one value
        String headerValue = (String) headers.nextElement();
        // In case the proxy host is running multiple virtual servers,
        // rewrite the Host header to ensure that we get content from
        // the correct virtual server
        if (headerName.equalsIgnoreCase(HttpHeaders.HOST)) {
          headerValue = host.getHostName();
          if (host.getPort() != -1)
            headerValue += ":" + host.getPort();
        } else if (headerName.equalsIgnoreCase(org.apache.http.cookie.SM.COOKIE)) {
          headerValue = getRealCookie(headerValue);
        }
        proxyRequest.addHeader(headerName, headerValue);
      }
    }
  }

  private void setXForwardedForHeader(HttpServletRequest servletRequest,
                                      HttpRequest proxyRequest) {
    String headerName = "X-Forwarded-For";
    if (doForwardIP) {
      String newHeader = servletRequest.getRemoteAddr();
      String existingHeader = servletRequest.getHeader(headerName);
      if (existingHeader != null) {
        newHeader = existingHeader + ", " + newHeader;
      }
      proxyRequest.setHeader(headerName, newHeader);
    }
  }


  /**
   * Take any client cookies that were originally from the proxy and prepare them to send to the
   * proxy.  This relies on cookie headers being set correctly according to RFC 6265 Sec 5.4.
   * This also blocks any local cookies from being sent to the proxy.
   */
  protected String getRealCookie(String cookieValue) {
    StringBuilder escapedCookie = new StringBuilder();
    String cookies[] = cookieValue.split("; ");
    for (String cookie : cookies) {
      String cookieSplit[] = cookie.split("=");
      if (cookieSplit.length == 2) {
        String cookieName = cookieSplit[0];
        if (cookieName.startsWith(getCookieNamePrefix())) {
          cookieName = cookieName.substring(getCookieNamePrefix().length());
          if (escapedCookie.length() > 0) {
            escapedCookie.append("; ");
          }
          escapedCookie.append(cookieName).append("=").append(cookieSplit[1]);
        }
      }

      cookieValue = escapedCookie.toString();
    }
    return cookieValue;
  }

  /**
   * The string prefixing rewritten cookies.
   */
  protected String getCookieNamePrefix() {
    return "!Proxy!" + servletName;
  }

  /**
   * HttpClient v4.1 doesn't have the
   * {@link org.apache.http.util.EntityUtils#consumeQuietly(org.apache.http.HttpEntity)} method.
   */
  public void consumeQuietly(HttpEntity entity) {
    try {
      EntityUtils.consume(entity);
    } catch (IOException e) {//ignore
      log(e.getMessage(), e);
    }
  }

}
