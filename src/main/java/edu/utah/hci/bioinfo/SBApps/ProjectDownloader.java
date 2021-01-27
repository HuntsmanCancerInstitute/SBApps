package edu.utah.hci.bioinfo.SBApps;

import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.exceptions.UnirestException;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

public class ProjectDownloader {

	//user defined fields
	private Credentials credentials = null;
	private String projectId = null;
	private Pattern[] toKeep = null;
	private boolean skipNoUrlFiles = false;
	private boolean justPrintFilePaths = false;
	private File aria2 = null;
	private File downloadDirectory = null;

	//internal fields
	private API api = null;
	private HashMap<String, SBFile> idFileMap = new HashMap<String, SBFile>();
	private TreeMap<String, SBFile> pathFileMap = new TreeMap<String, SBFile>();
	private ArrayList<SBFile> filesToDownload = new ArrayList<SBFile>();

	public ProjectDownloader (String[] args) {
		try {
			long startTime = System.currentTimeMillis();

			processArgs(args);

			//create an API helper
			api = new API (credentials);
			
			//pull projects?
			if (projectId == null) {
				Util.p("No project ID provided, loading visable projects...");
				fetchProjects();
			}

			//fetch files and folders for the project
			Util.p("Loading files from "+projectId+"...");
			JsonNode fj = api.query("files?project="+projectId, true);
			if (fj == null) System.exit(1);
			loadSBFiles(fj);

			//build the map
			Util.p("\nBuilding file paths...");
			buildFilePaths();
			
			//pull bulk details for archived files?  doesn't have it!
			/*
			ArrayList<String> al = new ArrayList<String>();
			al.add("6006fa6ee4b0bc6ab1b0309c");
			Util.p(api.bulkFileDetailQuery(al).getObject().toString(5));
			System.exit(0);
			*/

			//get file urls, some of these calls will fail if the file is archived or being archived/ unarchived
			Util.p("\nRequesting URLs...");
			requestUrls();

			//make aria2 download file
			Util.p("\nWriting aria2 bulk download file...");
			downloadFiles();

			//finish and calc run time
			double diffTime = ((double)(System.currentTimeMillis() -startTime))/60000;
			System.out.println("\nDone! "+Math.round(diffTime)+" Min\n");

		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}

	}



	private void fetchProjects() throws UnirestException {
		JsonNode pj = api.query("projects", true);
		if (pj == null) System.exit(1);
		Util.p("\tProjectID\tCreatedBy\tCreatedOn");
		JSONArray proj = pj.getObject().getJSONArray("items");
		Iterator<Object> it = proj.iterator();
		while (it.hasNext() ) {
			JSONObject jo = (JSONObject) it.next();
			StringBuilder sb = new StringBuilder();
			sb.append("\t");
			sb.append(jo.get("id")); sb.append("\t");
			sb.append(jo.get("created_by")); sb.append("\t");
			sb.append(jo.get("created_on")); 
			Util.p(sb);
		}
		System.exit(0);
	}



	private void downloadFiles() throws IOException {
		///Users/u0028003/BioApps/Aria2/aria2-1.35.0/bin/aria2c --show-console-readout=false --max-connection-per-server=10 --min-split-size=1M -c -i test.txt --dir /Users/u0028003/Downloads/Delme

		//write out file list for aria2
		File downloadSet = new File (downloadDirectory, "toDownloadAria2.txt");
		PrintWriter out = new PrintWriter (new FileWriter (downloadSet));

		for (SBFile f: filesToDownload) {
			out.println(f.getUrl());
			out.println("\tout="+f.getPath());
		}
		out.close();

		//build cmd
		String aria2String = "path/to/your/aria2";
		if (aria2 != null ) aria2String = aria2.getCanonicalPath();
		String[] cmd = new String[] {
				aria2String, "--show-console-readout=false", "-c",
				"--max-connection-per-server=10", "--min-split-size=1M", 
				"--dir", downloadDirectory.getCanonicalPath(),
				"-i", downloadSet.getCanonicalPath()
		};

		//no aria2, just exit
		if (aria2 == null) {
			Util.p("\tExecute -> "+Util.stringArrayToString(cmd, " "));
			System.exit(0);
		}

		Util.p("\tExecuting -> "+Util.stringArrayToString(cmd, " "));
		String[] output = Util.executeViaProcessBuilder(cmd, true, "\t");

		//OK?
		if (output[output.length-1].contains("(OK)") == false) {
			Util.printErrorExit("\t\nDownload Error from Aria2!  It may be an AWS issue.  Try executing the cmd line above in a few hours.  Downloads will pick up where they left off.");
		}
	}



