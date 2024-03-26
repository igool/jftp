package jftp.client;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;

import jftp.client.auth.UserCredentials;
import jftp.connection.Connection;
import jftp.connection.ConnectionFactory;
import jftp.connection.FtpConnection;
import jftp.exception.FtpException;

import org.apache.commons.net.ftp.FTPClient;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

public class FtpClientTest {

    @InjectMocks
    public FtpClient ftpClient = new FtpClient();

    @Mock
    private FTPClient mockFtpClient;

    @Mock
    private ConnectionFactory mockConnectionFactory;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private String hostname;
    private int port;
    private UserCredentials userCredentials;

    @Before
    public void setUp() throws IOException {
        initMocks(this);

        hostname = "this is a hostname";
        port = 80;

        userCredentials = new UserCredentials("thisisausername", "thisisapassword");

        ftpClient.setHost(hostname);
        ftpClient.setPort(port);
        ftpClient.setCredentials(userCredentials);

        when(mockFtpClient.getReplyCode()).thenReturn(200);
        when(mockFtpClient.login(userCredentials.getUsername(), userCredentials.getPassword())).thenReturn(true);
        when(mockFtpClient.isConnected()).thenReturn(true);

        when(mockConnectionFactory.createFtpConnection(mockFtpClient)).thenReturn(new FtpConnection(mockFtpClient));
    }

    @Test
    public void newFtpClientShouldCreateFTPClientInstance() {
        assertThat(ftpClient.ftpClient, is(instanceOf(FTPClient.class)));
    }

    @Test
    public void connectMethodShouldCallonUnderlyingFtpClientConnectMethodWithHostname() throws SocketException, IOException {

        ftpClient.connect();

        verify(mockFtpClient).connect(hostname, port);
    }

    @Test
    public void connectMethodShouldEnterPassiveModeLoginToUnderlyingFtpClient() throws IOException {

        ftpClient.connect();

        InOrder inOrder = Mockito.inOrder(mockFtpClient);

        inOrder.verify(mockFtpClient).enterLocalPassiveMode();
        inOrder.verify(mockFtpClient).login(userCredentials.getUsername(), userCredentials.getPassword());
    }

    @Test
    public void connectMethodShouldSetKeepAliveCommandToEveryFiveMinutes() {

        ftpClient.connect();

        verify(mockFtpClient).setControlKeepAliveTimeout(300);
    }

    @Test
    public void onceLoggedInTheClientShouldHaveFileTypeSetToBinary() throws IOException {
        
        ftpClient.connect();

        InOrder inOrder = Mockito.inOrder(mockFtpClient);
        
        inOrder.verify(mockFtpClient).login(userCredentials.getUsername(), userCredentials.getPassword());
        inOrder.verify(mockFtpClient).setFileType(FTPClient.BINARY_FILE_TYPE);
    }

    @Test
    public void connectMethodShouldReturnNewFtpConnectionTakingInUnderlyingFtpClient() {

        Connection connection = ftpClient.connect();

        verify(mockConnectionFactory).createFtpConnection(mockFtpClient);
        assertThat(connection, is(instanceOf(FtpConnection.class)));
    }

    @Test
    public void disconnectMethodShouldCallOnUnderlyingFtpClientDisconnectMethod() throws IOException {

        ftpClient.disconnect();

        verify(mockFtpClient).disconnect();
    }

    @Test
    public void ifConnectionFailsThenCatchThrownExceptionAndThrowFtpException() throws SocketException, IOException {

        expectedException.expect(FtpException.class);
        expectedException.expectMessage(is(equalTo("Unable to connect to host " + hostname + " on port " + port)));

        doThrow(new IOException()).when(mockFtpClient).connect(hostname, port);

        ftpClient.connect();
    }

    @Test
    public void ifConnectionFailsDueToUnknownHostThenCatchThrownExceptionAndThrowFtpException() throws SocketException,
            IOException {

        expectedException.expect(FtpException.class);
        expectedException.expectMessage(is(equalTo("Unable to connect to host " + hostname + " on port " + port)));

        doThrow(new UnknownHostException()).when(mockFtpClient).connect(hostname, port);

        ftpClient.connect();
    }

    @Test
    public void ifUnderlyingClientReturnsBadConnectionCodeThenThrowConnectionException() {

        expectedException.expect(FtpException.class);
        expectedException
                .expectMessage(is(equalTo("The host " + hostname + " on port " + port + " returned a bad status code.")));

        when(mockFtpClient.getReplyCode()).thenReturn(500);

        ftpClient.connect();
    }

    @Test
    public void ifUnableToLoginToFtpClientThenThrowFtpException() throws IOException {

        expectedException.expect(FtpException.class);
        expectedException.expectMessage(is(equalTo("Unable to login for user " + userCredentials.getUsername())));

        when(mockFtpClient.login(userCredentials.getUsername(), userCredentials.getPassword())).thenReturn(false);

        ftpClient.connect();
    }

    @Test
    public void whenDisconnectingThenClientShouldCheckToSeeIfAlreadyDisconnected() {

        ftpClient.disconnect();

        verify(mockFtpClient).isConnected();
    }

    @Test
    public void whenAlreadyDisconnectedThenClientShoudlNotCallOnUnderlyingClientDisconnectMethod() throws IOException {

        when(mockFtpClient.isConnected()).thenReturn(false);

        ftpClient.disconnect();

        verify(mockFtpClient, times(0)).disconnect();
    }

    @Test
    public void whenClientIsStillConnectedThenShouldCallOnUnderlyingClientDisconnectMethod() throws IOException {

        ftpClient.disconnect();

        verify(mockFtpClient).disconnect();
    }

    @Test
    public void ifUnderlyingClientThrowsExceptionWhenDisconnectingThenClientShouldCatchAndRethrow() throws IOException {

        expectedException.expect(FtpException.class);
        expectedException.expectMessage(is(equalTo("There was an unexpected error while trying to disconnect.")));

        doThrow(new IOException()).when(mockFtpClient).disconnect();

        ftpClient.disconnect();
    }
}
