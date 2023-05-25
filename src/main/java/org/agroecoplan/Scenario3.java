package org.agroecoplan;

import com.opencsv.CSVWriter;
import com.opencsv.CSVWriterBuilder;
import com.opencsv.exceptions.CsvException;
import org.chocosolver.solver.Solution;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.search.strategy.Search;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.stream.IntStream;

public class Scenario3 {

    String needs = getClass().getClassLoader().getResource("scenario3/besoinsreelsPetC_v8_1an_s3ope.csv").getPath();
    String interactions = getClass().getClassLoader().getResource("scenario3/interactionscategoriespaut.csv").getPath();
    String beds = getClass().getClassLoader().getResource("donneesplanchesV2.csv").getPath();

    public static void main(String[] args) throws IOException, CsvException {
        Scenario3 sc3 = new Scenario3();
        Data data = new Data(sc3.needs, sc3.interactions, sc3.beds);
        AgroEcoPlanProblem problem = new AgroEcoPlanProblem(data, true, true);
        problem.postGroupIdenticalCropsConstraint();
        Solver s = problem.getModel().getSolver();
        s.showStatistics();
        s.setSearch(Search.domOverWDegRefSearch(problem.getAssignment()));
        s.limitTime("1m");
        Solution sol = s.findSolution();

        String output = "/home/justeau-allaire/SOLUTIONS_AGROECO/scenario3.csv";

        int maxWeek = Arrays.stream(data.NEEDS_END).max().getAsInt();

        String[][] solution = new String[problem.getNbMaxBeds()][];
        for (int i = 1; i < problem.getNbMaxBeds() + 1; i++) {
            int finalI = i;
            int[] needs = IntStream.range(0, problem.getAssignment().length)
                    .filter(v -> sol.getIntVal(problem.getAssignment()[v]) == finalI)
                    .toArray();
            String[] row = new String[maxWeek + 1];
            row[0] = "Planche " + i;
            for (int j = 1; j < maxWeek + 1; j++) {
                row[j] = "";
            }
            for (int n : needs) {
                for (int w = data.NEEDS_BEGIN[n]; w <= data.NEEDS_END[n]; w++) {
                    row[w] = "" + n;
                }
            }
            solution[i - 1] = row;
        }

        CSVWriter writer = (CSVWriter) new CSVWriterBuilder(new FileWriter(output)).withSeparator(';').build();

        for (String[] row : solution) {
            writer.writeNext(row, false);
        }
        writer.close();
        System.out.println("Solution exported at: " + output);
    }

}