	private void requestUrls() throws UnirestException {
		Util.p("\tMade?\tFilePath\tURL");
		ArrayList<SBFile> fail = new ArrayList<SBFile>();
		for (SBFile f: pathFileMap.values()) {
			if (f.isMakeUrl()) {
				JsonNode pj = api.query("files/"+f.getId()+"/download_info", false);
				if (pj == null) {
					fail.add(f);
					Util.p("\tfalse\t"+f.getPath());
				}
				else {
					f.setUrl( (String)pj.getObject().get("url"));
					filesToDownload.add(f);
					Util.p("\ttrue\t"+f.getPath()+"\t"+f.getUrl());
				}

			}
		}
		if (fail.size() !=0 && skipNoUrlFiles == false) Util.printErrorExit("\n\t"+fail.size()+"\tURL(s) could not be made, check the files above in the SB web console (archived?) or set the -s flag to skip them.");

		if (filesToDownload.size() == 0) Util.printErrorExit("\n\tNo files to download?\n");
	}



	private void buildFilePaths() {
		//for each file walk it's parents
		for (SBFile f: idFileMap.values()) {
			if (f.isFolder() == false) {
				ArrayList<String> paths = new ArrayList<String>();
				paths.add(f.getName());
				addParentNames(paths, f);
				paths.add(projectId);
				f.setPath(paths);
				if (toKeep == null) f.setMakeUrl(true);
				else {
					String p = f.getPath();
					f.setMakeUrl(false);
					for (Pattern pat: toKeep) {
						if (pat.matcher(p).matches()) {
							f.setMakeUrl(true);
							break;
						}
					}
				}
				pathFileMap.put(f.getPath(), f);
			}
		}

		Util.p("\tDownload?\tFilePath");
		boolean urlsToFetch = false;
		for (String p: pathFileMap.keySet()) {
			boolean makeUrl = pathFileMap.get(p).isMakeUrl();
			if (makeUrl) urlsToFetch = true;
			Util.p("\t"+makeUrl+"\t"+p);
		}
		//anything to do?
		if (urlsToFetch == false) Util.printErrorExit("\nNo passing files to download? Check regular expressions.");
		//exit?
		if (justPrintFilePaths) System.exit(0);
	}

	private void addParentNames(ArrayList<String> paths, SBFile f) {
		SBFile parent = idFileMap.get(f.getParentId());
		if (parent != null) {
			paths.add(parent.getName());
			addParentNames(paths, parent);
		}
	}

	private void loadSBFiles(JsonNode fj) throws IOException, UnirestException {
		JSONArray items = (JSONArray) fj.getObject().get("items");
		Iterator<Object> it = items.iterator();
		while (it.hasNext()) {
			JSONObject ff = (JSONObject) it.next();
			SBFile sbf = new SBFile (ff);
			if (idFileMap.containsKey(sbf.getId())) Util.printErrorExit("Already saved "+sbf.getId());
			else {
				idFileMap.put(sbf.getId(), sbf);
				//is it a folder?
				if (sbf.isFolder()) {
					JsonNode folderQuery = api.query("files?parent="+sbf.getId(), true);
					if (folderQuery == null) throw new IOException ("Failed to load folder "+"files?parent="+sbf.getId());
					loadSBFiles(folderQuery);
				}
			}
		}
	}



	public static void main(String[] args) {
		new ProjectDownloader(args);
	}		

