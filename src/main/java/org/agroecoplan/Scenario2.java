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

public class Scenario2 {

    String needs = getClass().getClassLoader().getResource("scenario2/besoinsreelsPetC_v8_1an_s2DiluRota.csv").getPath();
    String interactions = getClass().getClassLoader().getResource("scenario2/interactionscategoriespaut.csv").getPath();
    String beds = getClass().getClassLoader().getResource("donneesplanchesV2.csv").getPath();

    public static void main(String[] args) throws IOException, CsvException {
        Scenario2 sc2 = new Scenario2();
        Data data = new Data(sc2.needs, sc2.interactions, sc2.beds);
        AgroEcoPlanProblem problem = new AgroEcoPlanProblem(data, true, true);
        problem.postDiluteSpeciesConstraint();
        problem.postRotationConstraints();
        Solver s = problem.getModel().getSolver();
        s.setSearch(Search.domOverWDegRefSearch(problem.getAssignment()));
        s.showStatistics();
        s.limitTime("1m");
        Solution sol = s.findSolution();

        String output = "/home/justeau-allaire/SOLUTIONS_AGROECO/scenario2.csv";

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
