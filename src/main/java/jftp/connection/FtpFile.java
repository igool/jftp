package jftp.connection;

import org.joda.time.DateTime;

public class FtpFile {

	private String name;
	private long size;
	private String fullPath;
	private DateTime lastModified;
	private boolean directory;
	
	public FtpFile(String name, long size, String fullPath, long mTime, boolean isDirectory) {

		this.name = name;
		this.size = size;
		this.fullPath = fullPath;
		this.lastModified = new DateTime(mTime);
		this.directory = isDirectory;
	}

	public String getName() {
		return name;
	}

	public long getSize() {
		return size;
	}

	public String getFullPath() {
		return fullPath;
	}

	public DateTime getLastModified() {
		return lastModified;
	}

	public boolean isDirectory() {
		return directory;
	}
}
