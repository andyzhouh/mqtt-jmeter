package net.xmeter.emqtt.samplers;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.Interruptible;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.log.Priority;
import org.fusesource.mqtt.client.Future;
import org.fusesource.mqtt.client.FutureConnection;
import org.fusesource.mqtt.client.MQTT;
import org.fusesource.mqtt.client.QoS;
import org.fusesource.mqtt.client.Topic;

public class ConnectionSampler extends AbstractJavaSamplerClient implements Interruptible/*, TestStateListener */{
	private static final String SERVER = "SERVER";
	private static final String PORT = "PORT";
	private static final String KEEP_ALIVE = "KEEP_ALIVE";
	private static final String CLIENT_ID_PREFIX = "CLIENT_ID_PREFIX";
	private static final String CONN_TIMEOUT = "CONN_TIMEOUT　";
	
	private static final String CONN_ELAPSED_TIME = "CONN_ELAPSED_TIME";
	private static final int MAX_CLIENT_ID_LENGTH = 23;
	private MQTT mqtt = new MQTT();
	private FutureConnection connection = null;
	private int elpasedTime;
	private static AtomicBoolean sleepFlag = new AtomicBoolean(false);
	public ConnectionSampler() {
		//StandardJMeterEngine.register(this);
	}
	
	@Override
	public Arguments getDefaultParameters() {
		Arguments defaultParameters = new Arguments();
		defaultParameters.addArgument(SERVER, "tcp://10.91.41.81");
		defaultParameters.addArgument(PORT, "1883");
		defaultParameters.addArgument(KEEP_ALIVE, "5");
		defaultParameters.addArgument(CLIENT_ID_PREFIX, "xmeter_emqtt");
		defaultParameters.addArgument(CONN_TIMEOUT, "10");
		defaultParameters.addArgument(CONN_ELAPSED_TIME, "60");
		return defaultParameters;
	}
	
	@Override
	public SampleResult runTest(JavaSamplerContext context) {
		String serverAddr = context.getParameter(SERVER);
		int port = context.getIntParameter(PORT);
		
		int keepAlive = context.getIntParameter(KEEP_ALIVE);
		SampleResult result = new SampleResult();
		this.elpasedTime = context.getIntParameter(CONN_ELAPSED_TIME);
        result.sampleStart(); 
		try {
			
			if(serverAddr != null && (serverAddr.trim().toLowerCase().startsWith("ssl://"))) {
				mqtt.setSslContext(getContext());
			}
			mqtt.setHost(serverAddr + ":" + port);
			mqtt.setKeepAlive((short) keepAlive);
			String clientId = generateClientId(context.getParameter(CLIENT_ID_PREFIX));
			mqtt.setClientId(clientId);
			mqtt.setConnectAttemptsMax(0);
			mqtt.setReconnectAttemptsMax(0);
			
			connection = mqtt.futureConnection();
			
			Future<Void> f1 = connection.connect();
			f1.await(context.getIntParameter(CONN_TIMEOUT), TimeUnit.SECONDS);
			
			Topic[] topics = {new Topic("topic_"+ clientId, QoS.AT_LEAST_ONCE)};
			connection.subscribe(topics);
			
			result.sampleEnd(); 
            result.setSuccessful(true);
            result.setResponseData("Successful.".getBytes());
            result.setResponseMessage(MessageFormat.format("Connection {0} connected successfully.", connection));
            result.setResponseCodeOK(); 
		} catch (Exception e) {
			getLogger().log(Priority.ERROR, e.getMessage(), e);
			result.sampleEnd(); 
            result.setSuccessful(false);
            result.setResponseMessage(MessageFormat.format("Connection {0} connected failed.", connection));
            result.setResponseData("Failed.".getBytes());
            result.setResponseCode("500"); 
		}
		return result;
	}
	
	private static String generateClientId(String prefix) {
		int leng = prefix.length();
		int postLeng = MAX_CLIENT_ID_LENGTH - leng;
		UUID uuid = UUID.randomUUID();
		String string = uuid.toString().replace("-", "");
		String post = string.substring(0, postLeng);
		return prefix + post;
	}
	
	private static SSLContext getContext() throws Exception {
		SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
		sslContext.init(null, new TrustManager[] { new X509TrustManager() {
			public X509Certificate[] getAcceptedIssuers() {
				return null;
			}

			public void checkClientTrusted(X509Certificate[] certs, String authType) {
			}

			public void checkServerTrusted(X509Certificate[] certs, String authType) {
			}
		} }, new SecureRandom());
		return sslContext;
	}
	
	@Override
	public void teardownTest(JavaSamplerContext context) {
		try {
			if(!sleepFlag.get()) {
				TimeUnit.SECONDS.sleep(elpasedTime);	
				sleepFlag.set(true);
			}
			
			if(this.connection != null) {
				this.connection.disconnect();
				getLogger().log(Priority.INFO, MessageFormat.format("The connection {0} disconneted successfully.", connection));	
			}
		} catch (InterruptedException e) {
			getLogger().log(Priority.ERROR, e.getMessage(), e);
		}
	}

	@Override
	public boolean interrupt() {
		System.out.println("interrupt");
		return true;
	}

//	@Override
//	public void testEnded() {
//		getLogger().info(MessageFormat.format("Received end message {0}.", Thread.currentThread().getName()));
//		try {
//			
//			long before = System.currentTimeMillis();
//			while(true) {
//				if((System.currentTimeMillis() - before) < elpasedTime) {
//					TimeUnit.SECONDS.sleep(5);	
//				} else {
//					if(connection != null) {
//						//connection.disconnect();
//						getLogger().info(MessageFormat.format("Connection {0} disconnected successfully.", connection));
//						connection = null;
//					}
//				}
//			}
//		} catch (Exception e) {
//			getLogger().log(Priority.ERROR, e.getMessage(), e);
//		}
//	}
//
//	@Override
//	public void testEnded(String arg0) {
//		
//	}
//
//	@Override
//	public void testStarted() {
//		
//	}
//
//	@Override
//	public void testStarted(String arg0) {
//		testStarted();
//	}
}