package edu.utah.hci.bioinfo.SBApps;

import java.io.File;
import org.junit.Assert;
import org.junit.Test;

/**
 * To enable these end-to-end unit tests:

1) Create a Project in SB using the web console

2) Upload the "SBApps/TestingResources/TestProjectForSBApps" folder into this SB Project using the SB cmd line uploader
	See https://docs.sevenbridges.com/docs/upload-via-the-seven-bridges-uploader

	/Applications/BioApps/SevenBridges/sbg-uploader/bin/sbg-uploader.sh \
		--preserve-folders \
		--project hci-bioinformatics-shared-reso/testprojectforsbapps \
		~/Code/SBApps/TestingResources/TestProjectForSBApps

3) Use the SB web console to archive the TestProjectForSBApps/Alignments/bigFileToArchive.txt.gz file.

4) Set up the ~/.sevenbridges/credentials as described in https://tinyurl.com/sbcred wtih the [default] account pointed to the
	division that contains the TestProjectForSBApps.  Duplicate the [default] account info in ~/.sevenbridges/credentials and
	rename it [go-biden]

5) Create a test executable jar either via maven with no testing or by selecting the ProjectDownloader.jar in Eclipse and Export it as a runnable jar.

6) Modify the fields below to match these paths on your computer.

 * */
public class ProjectDownloaderTest {

	
    private String projectID = "hci-bioinformatics-shared-reso/testprojectforsbapps";
    private String alternativeAccount = "go-biden";
    private File testJar = new File ("/Users/u0028003/Code/SBApps/target/test.jar");
    private File ariaExecutable = new File ("/Users/u0028003/Code/SBApps/TestingResources/Aria2/aria2-1.35.0/bin/aria2c");
    private File tempDownloadDir = new File ("/Users/u0028003/Code/SBApps/TestingResources/DeleteMe");
    

    @Test
    public void testTestResources() {
    	Assert.assertTrue("Failed to find the test jar file '"+testJar +"', please create it before building the package.", ariaExecutable.exists());
    	Assert.assertTrue("Failed to find the aria executable, fix path in the ProjectDownloaderTest.java file "+ariaExecutable, ariaExecutable.exists());
    }

    @Test
    public void testListProjectsDefault() {
    	
       	String[] command = new String[] {
    			"java", "-jar", testJar.toString(), "-l"
       	};
       	String[] output = Util.executeViaProcessBuilder(command, true, "\n");
    	Assert.assertTrue(Util.stringArrayToString(output, " ").contains(projectID));
    }
    
    
    @Test
    public void testListProjectsAltAccount() {
    	
       	String[] command = new String[] {
    			"java", "-jar", testJar.toString(),
    			"-c", alternativeAccount
       	};
       	String[] output = Util.executeViaProcessBuilder(command, true, "");
    	Assert.assertTrue(Util.stringArrayToString(output, " ").contains(projectID));
    }
    
    @Test
    public void testListAllFiles() {
    	String[] command = new String[] {
    			"java", "-jar", testJar.toString(),
    			"-p", projectID
    	};
    	String[] output = Util.executeViaProcessBuilder(command, true, "");
    	
    	Assert.assertTrue(output[output.length-2].equals("157 #ToDownload	0 #Skipped"));
    }
    
    @Test
    public void testFilePathRegex() {
    	String[] command = new String[] {
    			"java", "-jar", testJar.toString(),
    			"-p", projectID,
    			"-r", ".+vcf.gz,.+txt.gz"
    	};
    	String[] output = Util.executeViaProcessBuilder(command, true, "");
    	
    	Assert.assertTrue(output[output.length-2].equals("2 #ToDownload	155 #Skipped"));
    }
    
    @Test
    public void testDownloadShouldFail() {
    	//this should fail internally since a url link cannot be made to the archived file.
    	Util.deleteDirectory(tempDownloadDir);
    	String[] command = new String[] {
    			"java", "-jar", testJar.toString(),
    			"-p", projectID,
    			"-r", ".+vcf.gz,.+txt.gz",
    			"-d", tempDownloadDir.toString()
    	};
    	String[] output = Util.executeViaProcessBuilder(command, true, "");
    	
    	Assert.assertTrue(output[output.length-2].startsWith("1	URL(s) could not be made"));
    	Util.deleteDirectory(tempDownloadDir);
    }
    
    @Test
    public void testDownload() {
    	//this should fail internally since a url link cannot be made to the archived file.
    	Util.deleteDirectory(tempDownloadDir);
    	String[] command = new String[] {
    			"java", "-jar", testJar.toString(),
    			"-p", projectID,
    			"-r", ".+vcf.gz,.+txt.gz",
    			"-d", tempDownloadDir.toString(), "-s",
    			"-e", ariaExecutable.toString()
    	};
    	Util.executeViaProcessBuilder(command, true, "");
    	File downloadedFile = new File (tempDownloadDir, projectID +"/TestProjectForSBApps/VariantCalls/Vcfs/mock.vcf.gz");
    	Assert.assertTrue(downloadedFile.exists());
    	Util.deleteDirectory(tempDownloadDir);
    }
  




}