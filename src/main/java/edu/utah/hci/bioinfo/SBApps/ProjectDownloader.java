package edu.utah.hci.bioinfo.SBApps;

import com.mashape.unirest.http.JsonNode;
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
	private boolean skipDownloadedFiles = true;
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
			
			int exitStatus = doWork();

			//finish and calc run time
			double diffTime = ((double)(System.currentTimeMillis() -startTime))/60000;
			
			if (exitStatus == 0) System.out.println("Done! "+Math.round(diffTime)+" Min\n");
			else {
				System.err.println("\nERROR! "+Math.round(diffTime)+" Min\n");
				System.exit(1);
			}

		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}

	}



	private int doWork() throws Exception {

		//create an API helper
		api = new API (credentials);
		
		//pull projects?
		if (projectId == null) {
			Util.p("Listing visable Projects...");
			if (fetchProjects()) return 0;
			return 1;
		}

		//fetch files and folders for the project, max return per query is 100 items, unfortunately we have to fetch all of the files in a project, no option to fetch just a subset, some projects have 40K+ files!
		Util.p("Loading files from "+projectId+"...");
		JsonNode fj = api.query("files?offset=0&limit=100&project="+projectId, true, true);
		if (fj == null) return 1;
		loadSBFiles(fj);
		
		//build the map
		Util.p("\nBuilding file paths, comparing to what already exists...");
		buildFilePaths();
		if (downloadDirectory == null) return 0;

		//get file urls, some of these calls will fail if the file is archived or being archived/ unarchived or has hit some odd issue with no error! 
		Util.p("\nRequesting download URLs...");
		if (requestUrls() == false) return 1;

		//make aria2 download file
		Util.p("\nWriting aria2 bulk download file...");
		if (downloadFiles() == false) return 1;

		return 0; //ok
		
	}



	private boolean fetchProjects() throws Exception {
		//max num is 100

		ArrayList<JSONArray> projAL = new ArrayList<JSONArray>();
		int offset=0;
		while (true) {
			JsonNode pj = api.query("projects?offset="+offset+"&limit=100", true, true);
			if (pj == null) return false;
			JSONArray proj = pj.getObject().getJSONArray("items");
			projAL.add(proj);

			//check links
			JSONArray links = pj.getObject().getJSONArray("links");
			//Util.p("LINKS:\n"+ links.toString(5));
			if (links.length() == 0) break;
			JSONObject linksDetails = (JSONObject) links.get(0);
			String rel = linksDetails.getString("rel");
			if (rel == null) {
				Util.e("Failed to find the rel key in "+links.toString(5));
				return false;
			}
			
			//is there more?
			if (rel.equals("next") == false) break;
			offset+=100;
		}

		//print all projects
		Util.p("\tProjectID\tProjectName\tCreatedBy\tCreatedOn");
		int numProj = 0;
		for (JSONArray proj: projAL) {
			Iterator<Object> it = proj.iterator();
			while (it.hasNext() ) {
				numProj++;
				JSONObject jo = (JSONObject) it.next();
				StringBuilder sb = new StringBuilder();
				sb.append("\t");
				sb.append(jo.get("id")); sb.append("\t");
				sb.append(jo.get("name")); sb.append("\t");
				sb.append(jo.get("created_by")); sb.append("\t");
				sb.append(jo.get("created_on")); 
				Util.p(sb);
			}
			Util.p("\t\t"+numProj+"\tProjects");
		}
		return true;
	}



	private boolean downloadFiles() throws IOException {
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

		//no aria2, just print cmd they should execute.
		if (aria2 == null) {
			Util.p("\tExecute -> "+Util.stringArrayToString(cmd, " "));
			return true;
		}

		else {
			Util.p("\tExecuting -> "+Util.stringArrayToString(cmd, " "));
			String[] output = Util.executeViaProcessBuilder(cmd, true, "\t");
			//OK?
			if (output[output.length-1].contains("(OK)") == false) {
				Util.e("\t\nDownload Error from Aria2!  It may be an AWS issue.  Try executing the cmd line above in a few hours.  Downloads will pick up where they left off.");
				return false;
			}
			return true;
		}
	}



	private boolean requestUrls() throws Exception {
		Util.p("\tMade?\tFilePath\tURL");
		ArrayList<SBFile> fail = new ArrayList<SBFile>();
		for (SBFile f: pathFileMap.values()) {
			if (f.isMakeUrl()) {
				JsonNode pj = api.query("files/"+f.getId()+"/download_info", true, true);
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
		if (fail.size() !=0 && skipNoUrlFiles == false) {
			Util.e("\n\t"+fail.size()+"\tURL(s) could not be made, check the files above in the SB web console (archived?) or set the -s flag to skip them.");
			return false;
		}

		if (filesToDownload.size() == 0) {
			Util.e("\n\tNo files to download?\n");
			return false;
		}
		return true;
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
							//check if it exists?
							if (skipDownloadedFiles) {
								File test = new File (downloadDirectory, p);
								if (test.exists()) f.setMakeUrl(false);
								else f.setMakeUrl(true);
							}
							else f.setMakeUrl(true);
							break;
						}
					}
				}
				pathFileMap.put(f.getPath(), f);
			}
		}

		Util.p("\tDownload?\tFilePath");
		boolean urlsToFetch = false;
		int numToDownload = 0;
		int numToSkip = 0;
		for (String p: pathFileMap.keySet()) {
			boolean makeUrl = pathFileMap.get(p).isMakeUrl();
			if (makeUrl) {
				urlsToFetch = true;
				numToDownload++;
			}
			else numToSkip++;
			Util.p("\t"+makeUrl+"\t"+p);
		}
		Util.p("\t"+numToDownload+" #ToDownload\t"+numToSkip+" #Skipped\n");
		//anything to do?
		if (urlsToFetch == false) Util.printErrorExit("\nNo passing files to download? Check regular expressions!");
	}

	private void addParentNames(ArrayList<String> paths, SBFile f) {
		SBFile parent = idFileMap.get(f.getParentId());
		if (parent != null) {
			paths.add(parent.getName());
			addParentNames(paths, parent);
		}
	}

	
	private void loadSBFiles(JsonNode fj) throws Exception {
		ArrayList<SBFile> items = fetchFileItems(fj);
		for (SBFile sbf: items) {
			if (idFileMap.containsKey(sbf.getId())) Util.printErrorExit("Already saved "+sbf.getId());
			else {
				idFileMap.put(sbf.getId(), sbf);
				//is it a folder?
				if (sbf.isFolder()) {
					JsonNode folderQuery = api.query("files?offset=0&limit=100&parent="+sbf.getId(), true, true);
					if (folderQuery == null) throw new IOException ("Failed to load folder "+"files?parent="+sbf.getId());
					loadSBFiles(folderQuery);
				}
			}
		}
	}



	private ArrayList<SBFile> fetchFileItems(JsonNode fj) throws Exception {
		//first request, might contain a link to pull more
		ArrayList<SBFile> allItems = new ArrayList<SBFile>();
		JSONObject response = (JSONObject) fj.getObject();

		while (true) {
			//add the items
			JSONArray items = (JSONArray) response.get("items");
			Iterator<Object> it = items.iterator();
			while (it.hasNext()) allItems.add(new SBFile((JSONObject)it.next()));
			
			//anything left?
			JSONArray links = (JSONArray) response.get("links");
			
			if (links.length() == 0) return allItems;
			JSONObject l = links.getJSONObject(0);
			String rel = l.getString("rel");
			if (rel.equals("next")) {
				String hrefToCall = l.getString("href");
				JsonNode newNode = api.query(hrefToCall, false, true);
				response = (JSONObject) newNode.getObject();
			}
			else return allItems;
		}
	}



	public static void main(String[] args) {
		if (args.length == 0) printDocs();
		else new ProjectDownloader(args);
	}		

	/**This method will process each argument and assign new varibles
	 * @throws IOException */
	public void processArgs(String[] args) throws Exception{
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
					case 'l': break;
					case 'r': regExToKeep = args[++i]; break;
					case 's': skipNoUrlFiles = true; break;
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
		if (credentials.isOk() == false) System.exit(1);

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
		if (downloadDirectory != null) {
			downloadDirectory.mkdirs();
			if (downloadDirectory.isDirectory() == false || downloadDirectory.exists() == false) Util.printErrorExit("Failed to find or make your download directory -> "+downloadDirectory);
		}
		

	}	



	public static void printDocs(){
		Util.p(
				"**************************************************************************************\n" +
				"**                            Project Downloader: April 2021                        **\n" +
				"**************************************************************************************\n" +
				"This tool downloads files from Seven Bridges Projects while maintaining their folder\n"+
				"structure via the fast, multi-threaded, aria2 download utility. Files already\n"+
				"downloaded are skipped so run iteratively as new files become available.\n"+

				"\nOptions:\n"+
				"-l List visible Projects.\n"+
				"-p Project ID (division/projectName) to download.\n"+
				"      Skip this option to just list the visible Projects.\n"+
				"-r Regular expressions to select particular Project file paths to download.\n"+
				"      Comma delimited, no spaces, enclose in 'xxx', defaults to all.\n"+
				"-d Download Project files into this directory.\n"+
				"      Skip this option to just list the files.\n"+
				"-c Path to your SB unified credentials file.\n"+
				"      Defaults to ~/.sevenbridges/credentials\n"+
				"      If it does not exist, fetch an authentication token https://tinyurl.com/sbtoken\n"+
				"      Then use a text editor to create the ~/.sevenbridges/credentials file following\n"+
				"      the format in https://tinyurl.com/sbcred, chmod 600 the file, and keep it safe.\n"+
				"-a Account credentials to load, defaults to 'default'.\n"+
				"-s Skip files that failed URL creation instead of exiting. These are typically \n"+
				"     archived in Glacier. Use the SB web console to unarchive them, then rerun.\n"+
				"-e Path to the aria2 executable, see https://aria2.github.io to download and install.\n"+
				"     Skip this option to set up a mock aria2 download.\n"+

				"\nExamples assuming ~/.sevenbridges/credentials exists: \n\n"+
				"List visible Projects:\n"+
				"     java -Xmx1G -jar pathTo/ProjectDownloader_xxx.jar -l\n"+
				"List files in a particular Project:\n"+
				"     java -Xmx1G -jar pathTo/ProjectDownloader_xxx.jar -p alana-welm/pdx\n"+
				"Test Project file path regexes:\n"+
				"     java -Xmx1G -jar pathTo/ProjectDownloader_xxx.jar -p alana-welm/pdx\n"+
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
