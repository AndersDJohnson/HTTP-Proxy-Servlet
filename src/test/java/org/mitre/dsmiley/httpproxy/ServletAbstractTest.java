package org.mitre.dsmiley.httpproxy;

import com.meterware.servletunit.ServletRunner;
import com.meterware.servletunit.ServletUnitClient;
import org.apache.http.localserver.LocalTestServer;
import org.junit.After;
import org.junit.Before;

import java.util.Properties;

/**
 *
 */
public abstract class ServletAbstractTest {

  /**
   * From Apache httpcomponents/httpclient. Note httpunit has a similar thing called PseudoServlet but it is
   * not as good since you can't even make it echo the request back.
   */
  protected LocalTestServer localTestServer;

  /** From Meterware httpunit. */
  protected ServletRunner servletRunner;
  protected ServletUnitClient sc;

  protected String targetBaseUri;
  protected String sourceBaseUri;

  protected String servletName = MultiProxyServlet.class.getName();
  protected String servletPath = "/proxyMe";


  protected Properties getServletProperties() {
    Properties servletProps = new Properties();
    servletProps.setProperty("http.protocol.handle-redirects", "false");
    servletProps.setProperty(MultiProxyServlet.P_LOG, "true");
    servletProps.setProperty(MultiProxyServlet.P_FORWARDEDFOR, "true");
    return servletProps;
  }

  @Before
  public void setUp() throws Exception {
    localTestServer = new LocalTestServer(null, null);
    localTestServer.start();
    localTestServer.register("/targetPath*", new TestHelper.RequestInfoHandler());//matches /targetPath and /targetPath/blahblah

    servletRunner = new ServletRunner();

    Properties servletProps = getServletProperties();
    setUpServlet(servletProps);

    sc = servletRunner.newClient();
    sc.getClientProperties().setAutoRedirect(false);//don't want httpunit itself to redirect

  }

  protected void setUpServlet(Properties servletProps) {
    servletProps.putAll(servletProps);
    targetBaseUri = "http://localhost:"+localTestServer.getServiceAddress().getPort()+"/targetPath";
    servletProps.setProperty("targetUri", targetBaseUri);
    servletRunner.registerServlet(servletPath + "/*", servletName, servletProps);//also matches /proxyMe (no path info)
    sourceBaseUri = "http://localhost/proxyMe";//localhost:0 is hard-coded in ServletUnitHttpRequest
  }

  @After
  public void tearDown() throws Exception {
    servletRunner.shutDown();
    localTestServer.stop();
  }

}