	/**This method will process each argument and assign new varibles
	 * @throws IOException */
	public void processArgs(String[] args) throws IOException{
		Util.p("\nArgs: SBApps/ProjectDownloader: "+ Util.stringArrayToString(args, " ")+"\n");
		Pattern pat = Pattern.compile("-[a-z]");
		String regExToKeep = null;
		File credentialsFile = null;
		String account = null;
		for (int i = 0; i<args.length; i++){
			String lcArg = args[i].toLowerCase();
			if (lcArg.startsWith("-h") || lcArg.startsWith("--h")) printDocs();
			Matcher mat = pat.matcher(lcArg);
			if (mat.matches()){
				char test = args[i].charAt(1);
				try{
					switch (test){
					case 'c': credentialsFile = new File(args[++i]).getCanonicalFile(); break;
					case 'a': account = args[++i]; break;
					case 'p': projectId = args[++i]; break;
					case 'r': regExToKeep = args[++i]; break;
					case 's': skipNoUrlFiles = true; break;
					case 'l': justPrintFilePaths = true; break;
					case 'd': downloadDirectory = new File(args[++i]).getCanonicalFile(); break;
					case 'e': aria2 = new File(args[++i]).getCanonicalFile(); break;
					default: Util.printErrorExit("\nProblem, unknown option! " + mat.group()); 
					}
				}
				catch (Exception e){
					Util.printErrorExit("\nSorry, something doesn't look right with this parameter: -"+test+"\n");
				}
			}
		}

		//find credentials file and parse
		credentials = new Credentials(credentialsFile, account);

		//process regexes
		if (regExToKeep != null) {
			Util.p("Regular expressions for selecting particular file paths:" );
			Pattern comma = Pattern.compile(",");
			String[] rx = comma.split(regExToKeep);
			toKeep = new Pattern[rx.length];
			for (int i=0; i< toKeep.length; i++) {
				toKeep[i] = Pattern.compile(rx[i]);
				Util.p("\t"+toKeep[i]);
			}
			Util.p("");
		}

		//make download dir
		if (downloadDirectory == null) downloadDirectory = new File( System.getProperty("user.dir") ).getCanonicalFile();
		if (downloadDirectory.exists() == false) downloadDirectory.mkdirs();
		if (downloadDirectory.isDirectory() == false || downloadDirectory.exists() == false) Util.printErrorExit("Failed to find or make your download directory -> "+downloadDirectory);

	}	



	public static void printDocs(){
		Util.p(
				"**************************************************************************************\n" +
				"**                             Project Downloader: Jan 2021                         **\n" +
				"**************************************************************************************\n" +
				"This tool downloads one or more files from Seven Bridges Projects while maintaining\n"+
				"their folder structure via the fast, multi-threaded, aria2 download utility.\n"+

				"\nOptions:\n"+
				"-p Project ID (division/projectName) to download.\n"+
				"      Skip this option to list the visible Projects.\n"+
				"-d Path to a directory for downloading the Project.\n"+
				"      Defaults to the current working directory.\n"+
				"-l List the Project file paths and exit.\n"+
				"      Recommended, then use -r to select files to download.\n"+
				"-r Regular expressions to select particular Project file paths to download.\n"+
				"      Comma delimited, no spaces, enclose in 'xxx', defaults to all.\n"+
				"-c Path to your SB unified credentials file.\n"+
				"      Defaults to ~/.sevenbridges/credentials\n"+
				"      If it does not exist, fetch an authentication token https://tinyurl.com/sbtoken\n"+
				"      Then use a text editor to create the ~/.sevenbridges/credentials file following\n"+
				"      the format in https://tinyurl.com/sbcred, chmod 600 the file, and keep it safe.\n"+
				"-a Account credentials to load, defaults to [default].\n"+
				"-s Skip files that failed URL creation instead of exiting. These are typically \n"+
				"     archived in Glacier. Use the SB web console to unarchive them, then rerun.\n"+
				"-e Path to the aria2 executable, see https://aria2.github.io to download and install.\n"+
				"     Skip this option to set up a mock aria2 download.\n"+

				"\nExamples assuming ~/.sevenbridges/credentials exists: \n\n"+
				"List visible Projects:\n"+
				"     java -Xmx1G -jar pathTo/ProjectDownloader_xxx.jar\n"+
				"List files in a Project:\n"+
				"     java -Xmx1G -jar pathTo/ProjectDownloader_xxx.jar -p alana-welm/pdx -l\n"+
				"Test Project file path regexes:\n"+
				"     java -Xmx1G -jar pathTo/ProjectDownloader_xxx.jar -p alana-welm/pdx -l \n"+
				"     -r '.+bam,.+cram,.+bai,.+crai'\n"+
				"Download available alignment files:\n"+
				"     java -Xmx1G -jar pathTo/ProjectDownloader_xxx.jar -p alana-welm/pdx -d ~/PdxProj\n"+
				"     -r '.+bam,.+cram,.+bai,.+crai' -e ~/Aria2/1.35/bin/aria2c\n"+
				"Download all Project files skipping archived objects:\n"+
				"     java -Xmx1G -jar pathTo/ProjectDownloader_xxx.jar -p alana-welm/pdx -d ~/PdxProj\n"+
				"     -s -e ~/Aria2/1.35/bin/aria2c\n"+

				"**************************************************************************************\n");

		System.exit(0);
	}

}