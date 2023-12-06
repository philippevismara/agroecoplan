package org.agroecoplan;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.chocosolver.util.objects.setDataStructures.ISet;
import org.chocosolver.util.objects.setDataStructures.SetFactory;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvException;

public class Data {

	String NEEDS_FILE;
	String INTERACTIONS_FILE;
	String PRECEDENCE_FILE;
	String DELAYS_FILE;
	String BEDS_FILE;

	// Constants

	static final int COL_SPECIES = 1;
	static final int COL_BEGIN = 2;
	static final int COL_END = 3;
	static final int COL_QUANTITY = 4;
	static final int COL_FORBIDDEN_BEDS = 5;
	static final int COL_FAMILY = 6;
	static final int COL_RETURN_DELAY = 7;
	static final int COL_FIXED_BED = 18;

	static final int COL_ADJACENT_BEDS = 1;

	// Columns precedence file

	static final int COL_SP_1 = 0;
	static final int COL_SP_2 = 1;
	static final int COL_PRECEDENCE = 2;

	// Data

	Map<String, Integer> SPECIES_TO_ID;
	int NB_SPECIES;
	String[] SPECIES;
	int[][] INTERACTIONS;
	int[][] PRECEDENCES;
	int[][] DELAYS;

	/**
	 * NB_NEEDS is the total number of crops, including duplications of the same nned (i.e. the same plant grown on several beds at the same time).
	 */
	int NB_NEEDS;
	int[] NEEDS_SPECIES;
	int[] NEEDS_BEGIN;
	int[] NEEDS_END;
	ISet[] NEEDS_FORBIDDEN_BEDS;
	String[] NEEDS_FAMILY;
	int[] NEEDS_RETURN_DELAY;

	/**
	 * Groups of identical needs (e.g. 5 tomatoes)
	 */
	List<ISet> GROUPS;
	/**
	 * If specified in the input, some needs can be assigned a fixed bed.
	 * If so, the index of the bed is specified here, otherwise set to -1.
	 */
	int[] NEEDS_FIXED_BED;

	int NB_BEDS;
	ISet[] ADJACENCY;

	public Data() throws IOException, CsvException {
		this(Data.class.getClassLoader().getResource("besoinsreelsPetC_v7_1an.csv").getPath(),
				Data.class.getClassLoader().getResource("interactionscategoriespaut.csv").getPath(),
				Data.class.getClassLoader().getResource("donneesplanchesV2.csv").getPath(),
				null, null);
	}

