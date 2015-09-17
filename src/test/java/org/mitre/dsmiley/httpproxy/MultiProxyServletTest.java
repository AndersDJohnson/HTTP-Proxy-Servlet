package org.mitre.dsmiley.httpproxy;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.client.params.ClientPNames;
import org.junit.Test;

import java.util.Properties;

/**
 * @author David Smiley - dsmiley@mitre.org
 */
public class MultiProxyServletTest extends ServletAbstractTest
{
  private static final Log log = LogFactory.getLog(MultiProxyServletTest.class);

  @Override
  protected Properties getServletProperties() {
    Properties props = super.getServletProperties();
    props.setProperty(ClientPNames.HANDLE_REDIRECTS, "true");
    return props;
  }

  @Test
  public void testUrls() throws Exception {
    TestHelper.execAssert(sc, TestHelper.makeGetMethodRequest(sourceBaseUri + "/?urls=http://hc.apache.org&urls=http://google.com"));
  }

}
