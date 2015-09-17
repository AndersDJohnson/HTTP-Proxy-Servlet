package org.mitre.dsmiley.httpproxy;

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;
import com.meterware.servletunit.ServletUnitClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.RequestLine;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.util.EntityUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import static org.junit.Assert.assertTrue;

/**
 *
 */
public class TestHelper {

  private static final Log log = LogFactory.getLog(MultiProxyServletTest.class);

  public static WebResponse execAssert(ServletUnitClient sc, GetMethodWebRequest request, String expectedUri) throws Exception {
    return execAndAssert(sc, request, expectedUri);
  }

  public static WebResponse execAssert(ServletUnitClient sc, GetMethodWebRequest request) throws Exception {
    return execAndAssert(sc, request,null);
  }

  public static WebResponse execAndAssert(ServletUnitClient sc, PostMethodWebRequest request) throws Exception {
    request.setParameter("abc","ABC");

    WebResponse rsp = execAndAssert(sc, request, null);

    assertTrue(rsp.getText().contains("ABC"));
    return rsp;
  }

  public static WebResponse execAndAssert(ServletUnitClient sc, WebRequest request, String expectedUri) throws Exception {
    WebResponse rsp = sc.getResponse( request );

    assertTrue(rsp.getResponseCode() < 400);

    return rsp;
  }


  public static GetMethodWebRequest makeGetMethodRequest(final String url) {
    return makeMethodRequest(url,GetMethodWebRequest.class);
  }

  public static PostMethodWebRequest makePostMethodRequest(final String url) {
    return makeMethodRequest(url,PostMethodWebRequest.class);
  }

  //Fixes problems in HttpUnit in which I can't specify the query string via the url. I don't want to use
  // setParam on a get request.
  @SuppressWarnings({"unchecked"})
  public static <M> M makeMethodRequest(String incomingUrl, Class<M> clazz) {
    log.info("Making request to url "+incomingUrl);
    final String url = rewriteMakeMethodUrl(incomingUrl);
    String urlNoQuery;
    final String queryString;
    int qIdx = url.indexOf('?');
    if (qIdx == -1) {
      urlNoQuery = url;
      queryString = null;
    } else {
      urlNoQuery = url.substring(0,qIdx);
      queryString = url.substring(qIdx + 1);

    }
    //WARNING: Ugly! Groovy could do this better.
    if (clazz == PostMethodWebRequest.class) {
      return (M) new PostMethodWebRequest(urlNoQuery) {
        @Override
        public String getQueryString() {
          return queryString;
        }
        @Override
        protected String getURLString() {
          return url;
        }
      };
    } else if (clazz == GetMethodWebRequest.class) {
      return (M) new GetMethodWebRequest(urlNoQuery) {
        @Override
        public String getQueryString() {
          return queryString;
        }
        @Override
        protected String getURLString() {
          return url;
        }
      };
    }
    throw new IllegalArgumentException(clazz.toString());
  }

  //subclass extended
  public static String rewriteMakeMethodUrl(String url) {
    return url;
  }

  /**
   * Writes all information about the request back to the response.
   */
  public static class RequestInfoHandler implements HttpRequestHandler
  {

    public void handle(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException, IOException {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      PrintWriter pw = new PrintWriter(baos,false);
      final RequestLine rl = request.getRequestLine();
      pw.println("REQUESTLINE: " + rl);

      for (Header header : request.getAllHeaders()) {
        pw.println(header.getName() + ": " + header.getValue());
      }
      pw.println("BODY: (below)");
      pw.flush();//done with pw now

      if (request instanceof HttpEntityEnclosingRequest) {
        HttpEntityEnclosingRequest enclosingRequest = (HttpEntityEnclosingRequest) request;
        HttpEntity entity = enclosingRequest.getEntity();
        byte[] body = EntityUtils.toByteArray(entity);
        baos.write(body);
      }

      response.setStatusCode(200);
      response.setReasonPhrase("TESTREASON");
      response.setEntity(new ByteArrayEntity(baos.toByteArray()));
    }
  }

}
