package edu.utah.hci.bioinfo.SBApps;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.mashape.unirest.http.JsonNode;

public class Util {

	/**Returns a String separated by commas for each bin.*/
	public static String stringArrayToString(String[] s, String separator){
		if (s==null) return "";
		int len = s.length;
		if (len==1) return s[0];
		if (len==0) return "";
		StringBuffer sb = new StringBuffer(s[0]);
		for (int i=1; i<len; i++){
			sb.append(separator);
			sb.append(s[i]);
		}
		return sb.toString();
	}
	
	public static void p(Object o) {
		System.out.println(o.toString());
	}
	public static void e(Object o) {
		System.err.println(o.toString());
	}
	public static void printErrorExit(Object o) {
		System.err.println(o.toString());
		System.exit(1);
	}

	public static void printTabbedJson(JsonNode json) {
		Util.p(json.getObject().toString(5));
		
	}
	
	/**Uses ProcessBuilder to execute a cmd, combines standard error and standard out into one and returns their output.*/
	public static String[] executeViaProcessBuilder(String[] command, boolean printToStandardOut, String outputLinePrepender){
		ArrayList<String> al = new ArrayList<String>();
		try {
			ProcessBuilder pb = new ProcessBuilder(command);
			pb.redirectErrorStream(true);
			Process proc = pb.start();

			BufferedReader data = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			String line;
			while ((line = data.readLine()) != null){
				line = line.trim();
				if (line.length() !=0) {
					al.add(line);
					if (printToStandardOut) System.out.println(outputLinePrepender+line);
				}
			}
			data.close();
		} catch (Exception e) {
			System.err.println("Problem executing -> "+Util.stringArrayToString(command," "));
			e.printStackTrace();
			return null;
		}
		String[] res = new String[al.size()];
		al.toArray(res);
		return res;
	}
	
	/**Returns a gz zip or straight file reader on the file based on it's extension.
	 * @author davidnix*/
	public static BufferedReader fetchBufferedReader( File txtFile) throws IOException{
		BufferedReader in;
		String name = txtFile.getName().toLowerCase();
		if (name.endsWith(".zip")) {
			ZipFile zf = new ZipFile(txtFile);
			ZipEntry ze = (ZipEntry) zf.entries().nextElement();
			in = new BufferedReader(new InputStreamReader(zf.getInputStream(ze)));
		}
		else if (name.endsWith(".gz")) {
			in = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(txtFile))));
		}
		else in = new BufferedReader (new FileReader (txtFile));
		return in;
	}
	
	public static void closeNoError(BufferedReader br) {
		if (br != null) {
			try {
				br.close();
			} catch (IOException e) {}
		}
	}
	
}
