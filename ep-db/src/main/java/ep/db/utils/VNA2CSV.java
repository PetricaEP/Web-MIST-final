//package ep.db.utils;
//
//import java.io.BufferedReader;
//import java.io.BufferedWriter;
//import java.io.File;
//import java.io.IOException;
//import java.io.StringReader;
//import java.nio.file.Files;
//import java.nio.file.StandardOpenOption;
//import java.util.HashMap;
//import java.util.Map;
//
//import org.jbibtex.BibTeXDatabase;
//import org.jbibtex.BibTeXEntry;
//import org.jbibtex.BibTeXParser;
//import org.jbibtex.CharacterFilterReader;
//import org.jbibtex.Key;
//import org.jbibtex.ParseException;
//import org.jbibtex.TokenMgrException;
//import org.jbibtex.Value;
//import org.jblas.FloatMatrix;
//
//import cern.colt.matrix.tfloat.FloatMatrix2D;
//
//public class VNA2CSV {
//	
//	private static final Key KEY_ABSTRACT = new Key("abstract");
//	private static final Key KEY_KEYWORDS = new Key("keywords");
//	private static final String CSV_FIELD_SEPARATOR = ",";
//	
//
//	public static void main(String[] args) {
//		
//		if ( args.length != 2){
//			System.err.println("Wrong number of parameters. Expected: 2 Got :" + args.length);
//			printHelp();
//			return;
//		}
//		
//		
//		File docsFile = new File(args[0]);
//		File xyFile = new File(args[1]);
//		
//		Map<String, Integer> docIdMap = null; 
//		try {
//			docIdMap = processDocumentsFile(docsFile);
//		} catch (TokenMgrException | ParseException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		
//		readXY(xyFile, docIdMap);
//	}
//
//	private static void readXY(File xyFile, Map<String, Integer> docIdMap) {
//		try ( BufferedReader br = Files.newBufferedReader(xyFile.toPath());
//				BufferedWriter bwXY = Files.newBufferedWriter(new File(xyFile + "_xy.csv").toPath(), 
//						StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
//						StandardOpenOption.WRITE);
//				BufferedWriter bwGraph = Files.newBufferedWriter(new File(xyFile + "_citations.csv").toPath(), 
//						StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
//						StandardOpenOption.WRITE)){
//			
//			String line = null;
//			//Pula cabeçalho
//			br.readLine();
//			br.readLine();
//			line = br.readLine();
//			
//			FloatMatrix data = new FloatMatrix(docIdMap.size(), 3);
//			
//			while ( line != null ){
//				
//				if ( line.startsWith("*tie data")){
//					break;
//				}
//				
//				String[] f = line.split("\\s+");
//				if ( docIdMap.containsKey(f[0]))					
//					data.putRow(docIdMap.get(f[0]) - 1, new float[]{
//						Float.parseFloat(f[1]),
//						Float.parseFloat(f[2]),
//						Float.parseFloat(f[3])
//					});
//				
//				
//				line = br.readLine();
//			}
//			
//			//Normaliza X,Y antes de gravar no arquivo CSV
//			normalizeProjections(data);
//			
//			// Grava arquivo CSV
//			for(int i = 0; i < data.rows(); i++){
//				// Id, X, Y, Relevance
//				bwXY.write(String.format("%d,%f,%f,%f", i+1, data.get(i, 0), data.get(i, 1), data.get(i, 2) ));
//				bwXY.newLine();
//			}
//			
//			// Processa seção de citações
//			br.readLine(); // Pula cabeçalho
//			line = br.readLine();
//			Map<String, Boolean> map = new HashMap<>(); 
//			while ( line != null ){
//				String f[] = line.split("\\s+");
//				Integer docId = docIdMap.get(f[0]),
//						refId = docIdMap.get(f[1]);
//				
//				if ( docId != null && refId != null && docId != refId && !map.containsKey(""+docId+refId)){
//					bwGraph.write(String.format("%d,%d", docId, refId));
//					bwGraph.newLine();
//					map.put(""+docId+refId, true);
//				}
//				
//				line = br.readLine();
//			}
//
//		}catch (IOException e) {
//			e.printStackTrace();
//		}
//		
//	}
//
//	private static Map<String, Integer> processDocumentsFile(File docsFile) throws TokenMgrException, ParseException {
//		Map<String, Integer> docIdMap = new HashMap<>();
//		
//		BibTeXParser parser = new BibTeXParser();
//		
//		try ( BufferedReader br = Files.newBufferedReader(docsFile.toPath());
//				BufferedWriter bw = Files.newBufferedWriter(new File(docsFile + ".csv").toPath(), 
//						StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
//						StandardOpenOption.WRITE)){
//			String line = null;
//			line = br.readLine();
//			
//			String docId, bibRef;
//			int id = 1;
//			while ( line != null ){
//				docId = line.trim();
//				line = br.readLine();
//				bibRef = line;
//				try {
//				BibTeXDatabase bib = parser.parseFully(new CharacterFilterReader(new StringReader(bibRef.trim())));
//				if ( bib != null ){
//					bw.write(String.format("%d,", id));
//					BibTeXEntry entry = bib.resolveEntry(new Key(docId));
//					Value value = entry.getField(BibTeXEntry.KEY_TITLE);
//					bw.write(quote(value.toUserString()));
//					bw.write(CSV_FIELD_SEPARATOR);
////					value = entry.getField(BibTeXEntry.KEY_AUTHOR);
////					bw.write(quote(value.toUserString()));
////					bw.write(CSV_FIELD_SEPARATOR);
//					value = entry.getField(BibTeXEntry.KEY_YEAR);
//					bw.write(value.toUserString());
//					bw.write(CSV_FIELD_SEPARATOR);
//					value = entry.getField(BibTeXEntry.KEY_JOURNAL);
//					bw.write(quote(
//							value.toUserString().substring(0, Math.min(value.toUserString().length(), 255))));
//					bw.write(CSV_FIELD_SEPARATOR);
//					value = entry.getField(KEY_ABSTRACT);
//					bw.write(quote(value.toUserString()));
//					bw.write(CSV_FIELD_SEPARATOR);
//					value = entry.getField(KEY_KEYWORDS);
//					bw.write(quote(value.toUserString()));
//					bw.newLine();
//					
//					docIdMap.put(docId, id);
//					++id;
//				}
//				}catch (Exception e){
//					System.out.println("Skiped: " + bibRef);
//				}
//				
//				// Pula 7 linhas (?)
//				br.readLine();br.readLine();br.readLine();br.readLine();
//				br.readLine();br.readLine();br.readLine();
//				line = br.readLine();
//			}
//		}catch (Exception e) {
//			e.printStackTrace();
//		}
//		
//		return docIdMap;
//	}
//	
//	private static String quote(String string) {
//		return String.format("\"%s\"", string.trim().replaceAll("\"", ""));
//	}
//
//	private static void normalizeProjections(FloatMatrix2D y) {
//		final float maxX = y.viewColumn(0).getMaxLocation()[0], 
//				maxY = y.viewColumn(1).getMaxLocation()[0];
//		final float minX = y.viewColumn(0).getMinLocation()[0],
//				minY = y.viewColumn(1).getMinLocation()[0];
//		
//		y.viewColumn(0).assign( (v) -> 2 * (v - minX)/(maxX - minX) - 1 );
//		y.viewColumn(1).assign( (v) -> 2 * (v - minY)/(maxY - minY) - 1 );
//	}
//
//	private static void printHelp() {
//		System.out.println("Usage: VNA2CSV <documents_data_file> <xy_and_grap_file>");
//	}
//
//}
