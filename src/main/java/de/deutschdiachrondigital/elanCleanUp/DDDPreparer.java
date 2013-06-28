package de.deutschdiachrondigital.elanCleanUp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mpi.eudico.client.annotator.commands.SaveSelectionAsEafCA;
import mpi.eudico.client.annotator.commands.SaveSelectionAsEafCommand;
import mpi.eudico.server.corpora.clom.Annotation;
import mpi.eudico.server.corpora.clom.AnnotationCore;
import mpi.eudico.server.corpora.clom.TimeSlot;
import mpi.eudico.server.corpora.clom.Transcription;
import mpi.eudico.server.corpora.clomimpl.abstr.AbstractAnnotation;
import mpi.eudico.server.corpora.clomimpl.abstr.AlignableAnnotation;
import mpi.eudico.server.corpora.clomimpl.abstr.RefAnnotation;
import mpi.eudico.server.corpora.clomimpl.abstr.TierImpl;
import mpi.eudico.server.corpora.clomimpl.abstr.TimeSlotImpl;
import mpi.eudico.server.corpora.clomimpl.abstr.TranscriptionImpl;
import mpi.eudico.server.corpora.clomimpl.type.LinguisticType;

import org.apache.commons.io.FileUtils;

public class DDDPreparer {

	private static TranscriptionImpl eaf;
	private static String log = "";
	
	/**
	 * @param args[0] properties file elan2bearbeitung
	 * args[1] main directory of corpus
	 */
	public static void main(String[] args) throws Exception {
		//get properties file
		FileInputStream in = new FileInputStream(args[0]);
		Properties prop = new Properties();
		prop.load(new InputStreamReader(in, "UTF-8"));
		String inDir = args[1];
		Collection<String> fnames = getFileNamesInDirectory(inDir + "/4_fertig/");
		for (String fname : fnames){
			prepare(inDir, fname, prop);
		}
	}
		
