package jftp.connection;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.List;

import jftp.exception.FtpException;
import jftp.util.FileStreamFactory;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

public class FtpConnectionTest {

    private static final String LOCAL_DIRECTORY = ".";
    private static final String DIRECTORY_PATH = "this/is/a/directory";

    @InjectMocks
    private FtpConnection ftpConnection;

    @Mock
    private FileStreamFactory mockFileStreamFactory;

    @Mock
    private FileInputStream mockFileInputStream;

    @Mock
    private FileOutputStream mockFileOutputStream;

    private FTPClient mockFtpClient;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp() throws IOException {

        mockFtpClient = mock(FTPClient.class);

        when(mockFtpClient.changeWorkingDirectory(anyString())).thenReturn(true);
        when(mockFtpClient.printWorkingDirectory()).thenReturn(DIRECTORY_PATH);
        when(mockFtpClient.retrieveFile(anyString(), any(OutputStream.class))).thenReturn(true);

        FTPFile[] files = createRemoteFTPFiles();

        ftpConnection = new FtpConnection(mockFtpClient);

        initMocks(this);

        when(mockFtpClient.listFiles(anyString())).thenReturn(files);

        when(mockFileStreamFactory.createInputStream(anyString())).thenReturn(mockFileInputStream);
        when(mockFileStreamFactory.createOutputStream(anyString())).thenReturn(mockFileOutputStream);

        when(mockFtpClient.storeFile("remote/directory/path.txt", mockFileInputStream)).thenReturn(true);
    }

    @Test
    public void whenSettingDirectoryThenFtpClientShouldBeCalledToChangeDirectory() throws IOException {

        ftpConnection.changeDirectory(DIRECTORY_PATH);

        verify(mockFtpClient).changeWorkingDirectory(DIRECTORY_PATH);
    }

    @Test
    public void whenRemoteServerThrowsExceptionWhenChangingDirectoryThenConnectionShouldCatchAndRethrow() throws IOException {

        expectedException.expect(FtpException.class);
        expectedException.expectMessage(is(equalTo("Remote server was unable to change directory.")));

        when(mockFtpClient.changeWorkingDirectory(DIRECTORY_PATH)).thenThrow(new IOException());

        ftpConnection.changeDirectory(DIRECTORY_PATH);
    }

    @Test
    public void ifFtpClientReturnsFalseWhenChangingDirectoryThenThrowNoSuchDirectoryException() throws IOException {

        expectedException.expect(FtpException.class);
        expectedException.expectMessage(is(equalTo("The directory this/is/a/directory doesn't exist on the remote server.")));

        when(mockFtpClient.changeWorkingDirectory(DIRECTORY_PATH)).thenReturn(false);

        ftpConnection.changeDirectory(DIRECTORY_PATH);
    }

    @Test
    public void whenListingFilesThenFtpClientListFilesMethodShouldBeCalledForCurrentWorkingDirectory() throws IOException {

        ftpConnection.listFiles();

        verify(mockFtpClient).listFiles(DIRECTORY_PATH);
    }

    @Test
    public void ifWhenListingFilesFtpClientThrowsExceptionThenCatchAndRethrowFileListingExcepton() throws IOException {

        expectedException.expect(FtpException.class);
        expectedException.expectMessage(is(equalTo("Unable to list files in directory " + DIRECTORY_PATH)));

        when(mockFtpClient.listFiles(DIRECTORY_PATH)).thenThrow(new IOException());

        ftpConnection.listFiles();
    }

    @Test
    public void whenListingFilesThenFileArrayThatListFilesReturnsShouldBeConvertedToListOfFtpFilesAndReturned()
            throws IOException {

        ftpConnection.changeDirectory(DIRECTORY_PATH);

        List<FtpFile> returnedFiles = ftpConnection.listFiles();

        assertThat(returnedFiles.get(0).getName(), is(equalTo("File 1")));
        assertThat(returnedFiles.get(0).getSize(), is(equalTo(1000l)));
        assertThat(returnedFiles.get(0).getFullPath(), is(equalTo(DIRECTORY_PATH + "/File 1")));
        assertThat(returnedFiles.get(0).isDirectory(), is(equalTo(false)));

        assertThat(returnedFiles.get(1).getName(), is(equalTo("File 2")));
        assertThat(returnedFiles.get(1).getSize(), is(equalTo(2000l)));
        assertThat(returnedFiles.get(1).getFullPath(), is(equalTo(DIRECTORY_PATH + "/File 2")));
        assertThat(returnedFiles.get(1).isDirectory(), is(equalTo(true)));

        assertThat(returnedFiles.get(2).getName(), is(equalTo("File 3")));
        assertThat(returnedFiles.get(2).getSize(), is(equalTo(3000l)));
        assertThat(returnedFiles.get(2).getFullPath(), is(equalTo(DIRECTORY_PATH + "/File 3")));
        assertThat(returnedFiles.get(2).isDirectory(), is(equalTo(false)));
    }

