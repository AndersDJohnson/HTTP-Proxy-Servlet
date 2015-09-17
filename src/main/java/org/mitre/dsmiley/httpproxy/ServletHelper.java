package org.mitre.dsmiley.httpproxy;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.HttpParams;

import javax.servlet.ServletConfig;
import java.lang.reflect.Constructor;

/**
 *
 */
public class ServletHelper {

  protected ServletConfig servletConfig;

  public ServletConfig getServletConfig() {
    return servletConfig;
  }

  public void setServletConfig(ServletConfig servletConfig) {
    this.servletConfig = servletConfig;
  }

  /**
   * Reads a configuration parameter. By default it reads servlet init parameters but
   * it can be overridden.
   */
  protected String getConfigParam(String key) {
    return servletConfig.getInitParameter(key);
  }

  /**
   * Called from {@link javax.servlet.http.HttpServlet#init(javax.servlet.ServletConfig)}. HttpClient offers many opportunities
   * for customization. By default,
   * <a href="http://hc.apache.org/httpcomponents-client-ga/httpclient/apidocs/org/apache/http/impl/client/SystemDefaultHttpClient.html">
   * SystemDefaultHttpClient</a> is used if available, otherwise it falls
   * back to:
   * <pre>new DefaultHttpClient(new ThreadSafeClientConnManager(),hcParams)</pre>
   * SystemDefaultHttpClient uses PoolingClientConnectionManager. In any case, it should be thread-safe.
   */
  @SuppressWarnings({"unchecked", "deprecation"})
  protected HttpClient createHttpClient(HttpParams hcParams) {
    try {
      //as of HttpComponents v4.2, this class is better since it uses System
      // Properties:
      Class clientClazz = Class.forName("org.apache.http.impl.client.SystemDefaultHttpClient");
      Constructor constructor = clientClazz.getConstructor(HttpParams.class);
      return (HttpClient) constructor.newInstance(hcParams);
    } catch (ClassNotFoundException e) {
      //no problem; use v4.1 below
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    //Fallback on using older client:
    return new DefaultHttpClient(new ThreadSafeClientConnManager(), hcParams);
  }

  /**
   * Reads a servlet config parameter by the name {@code hcParamName} of type {@code type}, and
   * set it in {@code hcParams}.
   */
  protected void readConfigParam(HttpParams hcParams, String hcParamName, Class type) {
    String val_str = getConfigParam(hcParamName);
    if (val_str == null)
      return;
    Object val_obj;
    if (type == String.class) {
      val_obj = val_str;
    } else {
      try {
        //noinspection unchecked
        val_obj = type.getMethod("valueOf", String.class).invoke(type, val_str);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    hcParams.setParameter(hcParamName, val_obj);
  }

}
