package edu.utah.hci.bioinfo.SBApps;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Pattern;

public class Credentials {

	//fields
	private String division = null;
	private String url = null;
	private String token = null;
	private File cFile = null;
	private boolean ok = false;

	private static final Pattern PAT_EQUAL = Pattern.compile("\\s*=\\s*");
	private BufferedReader in = null;


	//constructors
	/**Both cFile and division maybe null.
	 * @throws IOException */
	public Credentials (File credFile, String division) throws Exception {
		cFile = credFile;
		this.division = division;
		if (this.division == null) this.division = "default";
		if (cFile == null || cFile.exists() == false) {
			//look in home
			cFile = new File(System.getProperty("user.home")+"/.sevenbridges/credentials");
			if (cFile.exists() == false) {
				printError ("Failed to find your credentials file in ~/.sevenbridges/credentials. See app menu.");
				return;
			}
		}
		parseCredentials();
	}

	private void parseCredentials() throws Exception {
		String line = null;
		String lookingFor = "["+ division+ "]";

		in = new BufferedReader (new FileReader (cFile));
		while ((line = in.readLine()) != null) {
			line = line.trim();
			if (line.contains(lookingFor)) {

				//endpoint
				line = in.readLine().trim();
				String[] fields = PAT_EQUAL.split(line);
				if (fields.length !=2) {
					printError("Failed to extract two fields from "+line);
					return;
				}
				if (fields[0].trim().equals("api_endpoint") == false) {
					printError("Failed to find the 'api_endpoint' key in "+line);
					return;
				}
				url = fields[1];

				//token
				line = in.readLine().trim();
				String[] fields2 = PAT_EQUAL.split(line);
				if (fields2.length !=2) {
					printError("Failed to extract two fields from "+line);
					return;
				}
				if (fields2[0].trim().equals("auth_token") == false) {
					printError("Failed to find the 'auth_token' key in "+line);
					return;
				}
				token = fields2[1];
				ok = true;
				return;
			}

		}
		if (url == null) {
			printError ("Failed to find a "+lookingFor+" line in your credentials file?");
			return;
		}
		in.close();
	}

	public void printError(String message) {
		Util.e("Problem parsing credentials from "+ cFile +"\nSee "
				+ "https://docs.sevenbridges.com/docs/store-credentials-to-access-seven-bridges-client-applications-and-libraries#unified-configuration-file "
				+ "and correct.\n");

		Util.e(message);
		ok = false;
		Util.closeNoError(in);
	}

	public String getDivision() {
		return division;
	}

	public String getUrl() {
		return url;
	}

	public String getToken() {
		return token;
	}

	public File getcFile() {
		return cFile;
	}

	public static Pattern getPatEqual() {
		return PAT_EQUAL;
	}

	public boolean isOk() {
		return ok;
	}



}
