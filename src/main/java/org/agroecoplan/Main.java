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
import org.chocosolver.util.tools.ArrayUtils;
import picocli.CommandLine;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.stream.IntStream;

@CommandLine.Command(
        description = "Agroecological crop allocation problem solver"
)
public class Main implements Runnable {

    @CommandLine.Parameters(
            description = "Path of the CSV file describing the crop calendar"
    )
    String needsFile;

    @CommandLine.Parameters(
            description = "Path of the CSV file describing the farm (vegetable beds and their adjacency relation)"
    )
    String bedsFile;

    @CommandLine.Parameters(
            description = "Path of the CSV file describing interactions between species"
    )
    String interactionsFile;

    @CommandLine.Parameters(
            description = "Path of the CSV file describing precedences interactions between species"
    )
    String precedenceFile;

    @CommandLine.Parameters(
            description = "Path of the CSV file describing delay interactions between species"
    )
    String delaysFile;

    @CommandLine.Parameters(
            description = "Output file path"
    )
    String output;

    @CommandLine.Option(
            names = {"-p", "--parallel"},
            description = "If used, parallelize the search using a parallel portfolio",
            defaultValue = "false"
    )
    boolean parallel;

    @CommandLine.Option(
            names = {"-c", "--cores"},
            description = "If parallel search is set, define the number of cores to use",
            defaultValue = "8"
    )
    int nbCores;

    @CommandLine.Option(
            names = {"-t", "--timeout"},
            description = "Time limit of the search, use -1 for no time limit",
            defaultValue = "1m"
    )
    String timeout;

    @CommandLine.Option(
            names = {"-opt", "--optimization-objective"},
            description = "Optimization objective to use. Currently available objectives are:\n" +
                    "-SAT: Constraint satisfaction only, no optimization objective\n" +
                    "-O1: Maximize the number of positive interactions\n" +
                    "-O2: Maximize the number of positive precedences\n" +
                    "Default is SAT",
            defaultValue = "SAT"
    )
    String optimizationObjective;

    @CommandLine.Option(
            names = {"-cst", "--constraints"},
            description = "Comma-separated list of the constraints to enforce (e.g. -cst C1,C2)." +
                    "Currently available constraints are:\n" +
                    "-C1: Enforce return delays\n" +
                    "-C2: Forbid negative interactions between adjacent crops\n" +
                    "-C3: Dilute identical crop, i.e. forbid adjacency between crops from the same species\n" +
                    "-C4: Forbid some beds (e.g. due to light requirements). The information of forbidden beds is in " +
                        "the crop calendar file\n" +
                    "-C5: group identical crops, i.e. force crops from the same species and same cultivation period" +
                        " to be allocated to a connected set of vegetable beds\n" +
                    "-C6: forbid negative precedences"
    )
    String constraints;

    @CommandLine.Option(
            names = {"-v", "--verbose"},
            description = "If true, display information useful for debug",
            defaultValue = "false"
    )
    boolean verbose;

    @CommandLine.Option(
            names = {"-s", "--show"},
            description = "If true, display the solution",
            defaultValue = "false"
    )
    boolean show;

    private static void enforceConstraints(AgroEcoPlanProblem problem, String[] constraints) {
        for (String c : constraints) {
            switch (c) {
                case "C1":
                    problem.postRotationConstraints();
                    break;
                case "C2":
                    problem.postForbidNegativeInteractionsConstraint();
                    break;
                case "C3":
                    problem.postDiluteSpeciesConstraint();
                    break;
                case "C5":
                    problem.postGroupIdenticalCropsConstraint();
                case "C6":
                    problem.postForbidNegativePrecedencesConstraint();
                default:
                    break;
            }
        }
    }