    @Test
    public void returnedFtpFilesShouldHaveCorrectModifiedDateTimesAgainstThem() {

        List<FtpFile> files = ftpConnection.listFiles();

        assertThat(files.get(0).getLastModified().toString("dd/MM/yyyy HH:mm:ss"), is(equalTo("19/03/2014 21:40:00")));
        assertThat(files.get(1).getLastModified().toString("dd/MM/yyyy HH:mm:ss"), is(equalTo("19/03/2014 21:40:00")));
        assertThat(files.get(2).getLastModified().toString("dd/MM/yyyy HH:mm:ss"), is(equalTo("19/03/2014 21:40:00")));
    }

    @Test
    public void whenListingFilesAndGivingRelativePathThenThatPathShouldBeUsedAlongsideCurrentWorkingDir() throws IOException {
                
        when(mockFtpClient.printWorkingDirectory()).thenReturn(DIRECTORY_PATH + "/relativePath");
        
        ftpConnection.listFiles("relativePath");

        verify(mockFtpClient).listFiles(DIRECTORY_PATH + "/relativePath");
    }
    
    @Test
    public void downloadMethodShouldCreateLocalFileStreamFromCorrectPathBasedOnRemoteFileName() throws FileNotFoundException {

        ftpConnection.download("path/to/remote.file", LOCAL_DIRECTORY);
        
        verify(mockFileStreamFactory).createOutputStream(LOCAL_DIRECTORY + "/remote.file"); 
    }

    @Test
    public void downloadMethodShouldCallOnFtpClientRetrieveFilesMethodWithRemoteFilename() throws IOException {

        ftpConnection.download("path/to/remote.file", LOCAL_DIRECTORY);

        verify(mockFtpClient).retrieveFile("path/to/remote.file", mockFileOutputStream);
    }

    @Test
    public void downloadMethodShouldThrowExceptionIfUnableToOpenStreamToLocalFile() throws IOException {

        expectedException.expect(FtpException.class);
        expectedException
                .expectMessage(is(equalTo("Unable to write to local directory " + LOCAL_DIRECTORY + "/remote.file")));

        when(mockFtpClient.retrieveFile("path/to/remote.file", mockFileOutputStream)).thenThrow(new FileNotFoundException());

        ftpConnection.download("path/to/remote.file", LOCAL_DIRECTORY);
    }

    @Test
    public void shouldDownloadFailForAnyReasonWhileInProgressThenCatchIOExceptionAndThrowNewDownloadFailedException()
            throws IOException {

        expectedException.expect(FtpException.class);
        expectedException.expectMessage(is(equalTo("Unable to download file path/to/remote.file")));

        when(mockFtpClient.retrieveFile("path/to/remote.file", mockFileOutputStream)).thenThrow(new IOException());

        ftpConnection.download("path/to/remote.file", LOCAL_DIRECTORY);
    }

    @Test
    public void ifRetrieveFileMethodInClientReturnsFalseThenThrowDownloadFailedException() throws IOException {

        expectedException.expect(FtpException.class);
        expectedException.expectMessage(is(equalTo("Server returned failure while downloading.")));

        when(mockFtpClient.retrieveFile("path/to/remote.file", mockFileOutputStream)).thenReturn(false);

        ftpConnection.download("path/to/remote.file", LOCAL_DIRECTORY);
    }

    @Test
    public void uploadingFileShouldCreateFileInputStreamAndPassIntoStoreFileMethodOnUnderlyingClient() throws IOException {

        ftpConnection.upload("local/file/path.txt", "remote/directory");

        verify(mockFtpClient).storeFile("remote/directory/path.txt", mockFileInputStream);
    }

    @Test
    public void ifClientStoreFileReturnsFalseThenExceptionShouldBeThrown() throws IOException {

        expectedException.expect(FtpException.class);
        expectedException.expectMessage(is(equalTo("Upload failed.")));

        when(mockFtpClient.storeFile("remote/directory/path.txt", mockFileInputStream)).thenReturn(false);

        ftpConnection.upload("local/file/path.txt", "remote/directory");
    }

