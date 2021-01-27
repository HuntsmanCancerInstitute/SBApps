package edu.utah.hci.bioinfo.SBApps;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.regex.Pattern;

public class Credentials {

	//fields
	private String division = null;
	private String url = null;
	private String token = null;
	private File cFile = null;

	private static final Pattern PAT_EQUAL = Pattern.compile("\s*=\s*");


	//constructors
	/**Both cFile and division maybe null.*/
	public Credentials (File credFile, String division) {
		cFile = credFile;
		this.division = division;
		if (this.division == null) this.division = "default";
		if (cFile == null || cFile.exists() == false) {
			//look in home
			cFile = new File(System.getProperty("user.home")+"/.sevenbridges/credentials");
			if (cFile.exists() == false) Util.printErrorExit("\nFailed to find your credentials file in ~/.sevenbridges/credentials. See app menu.");
		}
		parseCredentials();
	}

	private void parseCredentials() {
		String line = null;
		BufferedReader in = null;
		String lookingFor = "["+ division+ "]";

		try {
			in = new BufferedReader (new FileReader (cFile));
			while ((line = in.readLine()) != null) {
				line = line.trim();
				if (line.contains(lookingFor)) {

					//endpoint
					line = in.readLine().trim();
					String[] fields = PAT_EQUAL.split(line);
					if (fields.length !=2) throw new Exception("Failed to extract two fields from "+line);
					if (fields[0].trim().equals("api_endpoint") == false) throw new Exception("Failed to find the 'api_endpoint' key in "+line);
					url = fields[1];
					
					//token
					line = in.readLine().trim();
					String[] fields2 = PAT_EQUAL.split(line);
					if (fields2.length !=2) throw new Exception("Failed to extract two fields from "+line);
					if (fields2[0].trim().equals("auth_token") == false) throw new Exception("Failed to find the 'auth_token' key in "+line);
					token = fields2[1];
					return;
				}

			}
			if (url == null) throw new Exception ("Failed to find a [xxx] line in your credentials file?");
		}
		catch (Exception e) {
			Util.e("Problem parsing credentials from "+ cFile +"\nSee "
					+ "https://docs.sevenbridges.com/docs/store-credentials-to-access-seven-bridges-client-applications-and-libraries#unified-configuration-file "
					+ "and correct.");
			e.printStackTrace();
			System.exit(1);
		}
		finally {
			Util.closeNoError(in);
		}
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



}