	public static void prepare(String inDir, String fname, Properties prop){
		
		// correct the time slots
		correctTimeSlots(inDir + "/4_fertig/" + fname);
			
		// parse the Elan file
		eaf = new TranscriptionImpl(inDir + "/4_fertig/" + fname);
		System.out.println("working on " + fname);
		
		// extract meta data and write to file
		extractMetaData(fname, prop.getProperty("metadataloc"), inDir + "/meta/");
		
		// go through the tiers and report issues (e.g. timeslots, weird symbols)
		reportIssues(fname);
		
		// search and replace
		searchAndReplaces(fetchArrayFromPropFile("searchAndReplace", prop));
		
		// create the reference tier "norm" on the basis of a given tier
		TierImpl tierTok = (TierImpl) eaf.getTierWithId(prop.getProperty("ling"));
		tierTok.setName("ling");
		
		// create the txt tier which holds the actual words
		createTxtTier(prop.getProperty("edition"), "edition");
		
		// rename the remaining tiers
		String[][] translation = fetchArrayFromPropFile("rename", prop);
		renameTiers(translation);
		
		// get rid of the tiers that are not necessary
		String tiersToBeRemoved[] = prop.getProperty("remove").split(",");
		removeTiers(tiersToBeRemoved);
		
		// fix a markup issue
		fixMarkup();
		
		// save the new file
		AbstractAnnotation lastToken = (AbstractAnnotation) tierTok.getAnnotations().lastElement();
		long endtime = lastToken.getEndTimeBoundary();
		String corpusname = "DDD-" + inDir.split("/")[inDir.split("/").length-1];
		String foutName = inDir + corpusname + "/" + fname;
		File file = new File(foutName);
		file.getParentFile().mkdirs();
		SaveEAF e = new SaveEAF(eaf, (long) 0, (long) endtime, foutName);
		Vector<TierImpl> tiers = eaf.getTiers();
		for (TierImpl tier : tiers){
			System.out.println(tier.getName() + ": " + tier.getNumberOfAnnotations());
		}
		
		// write a logfile
		try {
			FileUtils.writeStringToFile(new File( inDir + "log.txt"), log);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}
	
	@SuppressWarnings("unchecked")
	private static void fixMarkup() {
		 // if a markup anno is not followed by another markup anno, and the next character is a space, extend the markup anno
		TierImpl m = (TierImpl) eaf.getTierWithId("markup");
		TierImpl chartier = (TierImpl) eaf.getTierWithId("character");
		for (Annotation anno: (Vector<Annotation>) m.getAnnotations()){
			long startTimeOfNextAnno = anno.getEndTimeBoundary();
			Annotation markupanno = m.getAnnotationAtTime(startTimeOfNextAnno);
			Annotation charanno = chartier.getAnnotationAtTime(startTimeOfNextAnno);
			while (charanno != null && charanno.getValue().trim().isEmpty() && markupanno == null){
				startTimeOfNextAnno = charanno.getEndTimeBoundary();
				markupanno = m.getAnnotationAtTime(charanno.getEndTimeBoundary());
				charanno = chartier.getAnnotationAtTime(charanno.getEndTimeBoundary());
			}
			long start = anno.getBeginTimeBoundary();
			long stop = startTimeOfNextAnno;
			String val = anno.getValue();
			m.removeAnnotation(anno);
			AlignableAnnotation aa = (AlignableAnnotation) m.createAnnotation(start, stop);
			aa.setValue(val);
		 }
	}

	private static void extractMetaData(String fname, String metadatalocation, String metaoutloc) {
		String id = fname.replaceAll(".eaf", "").split("_")[0];
		Map<String, Map> metadata = csv2map(metadatalocation);
		String textbereich = "Textbereich=" + metadata.get(id).get("Textbereich");
		String textname = "Text=" + metadata.get(id).get("Text: Name (weitere Namen)");
		String textart = "Textart=" + metadata.get(id).get("Textart");
		String sprache = "Sprache=" +metadata.get(id).get("Sprache 1") + ", " + metadata.get(id).get("Sprache 2");
		String sprachgebiet = "Sprachgebiet=" + metadata.get(id).get("sprachlicher Großraum 1") + ", " + metadata.get(id).get("sprachlicher Großraum 2");
		String sprachlandschaft = "Sprachlandschaft=" + metadata.get(id).get("Sprachlandschaft 1") + ", " + metadata.get(id).get("Sprachlandschaft 2");
		String lokalisierung = "Lokalisierung=" + metadata.get(id).get("Lokalisierung");
		String entstehungszeit = "Entstehungszeit=" + metadata.get(id).get("Entstehungszeit");
		String datierung = "Datierung=" + metadata.get(id).get("genauere Datierung");
		String referenz = "Referenz=" + metadata.get(id).get("Marburger/Paderborner Repertorium (andere Referenzen)");
		try{
			File file = new File(metaoutloc + id +".txt");
			file.getParentFile().mkdirs();
			FileWriter fstream = new FileWriter(metaoutloc + id +".txt");
			BufferedWriter out = new BufferedWriter(fstream);
			out.write(textbereich  + "\n" + 
					textart + "\n" + 
					textname + "\n" + 
					sprache.substring(0, sprache.lastIndexOf(", ")) + "\n" + 
					sprachgebiet.substring(0, sprachgebiet.lastIndexOf(", ")) + "\n" + 
					sprachlandschaft.substring(0, sprachlandschaft.lastIndexOf(", ")) + "\n" + 
					lokalisierung + "\n" + 
					entstehungszeit + "\n" + 
					datierung + "\n" + 
					referenz);
			//Close the output stream
			out.close();
		} catch (Exception e){//Catch exception if any
			System.err.println("Error: " + e.getMessage());
		}
		// ad the document name as a span
		// for kleinere denkmaeler
		//makeDocumentSpan((String) metadata.get(id).get("Text: Name (weitere Namen)"));
		//for the rest
		makeDocumentSpan(fname.replaceAll(".eaf", ""));
	}
	
	private static long getEarliestTime() {
		long earliestTime = 999999999;
		
		Enumeration elmts = eaf.getTimeOrder().elements();
		while (elmts.hasMoreElements()) {
			long t = ((TimeSlot) elmts.nextElement()).getTime();
			if (t < earliestTime) {
				earliestTime = t;
			}
		}		
		return earliestTime;
	}
	
	private static void makeDocumentSpan(String textname) {
		LinguisticType type = new LinguisticType("main-tier");
		TierImpl t = new TierImpl("document", null, (Transcription) eaf, type);
		eaf.addTier(t);
		long beginTime = getEarliestTime();
		long latestTime = eaf.getLatestTime();
		AlignableAnnotation aa = (AlignableAnnotation) t.createAnnotation(beginTime, latestTime);
		aa.setValue(textname);
	}

	private static Map<String, Map> csv2map(String string) {
		Map<String, Map> out = null;
		out = new HashMap<String, Map>();
		try {
			FileReader fr = new FileReader( string);
			BufferedReader br = new BufferedReader(fr);
			String line = null;
			String k = null;
			String v = "NA";
			String firstline = br.readLine(); 
			while ( (line = br.readLine()) != null )
			{
				line = line.replaceAll("\t", " \t");
				Map<String, String> mp=null;
				mp=new HashMap<String, String>();
				StringTokenizer header = new StringTokenizer(firstline, "\t");
				StringTokenizer st = new StringTokenizer(line,"\t");
				while (st.hasMoreTokens()) {
					k=header.nextToken();
					v=st.nextToken().trim();
					mp.put(k,v);
				}
				out.put(mp.get("Textkürzel"), mp);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return out;
	}

	private static void correctTimeSlots(String fnameIn) {
		try{
			FileInputStream fstream = new FileInputStream(fnameIn);
			// Get the object of DataInputStream
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String strLine;
			StringBuffer newElan = new StringBuffer();
			//Read File Line By Line
			while ((strLine = br.readLine()) != null)   {
				String regex = new String("TIME_VALUE=");
				if (strLine.contains(regex) == true){
					Pattern MY_PATTERN = Pattern.compile("TIME_VALUE=\"([0123456789]+?)\"");
					Matcher m = MY_PATTERN.matcher(strLine);
					while (m.find()) {
						int timevalue = Integer.parseInt( m.group(1) );
						int newtimevalue = -1;
						int dif = timevalue % 200;
						String replace = null;
						if (dif > 100){
							newtimevalue = timevalue + (200 - dif);
						    replace = ("TIME_VALUE=\"" + newtimevalue + "\"");
						}
						if (dif <= 100){
						   	newtimevalue = timevalue - dif;
						  	replace = ("TIME_VALUE=\"" + newtimevalue + "\"");
						}
						if (replace != null){
							String find = new String("TIME_VALUE=\"" + timevalue + "\"");
							newElan.append( strLine.replace(find, replace) + "\n" );
						}
					}
				}
				if (!strLine.contains(regex)){
					newElan.append( strLine + "\n" );
				}
			}
			//Close the input stream	
			in.close();
			
			FileUtils.writeStringToFile(new File(fnameIn), newElan.toString());
			
			
		}catch (Exception e){//Catch exception if any
			System.err.println("Error: " + e.getMessage());
		}
	}
		
	private static void reportIssues(String fin){
		Vector<TierImpl> tiers = eaf.getTiers();
		for (TierImpl tier : tiers){
			String tierName = tier.getName();
			Vector<AbstractAnnotation> annos = tier.getAnnotations();
			for (AbstractAnnotation anno : annos){
				String annoValue = anno.getValue().trim();
				int beginTime = (int) anno.getBeginTimeBoundary();
				int endTime = (int) anno.getEndTimeBoundary();
				
				// check consistency of annotation boundaries for main-tiers
				int beginDiff = beginTime%200;
				int endDiff = endTime%200;
				if (tier.getLinguisticType().getLinguisticTypeName().equals("main-tier")){
					if (beginDiff > 0 | endDiff > 0){
						log = log + ("WARNING: " + fin + ":" + 
								tierName + ":" + 
								Milliseconds2HumanReadable(beginTime) +
								Milliseconds2HumanReadable(endTime) + ":" +
								annoValue.trim() + 
								" | wrong begin or end time, please correct in original file\n");
					}
				}
				
				// notify of - at the beginning or ending of annotations in referenztext w
				if (tierName.equals("Referenztext W")){
					if ( annoValue.trim().length() > 1 & (annoValue.startsWith("-") | annoValue.endsWith("-"))){
						String newValue = annoValue.replaceAll("\\b-", "").replaceAll("-\\b", "");
						newValue = newValue.replaceAll("_$", "_");
						anno.setValue(newValue);
					}
				}

				// make sure the characters are a single character, or a space
				if (tierName.equals("Referenztext B")){
					String newValue = annoValue.trim();
					if (newValue.isEmpty() | newValue == null){
						newValue = " ";
					}
					anno.setValue(newValue);
				}
				
				// notify of [] at the beginning or ending of annotations
				if (annoValue.contains("[") | annoValue.contains("]")){
					if (annoValue.length() > 1){
					  String newValue = annoValue.replace("[", "").replace("]", "");
					  anno.setValue(newValue);
					}
				}
				
				// remove annos with identical start and end time
				if (anno.getBeginTimeBoundary() == anno.getEndTimeBoundary()){
					tier.removeAnnotation(anno);
				}
				
				 if (anno.getValue().contains("\n")){
					 anno.setValue(anno.getValue().replaceAll("\n", " "));
				 }
				 
				 if (anno.getValue().contains("\r")){
					 anno.setValue(anno.getValue().replaceAll("\r", " "));
				 }
				 
				 if (anno.getValue().contains("\t")){
					 anno.setValue(anno.getValue().replaceAll("\t", " "));
				 }
				 
				 if (anno.getValue().contains("\t")){
					 anno.setValue(anno.getValue().replaceAll(" ", ""));
				 }
				 
				 // remove annos with end time < start time
				 if (anno.getEndTimeBoundary() < anno.getBeginTimeBoundary()){
					 tier.removeAnnotation(anno);
				 }				 
				 
				 if (fin.contains("Hel_Fitte")){
					Map<String, String> out =  new HashMap<String, String>();
					try {
						FileReader fr = new FileReader("/media/sf_shared_folder/DDDcorpora/Heliand/mapping.txt");
						BufferedReader br = new BufferedReader(fr);
						String line = null;
						String k = null;
						String v = "NA"; 
						while ( (line = br.readLine()) != null )
						{
							String[] st = line.split("\t");
							out.put(st[1],st[0]);
						}
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
					if ( out.keySet().contains(anno.getValue().trim()) & tierName.equals("Lemma") ){
						String newValue = out.get(anno.getValue());
						anno.setValue(newValue);
					}
				 }
				
				// remove annos with empty value from specific tiers
				Collection<String> reltiers = new ArrayList<String>();
				reltiers.add("M1a DDDTS Lemma");
				reltiers.add("M1b DDDTS Beleg");
				reltiers.add("M2a Flexion Lemma");
				reltiers.add("M2b Flexion Beleg 1");
				reltiers.add("M2c Flexion Beleg 2");
				if (reltiers.contains(tierName) & (annoValue.isEmpty() | annoValue.equals(" ") ) ){
					tier.removeAnnotation(anno);
				}
				
				// uppercase annos with smallcase value from specific tiers
				Collection<String> reltiers2 = new ArrayList<String>();
				reltiers2.add("M1a DDDTS Lemma");
				reltiers2.add("M1b DDDTS Beleg");
				reltiers2.add("M2a Flexion Lemma");
				reltiers2.add("M2b Flexion Beleg 1");
				reltiers2.add("M2c Flexion Beleg 2");
				if (reltiers2.contains(tierName) ){
					anno.setValue( annoValue.toUpperCase() );
				}
			}
		}
	}
	
	private static Collection<String> getFileNamesInDirectory(String path){
		String files;
		Collection<String> out = new Vector();	
		System.out.println("working on path:" + path);
		File folder = new File(path);
		File[] listOfFiles = folder.listFiles(); 
		for (int i = 0; i < listOfFiles.length; i++){ 
			if (listOfFiles[i].isFile()){
				files = listOfFiles[i].getName();
				if (files.endsWith(".eaf") || files.endsWith(".EAF")){
					out.add(files);
				}
		    }
	    }
		return out;
	}
	
	private static String Milliseconds2HumanReadable(int millis){
		return String.format("%d min, %d sec", 
			    TimeUnit.MILLISECONDS.toMinutes(millis),
			    TimeUnit.MILLISECONDS.toSeconds(millis) - 
			    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))
			);
	}
	
	private static void searchAndReplaces(String[][] m){
		for (int i = 0; i < m.length; i++) {
	        String targetTier = m[i][0].trim();
	        String annoValue = m[i][1].trim();
	        String findValue = m[i][2].trim();
	        String replaceValue = m[i][3].trim();
	        String[] conditions = Arrays.copyOfRange(m[i], 4, m[i].length);
	        searchAndReplace(targetTier, annoValue, findValue, replaceValue, conditions);
		}
	}
	
	private static String[][] fetchArrayFromPropFile(String propertyName, Properties propFile) {
		String[] a = propFile.getProperty(propertyName).split(";");
		String[][] array = new String[a.length][a.length];
		for(int i = 0;i < a.length;i++) {
			a[i] = a[i].replace("\\,", "COMMA");
			array[i] = a[i].split(",");
			for (int j = 0; j < array[i].length; j++){
				array[i][j] = array[i][j].replaceAll("COMMA", ",");
			}
		}
		return array;
	}
	
	private static void searchAndReplace(String targetTier, String annoValue, String findValue, String replaceValue, String[] conditions) {
		TierImpl ctier = (TierImpl) eaf.getTierWithId(targetTier);
		String fin = eaf.getFullPath().substring(eaf.getFullPath().lastIndexOf("/")+1);
		Vector<AbstractAnnotation> annos = ctier.getAnnotations();
				
		for (int i = 0; i < annos.size(); i++){
			AbstractAnnotation targetAnno = annos.get(i);
			AbstractAnnotation compareAnno = null;
			boolean test = true;
			String condition = "";

			for (int c = 0; c < conditions.length-1; c=c+3){
				if (test){
					String direction = conditions[c].trim();
					String condTier = conditions[c+1].trim();
					String condValue = conditions[c+2].trim();
					String actualCondition = new String("on tier " + condTier + ", position " + direction + ", has the value " + condValue);
					test = false;
	
					TierImpl tierWithCondition = null;
					Vector<AbstractAnnotation> condAnnos = null;
					if ( condTier.length() > 0) {
						tierWithCondition = (TierImpl) eaf.getTierWithId(condTier);
						condAnnos = tierWithCondition.getAnnotations();
					}
				
					// check direction
					if (direction.equals("left") & i > 0){
						compareAnno = (AbstractAnnotation) tierWithCondition.getAnnotationBefore(targetAnno.getBeginTimeBoundary());
					}	
					if (direction.equals("right") & i < annos.size()){
						compareAnno = (AbstractAnnotation) tierWithCondition.getAnnotationAfter(targetAnno.getEndTimeBoundary());
					}
					if (direction.equals("align")){
						compareAnno = (AbstractAnnotation) tierWithCondition.getAnnotationAtTime(targetAnno.getBeginTimeBoundary());
					}
				
					// check the comparison
					if (compareAnno != null){
						if (compareAnno.getValue().matches("\\b(" + condValue + ")\\b")){
							test = true;
							condition = condition + actualCondition + "; ";
						}
					}
				}
			}
			if (test){
				if (targetAnno.getValue().matches("\\b(" + annoValue + ")\\b")){
					String targetValue = targetAnno.getValue();
					String newTargetValue = targetValue.replaceFirst(findValue, replaceValue);
					if (!targetValue.equals(newTargetValue)){
						targetAnno.setValue(newTargetValue);
						log = log + ("CHANGE: " + fin + ":" + 
							targetTier + ":" + 
							Milliseconds2HumanReadable((int) targetAnno.getBeginTimeBoundary()) + ":" + 
							Milliseconds2HumanReadable((int) targetAnno.getEndTimeBoundary()) + ":" + 
							targetValue.trim() + 
							" | changed this value to " + newTargetValue + " because I found " + findValue + " with the condition " + condition + "\n");
					}
				}
			}
		}
	}
	
	public static void createTxtTier(String levelname, String tiername){
		LinguisticType type = new LinguisticType("main-tier");
		TierImpl t = new TierImpl(tiername, null, (Transcription) eaf, type);
		eaf.addTier(t);
		TierImpl source = (TierImpl) eaf.getTierWithId(levelname);
		Vector<AbstractAnnotation> sourceAnnos = source.getAnnotations();
		long beginTime = -1;
		long endTime = -1;
		String value = "";
		for (AbstractAnnotation sourceAnno : sourceAnnos){
			if (beginTime < 0){
				beginTime = sourceAnno.getBeginTimeBoundary();
			}
			boolean test = isPunctuation(sourceAnno.getValue().trim());
			if (!test){
				if (isPunctuation(value) & !value.isEmpty()){
					AlignableAnnotation aa = (AlignableAnnotation) t.createAnnotation(beginTime, sourceAnno.getBeginTimeBoundary());
					aa.setValue(value);
					value = "";
					beginTime = sourceAnno.getBeginTimeBoundary();
				}
				value = value + sourceAnno.getValue();
				endTime = sourceAnno.getEndTimeBoundary();
			}
			// if it is punctuation, and it is not a whitespace, create anno at previous endtime
			if (test & sourceAnno.getValue().trim().length() > 0){
				if (!value.trim().isEmpty()){
					AlignableAnnotation aa = (AlignableAnnotation) t.createAnnotation(beginTime, endTime);
					aa.setValue(value);
				}
				beginTime = sourceAnno.getBeginTimeBoundary();
				endTime = sourceAnno.getEndTimeBoundary();
				value = sourceAnno.getValue();
			}

			// if it is punctuation, but it is actually whitespace, check if the following is also whitespace, and create anno with the endtime of the last whitespace
			if (test & sourceAnno.getValue().trim().isEmpty()){
				if (!value.trim().isEmpty()){
					endTime = sourceAnno.getEndTimeBoundary();
					Annotation nextAnno = source.getAnnotationAtTime(endTime);
					try{
						while ( nextAnno.getValue().trim().isEmpty() ){
							endTime = nextAnno.getEndTimeBoundary();
							nextAnno = source.getAnnotationAtTime(endTime);
						}
					} catch (NullPointerException ne){
						continue;
					}
					
					AlignableAnnotation aa = (AlignableAnnotation) t.createAnnotation(beginTime, endTime);
					aa.setValue(value);
				}
				beginTime = -1;
				value = "";
			}
			
			// if it is whitespace, then do not make a token
			if (test & sourceAnno.getValue().trim().isEmpty()){
				
			}
		}
	}
	
	public static boolean isPunctuation(String s){
		boolean out = false;
		String punct = "*+#'!()[]?.:,;\"";
		if (s.isEmpty()){
			out = true;
		}
		if (!s.isEmpty()){
			if (punct.contains(s)){
				out = true;
			}
		}
		return out;
	}
	
	public static void renameTiers(String[][] m){
		for (int i = 0; i < m.length; i++) {
	        String origName = m[i][0].trim();
	        String newName = m[i][1].trim();
	        TierImpl tier = (TierImpl) eaf.getTierWithId(origName);
	        if (tier != null){
	    		LinguisticType type = new LinguisticType("main-tier");
	    		TierImpl t = new TierImpl(newName, null, (Transcription) eaf, type);
	    		eaf.addTier(t);
	        	Vector<Object> annoobjects = tier.getAnnotations();
	        	for (Object annoobject : annoobjects){
	        		Annotation anno = (Annotation) annoobject;
	        		String value = anno.getValue();
	        		long beginTime = anno.getBeginTimeBoundary();
	        		long endTime = anno.getEndTimeBoundary();
	        		AlignableAnnotation aa = (AlignableAnnotation) t.createAnnotation(beginTime, endTime);
	        		if (aa != null){
	        			aa.setValue(value);
	        		}
	        		else {
	        			System.out.println("Error: " + newName + ", " + value + ", " + beginTime + ", " + endTime);
	        		}
	        	}
	        }
	    }
		for (int i = 0; i < m.length; i++){
			String origName = m[i][0].trim();
			TierImpl tier = (TierImpl) eaf.getTierWithId(origName);
			if (tier != null){
				eaf.removeTier(tier);
			}
		}
	}
	
	public static void removeTiers(String[] toBeRemoved){
		for (String remove : toBeRemoved){
			remove = remove.trim();
			try{
				TierImpl tier = (TierImpl) eaf.getTierWithId(remove);
				boolean removeok = true;
				for (TierImpl subtier : (Collection<TierImpl>) tier.getChildTiers()){
					for (String check : toBeRemoved){
						if (subtier.equals(check)){
							removeok = false;
							break;
						}
					}
				}
				if (removeok){
					eaf.removeTier(tier);
				}
			} catch (java.lang.NullPointerException nothing){
				continue;
			}
		}
	}
}