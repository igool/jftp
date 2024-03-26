package jftp.connection;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Vector;

import jftp.exception.FtpException;
import jftp.util.FileStreamFactory;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelSftp.LsEntry;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;

public class SftpConnectionTest {

    private ChannelSftp mockChannel;

    private static final String DIRECTORY = "this/is/the/pwd";

    @Mock
    private FileStreamFactory mockFileStreamFactory;
    
    @InjectMocks
    private SftpConnection sftpConnection;
    
    @Mock
    private FileInputStream mockFileInputStream;
    
    @Mock
    private FileOutputStream mockFileOutputStream;
    
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp() throws SftpException, FileNotFoundException {

        mockChannel = mock(ChannelSftp.class);

        Vector<LsEntry> lsEntries = createEntries();
        
        when(mockChannel.ls(anyString())).thenReturn(lsEntries);
        when(mockChannel.pwd()).thenReturn(DIRECTORY);

        sftpConnection = new SftpConnection(mockChannel);
        
        initMocks(this);
        
        when(mockFileStreamFactory.createInputStream(anyString())).thenReturn(mockFileInputStream);
        when(mockFileStreamFactory.createOutputStream(anyString())).thenReturn(mockFileOutputStream);
    }

    @Test
    public void setDirectoryShouldCallOnChannelLsCommandWithDirectoryPath() throws SftpException {

        String directory = "directory/path";

        sftpConnection.changeDirectory(directory);

        verify(mockChannel).cd(directory);
    }

    @Test
    public void whenDirectoryDoesNotExistThenNoSuchDirectoryExceptionShouldBeThrown() throws SftpException {

        expectedException.expect(FtpException.class);
        expectedException.expectMessage(is(equalTo("Directory not/a/directory does not exist.")));

        String directory = "not/a/directory";

        doThrow(new SftpException(0, "")).when(mockChannel).cd(directory);

        sftpConnection.changeDirectory(directory);
    }

    @Test
    public void listFilesMethodShouldCallOnChannelLsMethodForPresentDirectory() throws SftpException {

        sftpConnection.listFiles();

        verify(mockChannel).ls(DIRECTORY);
    }

    @Test
    public void whenListingFilesGivingRelativePathThenChannelLsMethodShouldUseGivenPath() throws SftpException {

        when(mockChannel.pwd()).thenReturn(DIRECTORY + "/some/other/path");
        
        sftpConnection.listFiles("some/other/path");

        verify(mockChannel).ls(DIRECTORY + "/some/other/path");
    }
    
    @Test
    public void ifUnderlyingChannelIsUnableToListFilesInPWDThenExceptionShouldBeCaughtAndRethrown() throws SftpException {
        
        expectedException.expect(FtpException.class);
        expectedException.expectMessage(is(equalTo("Unable to list files in directory " + DIRECTORY)));
        
        when(mockChannel.ls(DIRECTORY)).thenThrow(new SftpException(0, ""));
        
        sftpConnection.listFiles();
    }
    
    @Test
    public void whenListingFilesInNewDirectoryThenChannelShouldCDThenListThenCDBackToRetainPWD() throws SftpException {
        
        when(mockChannel.pwd()).thenReturn("initial/directory").thenReturn("another/path");
        
        sftpConnection.changeDirectory("initial/directory");        
        sftpConnection.listFiles("another/path");
        
        InOrder inOrder = Mockito.inOrder(mockChannel);
        
        inOrder.verify(mockChannel).pwd();
        inOrder.verify(mockChannel).cd("another/path");
        inOrder.verify(mockChannel).pwd();
        inOrder.verify(mockChannel).ls("another/path");
        inOrder.verify(mockChannel).cd("initial/directory");
    }

    @Test
    public void lsEntriesReturnedFromChannelShouldBeParsedIntoFtpFileAndReturnedInList() {

        sftpConnection.changeDirectory(DIRECTORY);

        List<FtpFile> files = sftpConnection.listFiles();

        assertThat(files.get(0).getName(), is(equalTo("File 1")));
        assertThat(files.get(0).getSize(), is(equalTo(123l)));
        assertThat(files.get(0).getFullPath(), is(equalTo(DIRECTORY + "/File 1")));
        assertThat(files.get(0).isDirectory(), is(equalTo(true)));

        assertThat(files.get(1).getName(), is(equalTo("File 2")));
        assertThat(files.get(1).getSize(), is(equalTo(456l)));
        assertThat(files.get(1).getFullPath(), is(equalTo(DIRECTORY + "/File 2")));
        assertThat(files.get(1).isDirectory(), is(equalTo(false)));

        assertThat(files.get(2).getName(), is(equalTo("File 3")));
        assertThat(files.get(2).getSize(), is(equalTo(789l)));
        assertThat(files.get(2).getFullPath(), is(equalTo(DIRECTORY + "/File 3")));
        assertThat(files.get(2).isDirectory(), is(equalTo(true)));
    }