	int count_comment_lines(String  filename) throws FileNotFoundException, IOException {
		int skip = 0;
		try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
			String ligne;
			while ((ligne = reader.readLine()) != null) {
				if (ligne.startsWith("#"))
					skip++;
				else
					break;
			}
		}
		return skip;
	}

	public Data(String needs, String interactions, String beds) throws IOException, CsvException {
		this(needs, interactions, beds, null, null);
	}

	public Data(String needs, String interactions, String beds, String precedences, String delays) throws IOException, CsvException {
		this.NEEDS_FILE = needs;
		this.INTERACTIONS_FILE = interactions;
		this.BEDS_FILE = beds;
		this.PRECEDENCE_FILE = precedences;
		this.DELAYS_FILE = delays;

		CSVParser csvParser = new CSVParserBuilder().withSeparator(';').withIgnoreQuotations(true).build();

		// Get beds and construct adjacency
		CSVReader readerBeds = new CSVReaderBuilder(new FileReader(BEDS_FILE)).withSkipLines(count_comment_lines(BEDS_FILE) + 1)
				.withCSVParser(csvParser).build();
		List<String[]> dataBeds = readerBeds.readAll();
		this.NB_BEDS = dataBeds.size();
		this.ADJACENCY = new ISet[dataBeds.size() + 1];
		for (int i = 1; i <= NB_BEDS; i++) {
			String[] row = dataBeds.get(i - 1);
			int[] adj;
			if (row[COL_ADJACENT_BEDS].equals("")) {
				adj = new int[]{};
			} else {
				adj = Arrays.stream(row[COL_ADJACENT_BEDS].split(","))
						.mapToInt(v -> Integer.parseInt(v))
						.toArray();
			}
			this.ADJACENCY[i] = SetFactory.makeConstantSet(adj);
		}

		// Get interactions and species list
		CSVReader readerInteractions = new CSVReaderBuilder(new FileReader(INTERACTIONS_FILE))
				.withSkipLines(count_comment_lines(INTERACTIONS_FILE) + 1)
				.withCSVParser(csvParser).build();

		List<String[]> dataInteraction = readerInteractions.readAll();
		this.SPECIES_TO_ID = new HashMap<>();
		this.NB_SPECIES = dataInteraction.size();
		this.SPECIES = new String[NB_SPECIES];
		this.GROUPS = new ArrayList<>();
		this.INTERACTIONS = new int[NB_SPECIES][NB_SPECIES];
		for (int i = 0; i < NB_SPECIES; i++) {
			String[] row = dataInteraction.get(i);
			SPECIES[i] = row[0];
			SPECIES_TO_ID.put(row[0], i);
			INTERACTIONS[i] = IntStream.range(1, row.length).map(j -> {
				if (row[j].equals("")) {
					return 0;
				}
				return Integer.parseInt(row[j]);
			}).toArray();
		}
		// If given, get precedences
		if (precedences != null) {
			this.PRECEDENCES = new int[NB_SPECIES][NB_SPECIES];
			CSVReader readerPrecedences = new CSVReaderBuilder(new FileReader(PRECEDENCE_FILE))
					.withSkipLines(count_comment_lines(PRECEDENCE_FILE) + 1)
					.withCSVParser(csvParser).build();
			List<String[]> dataPrecedence = readerPrecedences.readAll();
			for (int i = 0; i < NB_SPECIES; i++) {
				String[] row = dataPrecedence.get(i);
				PRECEDENCES[i] = IntStream.range(1, row.length).map(j -> {
					if (row[j].equals("")) {
						return 0;
					}
					return Integer.parseInt(row[j]);
				}).toArray();
			}
		}
		// If given, get delays
		if (delays != null) {
			this.DELAYS = new int[NB_SPECIES][NB_SPECIES];
			CSVReader readerPrecedences = new CSVReaderBuilder(new FileReader(DELAYS_FILE))
					.withSkipLines(count_comment_lines(DELAYS_FILE) + 1)
					.withCSVParser(csvParser).build();
			List<String[]> dataPrecedence = readerPrecedences.readAll();
			for (int i = 0; i < NB_SPECIES; i++) {
				String[] row = dataPrecedence.get(i);
				DELAYS[i] = IntStream.range(1, row.length).map(j -> {
					if (row[j].equals("")) {
						return 0;
					}
					return Integer.parseInt(row[j]);
				}).toArray();
			}
		}

		// Compute total needs

		CSVReader readerNeeds = new CSVReaderBuilder(new FileReader(NEEDS_FILE))
				.withSkipLines(count_comment_lines(NEEDS_FILE)  + 1)
				.withCSVParser(csvParser).build();
		List<String[]> dataNeeds = readerNeeds.readAll();

		this.NB_NEEDS = 0;

		for (int i = 0; i < dataNeeds.size(); i++) {
			String[] row = dataNeeds.get(i);
			int quantity = Integer.parseInt(row[COL_QUANTITY]);
			NB_NEEDS += quantity;
		}

		NEEDS_SPECIES = new int[NB_NEEDS];
		NEEDS_BEGIN = new int[NB_NEEDS];
		NEEDS_END = new int[NB_NEEDS];
		NEEDS_FORBIDDEN_BEDS = new ISet[NB_NEEDS];
		NEEDS_FAMILY = new String[NB_NEEDS];
		NEEDS_RETURN_DELAY = new int[NB_NEEDS];
		NEEDS_FIXED_BED = new int[NB_NEEDS];

		int offset = 0;
		for (int i = 0; i < dataNeeds.size(); i++) {
			String[] row = dataNeeds.get(i);
			int quantity = Integer.parseInt(row[COL_QUANTITY]);
			int species = SPECIES_TO_ID.get(row[COL_SPECIES]);
			int begin = Integer.parseInt(row[COL_BEGIN]);
			int end = Integer.parseInt(row[COL_END]);
			int returnDelay = row[COL_RETURN_DELAY].equals("") ? 0 : Integer.parseInt(row[COL_RETURN_DELAY]);
			String family = row[COL_FAMILY];
			int[] forbiddenBeds;
			if (row[COL_FORBIDDEN_BEDS].equals("")) {
				forbiddenBeds = new int[] {};
			} else {
				forbiddenBeds = Arrays.stream(row[COL_FORBIDDEN_BEDS].split(",")).mapToInt(v -> Integer.parseInt(v))
						.toArray();
			}
			int[] fixedBeds;
			if (row[COL_FIXED_BED].equals("")) {
				fixedBeds = IntStream.range(0, quantity).map(v -> -1).toArray();
			} else {
				fixedBeds = Arrays.stream(row[COL_FIXED_BED].split(","))
						.mapToInt(v -> Integer.parseInt(v))
						.toArray();
			}
			for (int j = i + offset; j < i + offset + quantity; j++) {
				NEEDS_SPECIES[j] = species;
				NEEDS_BEGIN[j] = begin;
				NEEDS_END[j] = end;
				NEEDS_RETURN_DELAY[j] = returnDelay;
				NEEDS_FAMILY[j] = family;
				NEEDS_FORBIDDEN_BEDS[j] = SetFactory.makeConstantSet(forbiddenBeds);
				NEEDS_FIXED_BED[j] = fixedBeds[j - i - offset];
			}
			if (quantity > 1) {
				ISet s = SetFactory.makeBipartiteSet(0);
				for (int j = i + offset; j < i + offset + quantity; j++) {
					s.add(j);
				}
				GROUPS.add(s);
			}
			offset += quantity - 1;
		}
	}
}