    @Test
    public void clientUploadShouldBeSafeWhenIncludingDirectorySlashAtEndofPath() throws IOException {
        
        ftpConnection.upload("local/file/path.txt", "remote/directory/");

        verify(mockFtpClient).storeFile("remote/directory/path.txt", mockFileInputStream);
    }

    @Test
    public void fileStreamShouldBeClosedAfterUploadAttempts() throws IOException {

        ftpConnection.upload("local/file/path.txt", "remote/directory");

        InOrder inOrder = Mockito.inOrder(mockFtpClient, mockFileInputStream);

        inOrder.verify(mockFtpClient).storeFile("remote/directory/path.txt", mockFileInputStream);
        inOrder.verify(mockFileInputStream).close();
    }
    
    @Test
    public void ifStreamCannotBeOpenedWhileUploadingThenExceptionShouldBeCaughtAndRethrown() throws FileNotFoundException {
        
        expectedException.expect(FtpException.class);
        expectedException.expectMessage(is(equalTo("Could not find file: local/file/to/upload.txt")));
        
        when(mockFileStreamFactory.createInputStream("local/file/to/upload.txt")).thenThrow(new FileNotFoundException());
        
        ftpConnection.upload("local/file/to/upload.txt", "remote/directory");
    }
    
    @Test
    public void ifUploadingIsInterruptedByAnIOIssueThenExceptionShouldBeCaughtAndRethrown() throws IOException {
        
        expectedException.expect(FtpException.class);
        expectedException.expectMessage(is(equalTo("Upload may not have completed.")));
        
        when(mockFtpClient.storeFile("remote/directory/upload.txt", mockFileInputStream)).thenThrow(new IOException());
        
        ftpConnection.upload("local/file/to/upload.txt", "remote/directory");
    }
    
    @Test
    public void printingWorkingDirectoryShouldCallOnUnderlyingClientMethodToGetCurrentDirectory() throws IOException {
        
        ftpConnection.printWorkingDirectory();
        
        verify(mockFtpClient).printWorkingDirectory();
    }
    
    @Test
    public void printingWorkingDirectoryShouldReturnExactlyWhatTheUnderlyingClientReturns() {
        
        assertThat(ftpConnection.printWorkingDirectory(), is(equalTo(DIRECTORY_PATH)));
    }
    
    @Test
    public void ifClientThrowsExceptionWhenTryingToGetWorkingDirectoryThenCatchExceptionAndRethrow() throws IOException {
        
        expectedException.expect(FtpException.class);
        expectedException.expectMessage(is(equalTo("Unable to print the working directory")));
        
        when(mockFtpClient.printWorkingDirectory()).thenThrow(new IOException());
        
        ftpConnection.printWorkingDirectory();
    }

    @Test
    public void whenListingFilesOnDifferentPathTheClientShouldCDToThatPathThenCDBackWhenFinished() throws IOException {
        
        when(mockFtpClient.printWorkingDirectory()).thenReturn("initial/directory").thenReturn("another/path");
        
        ftpConnection.changeDirectory("initial/directory");        
        ftpConnection.listFiles("another/path");
        
        InOrder inOrder = Mockito.inOrder(mockFtpClient);
        
        inOrder.verify(mockFtpClient).printWorkingDirectory();
        inOrder.verify(mockFtpClient).changeWorkingDirectory("another/path");
        inOrder.verify(mockFtpClient).printWorkingDirectory();
        inOrder.verify(mockFtpClient).listFiles("another/path");
        inOrder.verify(mockFtpClient).changeWorkingDirectory("initial/directory");
    }
    
    private FTPFile[] createRemoteFTPFiles() {

        Calendar calendar = Calendar.getInstance();
        calendar.set(2014, 2, 19, 21, 40, 00);

        FTPFile[] files = new FTPFile[3];

        for (int i = 0; i < 3; i++) {

            FTPFile file = mock(FTPFile.class);

            when(file.getName()).thenReturn("File " + (i + 1));
            when(file.getSize()).thenReturn((long) (i + 1) * 1000);
            when(file.getTimestamp()).thenReturn(calendar);
            when(file.isDirectory()).thenReturn(setTrueIfNumberIsEven(i));

            files[i] = file;
        }

        return files;
    }

    private boolean setTrueIfNumberIsEven(int i) {
        return (i + 1) % 2 == 0 ? true : false;
    }
}
