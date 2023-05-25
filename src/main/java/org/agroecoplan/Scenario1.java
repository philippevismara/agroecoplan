package org.agroecoplan;

import com.opencsv.CSVWriter;
import com.opencsv.CSVWriterBuilder;
import com.opencsv.exceptions.CsvException;
import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solution;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.search.strategy.Search;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.util.objects.setDataStructures.ISet;
import org.chocosolver.util.objects.setDataStructures.SetFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.stream.IntStream;

public class Scenario1 {

    String needs = getClass().getClassLoader().getResource("scenario1/besoinsreelsPetC_v8_1an_s1inte.csv").getPath();
    String interactions = getClass().getClassLoader().getResource("scenario1/interactionscategoriespaut.csv").getPath();
    String beds = getClass().getClassLoader().getResource("donneesplanchesV2.csv").getPath();

    public static void main(String[] args) throws IOException, CsvException {
        Scenario1 sc1 = new Scenario1();
        Data data = new Data(sc1.needs, sc1.interactions, sc1.beds);
        boolean parallel = true;
        String timeout = "1h";
        int nbCores = 8;
        String output = "/home/justeau-allaire/SOLUTIONS_AGROECO/scenario1.csv";
        IntVar[] assignments;
        IntVar gain;
        Solution sol;
        int nbMaxBeds;
        if (!parallel) {
            AgroEcoPlanProblem problem = new AgroEcoPlanProblem(data, true, true);
            problem.postForbidNegativeInteractionsConstraint();
            problem.postInteractionConstraints();
            assignments = problem.getAssignment();
            gain = problem.getGain();
            nbMaxBeds = problem.getNbMaxBeds();
            Solver s = problem.getModel().getSolver();
            s.showStatistics();
            s.setSearch(Search.domOverWDegRefSearch(problem.getAssignment()));
            s.limitTime("5m");
            sol = s.findOptimalSolution(problem.getGain(), true);
        } else {
            CustomParallelPortfolio portfolio = new CustomParallelPortfolio();
            // Instantiate the first instance separately to retrieve variables
            // Retrieving the value of a variable from a Choco Solution is done by identifying the variable
            // Through its ID, which is sequentially attributed when a model's variables are created.
            // Since all models are created identically, IDs are identical across models.
            AgroEcoPlanProblem problem = new AgroEcoPlanProblem(data, true, true);
            nbMaxBeds = problem.getNbMaxBeds();
            problem.postForbidNegativeInteractionsConstraint();
            problem.postInteractionConstraints();
            problem.getModel().setObjective(true, problem.getGain());
            problem.getModel().getSolver().showShortStatistics();
            if (timeout != null)
                problem.getModel().getSolver().limitTime(timeout);
            portfolio.addModel(problem);
            for (int i = 1; i < nbCores; i++) {
                AgroEcoPlanProblem pb = new AgroEcoPlanProblem(data, true, false);
                pb.postForbidNegativeInteractionsConstraint();
                pb.postInteractionConstraints();
                pb.getModel().setObjective(true, pb.getGain());
                pb.getModel().getSolver().showShortStatistics();
                if (timeout != null)
                    pb.getModel().getSolver().limitTime(timeout);
                portfolio.addModel(pb);
            }
            portfolio.stealNogoodsOnRestarts();
            Solution[] sols = portfolio.streamSolutions().toArray(Solution[]::new);
            //while (portfolio.solve()) {
            //}
            problem = portfolio.finderProblem;
            Model bestModel = problem.getModel();
            //sol = bestModel.getSolver().defaultSolution();
            sol = sols[sols.length - 1];
            gain = problem.getGain();
            assignments = problem.getAssignment();

            if (sol == null) {
                System.out.println("THERE IS NO SOLUTION");
                return;
            }

            System.out.println("Total positive interaction = " + sol.getIntVal(gain));
            ISet beds = SetFactory.makeConstantSet(Arrays.stream(assignments).mapToInt(v -> sol.getIntVal(v)).toArray());
            int nBeds = beds.size();
            System.out.println("Nb beds = " + nBeds);

            int maxWeek = Arrays.stream(data.NEEDS_END).max().getAsInt();

            String[][] solution = new String[nbMaxBeds][];
            for (int i = 1; i < nbMaxBeds + 1; i++) {
                int finalI = i;
                int[] needs = IntStream.range(0, assignments.length)
                        .filter(v -> sol.getIntVal(assignments[v]) == finalI)
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

}
