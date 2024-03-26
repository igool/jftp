package jftp.client;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import jftp.client.auth.UserCredentials;
import jftp.connection.Connection;
import jftp.connection.ConnectionFactory;
import jftp.connection.SftpConnection;
import jftp.exception.FtpException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Answers;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

public class SftpClientTest {

	private static final String SFTP = "sftp";
	private static final String CONNECTION_ERROR_MESSAGE = "Unable to connect to host %s on port %d";

	@InjectMocks
	private SftpClient sftpClient = new SftpClient();

	@Mock(answer = Answers.RETURNS_DEEP_STUBS)
	private JSch mockJsch;

	@Mock
	private ConnectionFactory mockConnectionFactory;

	@Rule
	public ExpectedException expectedException = ExpectedException.none();
	
	private UserCredentials userCredentials;
	
	@Before
	public void setUp() throws JSchException {

		initMocks(this);

		userCredentials = new UserCredentials("user", "password");
		
		sftpClient.setHost("host");
		sftpClient.setPort(999);
		sftpClient.setCredentials(userCredentials);

		when(mockConnectionFactory.createSftpConnection(any(Channel.class))).thenReturn(new SftpConnection(new ChannelSftp()));
	}

	@Test
	public void connectMethodShouldCreateSessionUsingHostPortAndUsername() throws JSchException {

		sftpClient.connect();

		verify(mockJsch).getSession("user", "host", 999);
	}

	@Test
	public void sessionFromInitialConnectionNeedsConfigAndPasswordSettingBeforeConnecting() throws JSchException {

		Session mockSession = mockJsch.getSession("user", "host", 999);

		InOrder inOrder = Mockito.inOrder(mockSession);

		sftpClient.connect();

		inOrder.verify(mockSession).setConfig("StrictHostKeyChecking", "no");
		inOrder.verify(mockSession).setPassword("password");
		inOrder.verify(mockSession).connect();
	}

	@Test
	public void returnedSessionObjectShouldSetChannelToSftpAndOpen() throws JSchException {

		Session mockSession = mockJsch.getSession("user", "host", 999);

		sftpClient.connect();

		verify(mockSession).openChannel(SFTP);
	}
	
	@Test
	public void ifForAnyReasonTheUnderlyingSessionCantConnectThenCatchTheExceptionAndRethrow() throws JSchException {
		
		expectedException.expect(FtpException.class);
		expectedException.expectMessage(is(equalTo(String.format(CONNECTION_ERROR_MESSAGE, "host", 999))));
		
		Session mockSession = mockJsch.getSession("user", "host", 999);
		doThrow(new JSchException()).when(mockSession).connect();

		sftpClient.connect();
	}

	@Test
	public void sessionChannelShouldBeConnectedTo() throws JSchException {

		Session mockSession = mockJsch.getSession("user", "host", 999);
		Channel mockChannel = mockSession.openChannel(SFTP);

		sftpClient.connect();

		verify(mockChannel).connect();
	}

	@Test
	public void connectMethodShouldReturnLiveInstanceOfSftpChannelWrappedInStfpConnection() {

		Connection connection = sftpClient.connect();

		assertThat(connection, is(instanceOf(SftpConnection.class)));
	}
	
	@Test
	public void disconnectMethodShouldDisconnectUnderlyingChannelAndSession() throws JSchException {
		
		Session mockSession = mockJsch.getSession("user", "host", 999);
		Channel mockChannel = mockSession.openChannel(SFTP);
		
		sftpClient.connect();
		sftpClient.disconnect();
		
		verify(mockSession).disconnect();
		verify(mockChannel).disconnect();
	}
	
	@Test
	public void disconnectMethodShouldThrowExceptionWhenNotInitiallyConnected() {
		
		expectedException.expect(FtpException.class);
		expectedException.expectMessage(is(equalTo("The underlying connection was never initially made.")));
		
		sftpClient.disconnect();
		
	}
}
