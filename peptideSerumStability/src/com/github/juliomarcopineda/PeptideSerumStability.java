package com.github.juliomarcopineda;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.github.juliomarcopineda.peptide.Peptide;
import com.github.juliomarcopineda.peptide.PeptideType;

/**
 * PeptideSerumStability analyzes linear and cyclic peptides and determines if any fragments occur in a peptide serum stability study.
 * This program has two modes: interactive and input.
 * 
 * The input mode accepts a text file with a pre-defined format to build the following Peptide objects used for analysis. Then, this program will write
 * a CSV file of the suggested fragments. Here are the following arguments for the input mode: [input] [input file] [output file] [threshold]
 * 
 * The interactive mode lets the user input the peptide and mass spectrometry data manually. The user also has options to print out all the possible
 * fragments dependent on their size. The argument to start the interactive mode is: [interactive].
 * 
 * @author Julio Pineda
 *
 */
public class PeptideSerumStability {
	public static void main(String[] args) {
		if (args[0].toLowerCase()
			.equals("input")) {
			
			if (args.length != 4) {
				System.out.println("Please add the right number of arguments for the choice \"input\"");
				System.exit(1);
			}
			
			String inputFile = args[1];
			String outputFile = args[2];
			double threshold = Double.parseDouble(args[3]);
			
			List<Peptide> peptides = new InputParser(inputFile).parse()
				.getPeptides();
			
			writeOutputFile(peptides, outputFile, threshold);
		}
		else if (args[0].toLowerCase()
			.equals("interactive")) {
			
			printIntro();
			
			Peptide peptide = new Peptide();
			
			try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
				// Set the peptide sequence
				System.out.println("Please enter the peptide sequence. If peptide is not linear, do not include the linkers");
				System.out.print("Peptide sequence: ");
				String peptideSequence = br.readLine();
				System.out.println();
				
				peptide.setSequence(peptideSequence);
				
				// Set the peptide type
				System.out.println("Please enter the peptide type (linear, amide, DFBP, disulfide, custom)");
				System.out.print("Peptide Type: ");
				
				String typeString = br.readLine();
				
				// If user inputs a custom cyclization moeity
				double customWeight = 0;
				if (typeString.toUpperCase()
					.equals("CUSTOM")) {
					System.out.println("What is the molecular weight of the CUSTOM cyclization moiety?");
					System.out.print("Molecular Weight: ");
					
					customWeight = Double.parseDouble(br.readLine());
				}
				peptide.setCustomWeight(customWeight);
				
				System.out.println();
				try {
					PeptideType type = PeptideType.valueOf(typeString.toUpperCase());
					peptide.setType(type);
					
					// Set the conenctions of the peptide
					List<Integer> connections = new ArrayList<>();
					if (!type.equals(PeptideType.LINEAR)) { // only prompt for connections for cyclic peptides
						
						while (connections.isEmpty() || hasDuplicates(connections)) {
							connections.clear();
							promptForConnections(connections, br);
						}
						
						// Ensure that the connections are in increasing order
						Collections.sort(connections);
					}
					
					peptide.setConnections(connections);
					
					// Set the graph structure of the peptide
					Map<Integer, List<Integer>> graph = createGraphStructure(peptideSequence, connections, type);
					peptide.setGraph(graph);
					
				}
				catch (IllegalArgumentException e) {
					System.out.println("Please input a valid peptide type (linear, amide, DFBP, disulfide, custom)");
					System.exit(1);
				}
				
				// Create FragmentAnalyzer and then start interactive session
				FragmentAnalyzer analyzer = new FragmentAnalyzer(peptide);
				analyzer.findAllFragments()
					.measureAllFragmentWeights();
				interactiveSession(analyzer, br);
				
				System.out.println("Goodbye!");
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	private static boolean hasDuplicates(List<Integer> connections) {
		Set<Integer> setCheck = new HashSet<>(connections);
		
		if (setCheck.size() < connections.size()) {
			System.out.println("Please do not enter duplicate connections. Please try again.");
			
			return true;
		}
		else {
			return false;
		}
	}
	
	private static void promptForConnections(List<Integer> connections, BufferedReader br) {
		System.out.println("Please enter the indices where the cyclic connections are. Separate the indices with commas.");
		System.out.print("Connections: ");
		String connectionsString = null;
		try {
			connectionsString = br.readLine();
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println();
		
		String[] split = connectionsString.split(",");
		
		for (String indexString : split) {
			connections.add(Integer.parseInt(indexString));
		}
	}
	
	/**
	 * Given a list of peptides, an output file and a user-definied threshold, writes to a CSV file the suggested fragments with the following format:
	 * | Peptide | Mass Spec | Suggested Fragment | Calculated Weight |
	 * 
	 * @param peptides
	 * @param outputFile
	 * @param threshold
	 */
	private static void writeOutputFile(List<Peptide> peptides, String outputFile, double threshold) {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
			// Write header
			writer.write("Peptide,Mass Spec,Suggested Fragment,Calculated Weight\n");
			
			for (Peptide peptide : peptides) {
				String peptideSequence = peptide.getSequence();
				
				FragmentAnalyzer analyzer = new FragmentAnalyzer(peptide).findAllFragments()
					.measureAllFragmentWeights();
				
				List<Double> massSpecData = peptide.getMassSpecData();
				for (double data : massSpecData) {
					Map<String, Double> suggestedFragments = analyzer.suggestFragments(data, threshold);
					
					if (suggestedFragments != null && !suggestedFragments.isEmpty()) {
						for (Map.Entry<String, Double> entry : suggestedFragments.entrySet()) {
							String suggestedFragment = entry.getKey();
							double calculatedWeight = entry.getValue();
							
							writer.write(peptideSequence + "," + data + "," + suggestedFragment + "," + calculatedWeight + "\n");
						}
					}
				}
			}
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Given a fragment analyzer, runs the interactive session for the user in the command line.
	 * 
	 * @param analyzer
	 */
	private static void interactiveSession(FragmentAnalyzer analyzer, BufferedReader br) {
		try {
			String prompt = "in"; // Go in the while loop for the first time
			
			while (!prompt.substring(0, 1)
				.toUpperCase()
				.equals("Q")) {
				
				menuPrompt();
				prompt = br.readLine();
				System.out.println();
				
				if (prompt.substring(0, 1)
					.toUpperCase()
					.equals("C")) {
					
					String proceed = "Y";
					
					while (proceed.substring(0, 1)
						.toUpperCase()
						.equals("Y")) {
						
						System.out.print("Enter data from mass spectrometry: ");
						double data = Double.parseDouble(br.readLine());
						System.out.print("Enter threshold to compare theoretical molecular weights: ");
						double threshold = Double.parseDouble(br.readLine());
						System.out.println();
						
						Map<String, Double> suggestedFragments = analyzer.suggestFragments(data, threshold);
						suggestedFragments.entrySet()
							.stream()
							.forEach(entry -> {
								String fragment = entry.getKey();
								double weight = entry.getValue();
								
								System.out.println("Suggested fragment: " + fragment);
								System.out.println("Calculated weight: " + weight);
								System.out.println();
							});
						
						System.out.print("More data? (Y/N) ");
						proceed = br.readLine();
						System.out.println();
					}
				}
				
				else if (prompt.substring(0, 1)
					.toUpperCase()
					.equals("P")) {
					
					String proceed = "Y";
					while (proceed.substring(0, 1)
						.toUpperCase()
						.equals("Y")) {
						
						Map<String, Double> fragments = analyzer.getFragmentWeights();
						
						System.out.print("What fragment size to print? ");
						int size = Integer.parseInt(br.readLine());
						
						for (Map.Entry<String, Double> entry : fragments.entrySet()) {
							String fragment = entry.getKey();
							double weight = entry.getValue();
							
							if (!fragment.contains("#")) {
								int length = fragment.length();
								
								if (length == size) {
									System.out.println(fragment + "\t" + weight);
								}
							}
							else {
								int length = 0;
								String[] split = fragment.split("#");
								
								for (String fragmentPiece : split) {
									length += fragmentPiece.length();
								}
								
								if (length == size) {
									System.out.println(fragment + "\t" + weight);
								}
							}
						}
						System.out.println();
						System.out.print("Print more? (Y/N) ");
						proceed = br.readLine();
						System.out.println();
					}
				}
			}
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Creates the graph structure reperesntation of a peptide. This graph is represented as a map from the index and then its connections.
	 * 
	 * @param peptideSequence
	 * @param connections
	 * @param type
	 * @return
	 */
	public static Map<Integer, List<Integer>> createGraphStructure(String peptideSequence, List<Integer> connections, PeptideType type) {
		// Create the graph structure of the peptide base sequence
		Map<Integer, List<Integer>> graph = IntStream.range(0, peptideSequence.length() - 1)
			.mapToObj(source -> {
				int target = source + 1;
				
				List<Integer> targets = new ArrayList<>();
				targets.add(target);
				
				return new AbstractMap.SimpleEntry<Integer, List<Integer>>(source, targets);
			})
			.sorted(Map.Entry.comparingByKey())
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (collision1, collision2) -> collision1, LinkedHashMap::new));
		
		// Add any cyclic connections if connections is not empty
		if (!connections.isEmpty()) {
			switch (type) {
				case CUSTOM:
					// Set index of CUSTOM to the length of the peptide sequence
					int customIndex = peptideSequence.length();
					
					for (int connection : connections) {
						// Add connections from CUSTOM
						if (!graph.containsKey(customIndex)) {
							List<Integer> targets = new ArrayList<>();
							targets.add(connection);
							
							graph.put(customIndex, targets);
						}
						else {
							graph.get(customIndex)
								.add(connection);
						}
						
						// Add connections to CUSTOM
						if (!graph.containsKey(connection)) { // second connection is at the end of the graph
							List<Integer> targets = new ArrayList<>();
							targets.add(connection);
							
							graph.put(connection, targets);
						}
						else {
							graph.get(connection)
								.add(customIndex);
						}
					}
					
					break;
				
				case AMIDE:
					for (int i = 0; i < connections.size(); i++) {
						if (i % 2 == 0) {
							graph.get(connections.get(i))
								.add(connections.get(i + 1));
						}
						else {
							if (!graph.containsKey(connections.get(i))) { // connection is at the end of peptide
								List<Integer> target = new ArrayList<>();
								target.add(connections.get(i - 1));
								
								graph.put(connections.get(i), target);
							}
							else { // second connection is not at the end of peptide
								graph.get(connections.get(i))
									.add(connections.get(i - 1));
							}
						}
					}
					
					break;
				
				case DFBP:
					int dfbpIndex = peptideSequence.length();
					
					for (int connection : connections) {
						// Add connections from DFBP
						if (!graph.containsKey(dfbpIndex)) {
							List<Integer> targets = new ArrayList<>();
							targets.add(connection);
							
							graph.put(dfbpIndex, targets);
						}
						else {
							graph.get(dfbpIndex)
								.add(connection);
						}
						
						// Add connections to DFBP
						if (!graph.containsKey(connection)) { // second connection is at the end of graph
							List<Integer> targets = new ArrayList<>();
							targets.add(dfbpIndex);
							
							graph.put(connection, targets);
						}
						else {
							graph.get(connection)
								.add(dfbpIndex);
						}
						
					}
					
					break;
				case DISULFIDE:
					// Create disulfide bridge
					int s1Index = peptideSequence.length();
					int s2Index = s1Index + 1;
					
					List<Integer> s1ToS2 = new ArrayList<>();
					s1ToS2.add(s2Index);
					
					List<Integer> s2ToS1 = new ArrayList<>();
					s2ToS1.add(s1Index);
					
					graph.put(s1Index, s1ToS2);
					graph.put(s2Index, s2ToS1);
					
					// Add connections from peptide base
					graph.get(connections.get(0))
						.add(s1Index);
					
					if (!graph.containsKey(connections.get(1))) { // second connection is at end of peptide
						List<Integer> target = new ArrayList<>();
						target.add(s2Index);
						
						graph.put(connections.get(1), target);
					}
					else {
						graph.get(connections.get(1))
							.add(s2Index);
					}
					
					// Add connections from disulfide bridge
					graph.get(s1Index)
						.add(connections.get(0));
					graph.get(s2Index)
						.add(connections.get(1));
					
					break;
				case LINEAR:
					break;
			}
		}
		
		return graph;
	}
	
	/**
	 * Menu prompt for interactive mode of the program.
	 */
	private static void menuPrompt() {
		System.out.println("What do you want to do?");
		System.out.println("| (C)ompare | (P)rint | (Q)uit |");
		System.out.print("Enter: ");
	}
	
	/**
	 * Intro message when using the interactive mode of the program.
	 * 
	 * @param peptide
	 */
	private static void printIntro() {
		System.out.println("-------------------------");
		System.out.println("Peptide Serum Stability");
		System.out.println("-------------------------");
		System.out.println();
		System.out.println("This program will generate all possible fragments of the given input peptide sequence");
		System.out.println("and will calculate their corresponding theoretical molecular weights.");
		System.out.println();
	}
}