    @Override
    public void run() {
        Data data;

        String[] constraintList = constraints.split(",");
        boolean includeForbiddenBeds = ArrayUtils.contains(constraintList, "C4");

        if (needsFile == null || interactionsFile == null) {
            try {
                data = new Data();
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (CsvException e) {
                throw new RuntimeException(e);
            }
        } else {
            try {
                data = new Data(needsFile, interactionsFile, bedsFile, precedenceFile, delaysFile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (CsvException e) {
                throw new RuntimeException(e);
            }
        }

        if (verbose) {
            System.out.println("PARALLEL SEARCH ? " + parallel);
            if (parallel) {
                System.out.println("-> NB CORES = " + nbCores);
            }
        }

        AgroEcoPlanProblem problem;
        Solution sol;
        IntVar gain;
        //IntVar nbBeds;
        IntVar[] assignments;

        if (parallel) {
            CustomParallelPortfolio portfolio = new CustomParallelPortfolio();
            // Instantiate the first instance separately to retrieve variables
            // Retrieving the value of a variable from a Choco Solution is done by identifying the variable
            // Through its ID, which is sequentially attributed when a model's variables are created.
            // Since all models are created identically, IDs are identical across models.
            problem = new AgroEcoPlanProblem(data, includeForbiddenBeds, verbose);
            enforceConstraints(problem, constraintList);
            // Set optimization objective
            switch (optimizationObjective) {
                case "O1":
                    try {
                        problem.postInteractionConstraints();
                    } catch (AgroEcoPlanProblem.AgroecoplanException e) {
                        throw new RuntimeException(e);
                    }
                    problem.getModel().setObjective(true, problem.getGain());
                    break;
                case "O2":
                    try {
                        problem.initNumberOfPositivePrecedences();
                    } catch (AgroEcoPlanProblem.AgroecoplanException e) {
                        throw new RuntimeException(e);
                    }
                    problem.getModel().setObjective(true, problem.getGain());
                case "SAT":
                    break;
                default:
                    System.out.println("Warning: incorrect optimization objective key, SAT will be used.");
            }
            problem.getModel().getSolver().showShortStatistics();
            if (timeout != null)
                problem.getModel().getSolver().limitTime(timeout);
            portfolio.addModel(problem);
            for (int i = 1; i < nbCores; i++) {
                AgroEcoPlanProblem pb = new AgroEcoPlanProblem(data, includeForbiddenBeds, verbose);
                enforceConstraints(pb, constraintList);
                // Set optimization objective
                switch (optimizationObjective) {
                    case "O1":
                        try {
                            pb.postInteractionConstraints();
                        } catch (AgroEcoPlanProblem.AgroecoplanException e) {
                            throw new RuntimeException(e);
                        }
                        pb.getModel().setObjective(true, pb.getGain());
                        break;
                    case "O2":
                        try {
                            pb.initNumberOfPositivePrecedences();
                        } catch (AgroEcoPlanProblem.AgroecoplanException e) {
                            throw new RuntimeException(e);
                        }
                        pb.getModel().setObjective(true, pb.getGain());
                    case "SAT":
                        break;
                    default:
                        System.out.println("Warning: incorrect optimization objective key, SAT will be used.");
                }
                pb.getModel().getSolver().showShortStatistics();
                if (timeout != null)
                    pb.getModel().getSolver().limitTime(timeout);
                portfolio.addModel(pb);
            }
            portfolio.stealNogoodsOnRestarts();
            Solution[] sols = portfolio.streamSolutions().toArray(Solution[]::new);
            problem = portfolio.finderProblem;
            Model bestModel = problem.getModel();
            sol = sols[sols.length - 1];
            gain = problem.getGain();
            assignments = problem.getAssignment();
        } else {
            problem = new AgroEcoPlanProblem(data, includeForbiddenBeds, verbose);
            enforceConstraints(problem, constraintList);
            // Set optimization objective
            switch (optimizationObjective) {
                case "O1":
                    try {
                        problem.postInteractionConstraints();
                    } catch (AgroEcoPlanProblem.AgroecoplanException e) {
                        throw new RuntimeException(e);
                    }
                    problem.getModel().setObjective(true, problem.getGain());
                    break;
                case "O2":
                    try {
                        problem.initNumberOfPositivePrecedences();
                    } catch (AgroEcoPlanProblem.AgroecoplanException e) {
                        throw new RuntimeException(e);
                    }
                    problem.getModel().setObjective(true, problem.getGain());
                case "SAT":
                    break;
                default:
                    System.out.println("Warning: incorrect optimization objective key, SAT will be used.");
            }
            Solver s = problem.getModel().getSolver();
            if (timeout != null)
                s.limitTime(timeout);
            s.showShortStatistics();
            s.setSearch(Search.domOverWDegRefSearch(problem.getAssignment()));
            gain = problem.getGain();
            assignments = problem.getAssignment();
            if (optimizationObjective.equals("SAT")) {
                sol = s.findSolution();
            } else {
                sol = s.findOptimalSolution(gain, true);
            }
        }

        if (sol == null) {
            System.out.println("THERE IS NO SOLUTION");
            return;
        }

        if (gain != null) {
            if (optimizationObjective.equals("O1")) {
                System.out.println("Total positive interaction = " + sol.getIntVal(gain));
            } else {
                System.out.println("Total positive precedences = " + sol.getIntVal(gain));
            }
        }

        if (verbose) {
            int checkedPosInt = 0;
            for (int i = 0; i < assignments.length; i++) {
                for (int j = i + 1; j < assignments.length; j++) {
                    if (data.INTERACTIONS[data.NEEDS_SPECIES[i]][data.NEEDS_SPECIES[j]] >= 1 && problem.getIntervalGraphSets()[i].contains(j)) {
                        int a = sol.getIntVal(assignments[i]);
                        int b = sol.getIntVal(assignments[j]);
                        if (data.ADJACENCY[a].contains(b)) {
                            checkedPosInt++;
                        }
                    }
                }
            }
            System.out.println("Checked pos int = " + checkedPosInt);
        }

        ISet beds = SetFactory.makeConstantSet(Arrays.stream(assignments).mapToInt(v -> sol.getIntVal(v)).toArray());
        int nBeds = beds.size();
        System.out.println("Nb beds = " + nBeds);

        int maxWeek = Arrays.stream(data.NEEDS_END).max().getAsInt();

        String[][] solution = new String[problem.getNbMaxBeds()][];
        for (int i = 1; i < problem.getNbMaxBeds() + 1; i++) {
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

        if (!output.equals("null")) {
            CSVWriter writer = null;
            try {
                writer = (CSVWriter) new CSVWriterBuilder(new FileWriter(output)).withSeparator(';').build();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            for (String[] row : solution) {
                writer.writeNext(row, false);
            }
            try {
                writer.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            System.out.println("Solution exported at: " + output);
        }
        if (show) {
            String[] printSol = problem.getReadableSolution(sol);
            for (String s : printSol) {
                System.out.println(s);
            }
        }
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            new CommandLine(new Main()).usage(System.out);
            return;
        }
        System.exit(new CommandLine(new Main()).execute(args));
    }
}
