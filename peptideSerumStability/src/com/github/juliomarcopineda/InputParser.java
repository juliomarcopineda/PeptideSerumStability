package com.github.juliomarcopineda;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.github.juliomarcopineda.peptide.Peptide;
import com.github.juliomarcopineda.peptide.PeptideType;

/**
 * This class parses through the designated input text format for the peptide serum stability analysis. The general format as follows:
 * 
 * Line 0: [peptide sequence] [peptide type] [optional: first connection index] [optional: second connection index] ... (more indices if desired).
 * Line 1: Mass spectrometry data delimited with white space.
 * 
 * More lines can be added if more sequences want to be analyzed.
 * 
 * The peptide type can have the following valid options: linear, disulfide, dfbp and amide.
 * If the peptide type is not linear, the indices afterwards must be even in number. The index is assumed to be zero-index.
 * 
 * @author Julio Pineda
 *
 */
public class InputParser {
	private String inputFile;
	
	private List<Peptide> peptides;
	
	public InputParser(String inputFile) {
		this.inputFile = inputFile;
	}
	
	public InputParser() {
		
	}
	
	public List<Peptide> getPeptides() {
		return peptides;
	}
	
	public void setPeptides(List<Peptide> peptides) {
		this.peptides = peptides;
	}
	
	/**
	 * Begins the process of parsing through the input text file.
	 * 
	 * Throws an IllegalArgumentException if the first line for each peptide contains an odd number of arguments. This suggests that the number of connection
	 * indices entered was incorrect. The number of indices must be even to have a valid input.
	 */
	public InputParser parse() {
		// Initialize array list of peptides
		List<Peptide> peptides = new ArrayList<>();
		
		try (BufferedReader reader = new BufferedReader(new FileReader(inputFile))) {
			
			String line;
			int lineNumber = 0;
			while ((line = reader.readLine()) != null) {
				if (lineNumber % 2 == 0) {
					String[] split = line.split("\\s+");
					
					// Extract input arguments from text file
					String peptideSequence = split[0];
					PeptideType type = PeptideType.valueOf(split[1].toUpperCase());
					List<Integer> connections = new ArrayList<>();
					
					if (!type.equals(PeptideType.LINEAR)) {
						
						for (int i = 2; i < split.length; i++) {
							connections.add(Integer.parseInt(split[i]));
						}
					}
					
					// Build graph structure from sequence and index connections
					Map<Integer, List<Integer>> graph = PeptideSerumStability.createGraphStructure(peptideSequence, connections, type);
					
					// Create Peptide object from data above
					Peptide peptide = new Peptide();
					peptide.setSequence(peptideSequence);
					peptide.setType(type);
					peptide.setConnections(connections);
					peptide.setGraph(graph);
					
					// Add to list of peptides
					peptides.add(peptide);
				}
				else {
					String[] split = line.split("\\s+");
					
					if (split.length != 0) {
						List<Double> massSpecData = Arrays.stream(split)
							.mapToDouble(i -> Double.parseDouble(i))
							.boxed()
							.collect(Collectors.toList());
						
						Peptide peptide = peptides.get(peptides.size() - 1);
						peptide.setMassSpecData(massSpecData);
					}
				}
				
				lineNumber++;
			}
			
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		
		this.peptides = peptides;
		
		return this;
	}
	
	public static void main(String[] args) {
		String seq = "YEQDPWGVKK";
		
		List<Integer> connections = Arrays.asList(2, 8);
		
		Map<Integer, List<Integer>> graph = PeptideSerumStability.createGraphStructure(seq, connections, PeptideType.DISULFIDE);
		
		System.out.println("SEQUENCE: " + seq);
		System.out.println();
		
		graph.entrySet()
			.forEach(e -> {
				int source = e.getKey();
				List<Integer> targets = e.getValue();
				
				if (source < seq.length()) {
					System.out.println(seq.charAt(source) + " -> " + printTargets(targets, seq));
				}
				else {
					System.out.println(source + " -> " + printTargets(targets, seq));
				}
				
			});
	}
	
	private static String printTargets(List<Integer> targets, String sequence) {
		
		StringBuilder sb = new StringBuilder();
		
		for (int target : targets) {
			if (sb.length() != 0) {
				sb.append(", ");
			}
			
			if (target < sequence.length()) {
				sb.append(sequence.charAt(target));
			}
			else {
				sb.append(target);
			}
		}
		
		return sb.toString();
	}
}
