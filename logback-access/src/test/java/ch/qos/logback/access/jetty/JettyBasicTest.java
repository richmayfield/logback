package ch.qos.logback.access.jetty;

import java.net.HttpURLConnection;
import java.net.URL;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import ch.qos.logback.access.spi.AccessEvent;
import ch.qos.logback.access.spi.Util;
import ch.qos.logback.core.appender.ListAppender;

public class JettyBasicTest extends TestCase {

  static RequestLogImpl requestLogImpl;

  public static Test suite() {
    TestSuite suite = new TestSuite();
    suite.addTestSuite(JettyBasicTest.class);
    requestLogImpl = new RequestLogImpl();
    return new JettyTestSetup(suite, requestLogImpl);
  }

  public void testGetRequest() throws Exception {
    URL url = new URL("http://localhost:8080/");
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setDoInput(true);

    String result = Util.readToString(connection.getInputStream());

    assertEquals("hello world", result);

    ListAppender appender = (ListAppender) requestLogImpl.getAppender("list");
    appender.list.clear();
  }

  public void testEventGoesToAppenders() throws Exception {
    URL url = new URL("http://localhost:8080/");
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setDoInput(true);

    String result = Util.readToString(connection.getInputStream());

    assertEquals("hello world", result);

    ListAppender appender = (ListAppender) requestLogImpl.getAppender("list");
    AccessEvent event = (AccessEvent) appender.list.get(0);
    assertEquals("127.0.0.1", event.getRemoteHost());
    assertEquals("localhost", event.getServerName());
    appender.list.clear();
  }

//  public void testPostContentConverter() throws Exception {
//    //System.out.println("into test");
//    URL url = new URL("http://localhost:8080/");
//    String msg = "test message";
//    
//    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
//    //this line is necessary to make the stream aware of when the message is over.
//    connection.setFixedLengthStreamingMode(msg.getBytes().length);
//    ((HttpURLConnection) connection).setRequestMethod("POST");
//    connection.setDoOutput(true);
//    connection.setDoInput(true);
//    connection.setUseCaches(false);
//    connection.setRequestProperty("Content-Type", "text/plain");
//
//   
//    PrintWriter output = new PrintWriter(new OutputStreamWriter(connection
//        .getOutputStream()));
//    output.print(msg);
//    output.flush();
//    output.close();
//
//    // StatusPrinter.print(requestLogImpl.getStatusManager());
//
//    ListAppender listAppender = (ListAppender) requestLogImpl
//        .getAppender("list");
//    Thread.sleep(100);
//    AccessEvent event = (AccessEvent) listAppender.list.get(0);
//    assertEquals(msg, event.getPostContent());
//  }
}