    @Test
    public void returnedFtpFilesShouldHaveCorrectModifiedDateTimesAgainstThem() {

        List<FtpFile> files = sftpConnection.listFiles();

        assertThat(files.get(0).getLastModified().toString("dd/MM/yyyy HH:mm:ss"), is(equalTo("11/03/2014 08:07:45")));
        assertThat(files.get(1).getLastModified().toString("dd/MM/yyyy HH:mm:ss"), is(equalTo("12/03/2014 19:22:41")));
        assertThat(files.get(2).getLastModified().toString("dd/MM/yyyy HH:mm:ss"), is(equalTo("08/02/2014 17:09:24")));
    }

    @Test
    public void downloadMethodShouldCallChannelGetMethodWithFtpFileNameAndDirectory() throws SftpException {

        sftpConnection.download("path/to/file.txt", "some/directory");

        verify(mockChannel).get("path/to/file.txt", "some/directory");
    }

    @Test
    public void downloadMethodShouldThrowDownloadFailedExceptionWhenChannelThrowsSftpConnection() throws SftpException {

        expectedException.expect(FtpException.class);
        expectedException.expectMessage(is(equalTo("Unable to download file path/to/file.txt")));

        doThrow(new SftpException(999, "")).when(mockChannel).get("path/to/file.txt", "some/directory");

        sftpConnection.download("path/to/file.txt", "some/directory");
    }

    @Test
    public void uploadingShouldCallUnderlyingChannelToBeginUploadUsingCreatedFileInputStream() throws SftpException {
        
        sftpConnection.upload("local/file/to/upload.txt", "remote/directory");
        
        verify(mockChannel).put(mockFileInputStream, "remote/directory/upload.txt");
    }
    
    @Test
    public void ifConnectionIsUnableToFindOrOpenFileOnClientThenExceptionShouldBeCaughtAndRethrown() throws FileNotFoundException {

        expectedException.expect(FtpException.class);
        expectedException.expectMessage(is(equalTo("Could not find file: local/file/to/upload.txt")));
        
        when(mockFileStreamFactory.createInputStream("local/file/to/upload.txt")).thenThrow(new FileNotFoundException());
        
        sftpConnection.upload("local/file/to/upload.txt", "remote/directory");
    }
    
    @Test
    public void ifUploadFailsDuringPhysicalFileUploadTheSftpExceptionShouldBeCaughtAndRethrown() throws SftpException {
        
        expectedException.expect(FtpException.class);
        expectedException.expectMessage(is(equalTo("Upload failed to complete.")));
        
        doThrow(new SftpException(0, null)).when(mockChannel).put(mockFileInputStream, "remote/directory/upload.txt");
        
        sftpConnection.upload("local/file/to/upload.txt", "remote/directory");
    }
    
    @Test
    public void theInputStreamUsedToUploadFileShouldBeClosedAfterUploadHasFinished() throws IOException, SftpException {
        
        sftpConnection.upload("local/file/to/upload.txt", "remote/directory");
        
        InOrder inOrder = Mockito.inOrder(mockChannel, mockFileInputStream);
        
        inOrder.verify(mockChannel).put(mockFileInputStream, "remote/directory/upload.txt");
        inOrder.verify(mockFileInputStream).close();
    }
    
    @Test
    public void ifLocalFileInputStreamCannotBeClosedAfterUploadingThenTheExceptionShouldBeCaughtAndRethrown() throws IOException {
        
        expectedException.expect(FtpException.class);
        expectedException.expectMessage(is(equalTo("Upload may not have completed.")));
        
        doThrow(new IOException()).when(mockFileInputStream).close();
        
        sftpConnection.upload("local/file/to/upload.txt", "remote/directory");
    }
    
    @Test
    public void printingWorkingDirectoryShouldCallOnUnderlyingClientMethodToGetCurrentDirectory() throws SftpException {
        
        sftpConnection.printWorkingDirectory();
        
        verify(mockChannel).pwd();
    }
    
    @Test
    public void printingWorkingDirectoryShouldReturnExactlyWhatTheUnderlyingClientReturns() {
        
        assertThat(sftpConnection.printWorkingDirectory(), is(equalTo(DIRECTORY)));
    }
    
    @Test
    public void ifClientThrowsExceptionWhenTryingToGetWorkingDirectoryThenCatchExceptionAndRethrow() throws SftpException  {
        
        expectedException.expect(FtpException.class);
        expectedException.expectMessage(is(equalTo("Unable to print the working directory")));
        
        when(mockChannel.pwd()).thenThrow(new SftpException(0, ""));
        
        sftpConnection.printWorkingDirectory();
    }
    
    private Vector<LsEntry> createEntries() {

        Vector<LsEntry> vector = new Vector<LsEntry>();

        vector.add(createSingleEntry("File 1", 123l, 1394525265, true));
        vector.add(createSingleEntry("File 2", 456l, 1394652161, false));
        vector.add(createSingleEntry("File 3", 789l, 1391879364, true));

        return vector;
    }

    private LsEntry createSingleEntry(String fileName, long size, int mTime, boolean directory) {

        SftpATTRS attributes = mock(SftpATTRS.class);
        when(attributes.getSize()).thenReturn(size);
        when(attributes.getMTime()).thenReturn(mTime);

        LsEntry entry = mock(LsEntry.class);
        when(entry.getAttrs()).thenReturn(attributes);
        when(entry.getFilename()).thenReturn(fileName);
        when(entry.getAttrs().isDir()).thenReturn(directory);

        return entry;
    }

}
