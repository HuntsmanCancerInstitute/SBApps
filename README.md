# SBApps
Tools for working with the Seven Bridges bioinformatics platform.

## ProjectDownloader
<pre>
 u0028003$ java -jar ~/Code/SBApps/target/ProjectDownloader_0.2.jar -h

Args: SBApps/ProjectDownloader: -h

**************************************************************************************
**                             Project Downloader: Feb 2021                         **
**************************************************************************************
This tool downloads files from Seven Bridges Projects while maintaining their folder
structure via the fast, multi-threaded, aria2 download utility.

Options:
-p Project ID (division/projectName) to download.
      Skip this option to just list the visible Projects.
-r Regular expressions to select particular Project file paths to download.
      Comma delimited, no spaces, enclose in 'xxx', defaults to all.
-d Download Project files into this directory.
      Skip this option to just list the files.
-c Path to your SB unified credentials file.
      Defaults to ~/.sevenbridges/credentials
      If it does not exist, fetch an authentication token https://tinyurl.com/sbtoken
      Then use a text editor to create the ~/.sevenbridges/credentials file following
      the format in https://tinyurl.com/sbcred, chmod 600 the file, and keep it safe.
-a Account credentials to load, defaults to 'default'.
-s Skip files that failed URL creation instead of exiting. These are typically 
     archived in Glacier. Use the SB web console to unarchive them, then rerun.
-e Path to the aria2 executable, see https://aria2.github.io to download and install.
     Skip this option to set up a mock aria2 download.

Examples assuming ~/.sevenbridges/credentials exists: 

List visible Projects:
     java -Xmx1G -jar pathTo/ProjectDownloader_xxx.jar
List files in a Project:
     java -Xmx1G -jar pathTo/ProjectDownloader_xxx.jar -p alana-welm/pdx
Test Project file path regexes:
     java -Xmx1G -jar pathTo/ProjectDownloader_xxx.jar -p alana-welm/pdx
     -r '.+bam,.+cram,.+bai,.+crai'
Download available alignment files:
     java -Xmx1G -jar pathTo/ProjectDownloader_xxx.jar -p alana-welm/pdx -d ~/PdxProj
     -r '.+bam,.+cram,.+bai,.+crai' -e ~/Aria2/1.35/bin/aria2c
Download all Project files skipping archived objects:
     java -Xmx1G -jar pathTo/ProjectDownloader_xxx.jar -p alana-welm/pdx -d ~/PdxProj
     -s -e ~/Aria2/1.35/bin/aria2c
**************************************************************************************
</pre>
