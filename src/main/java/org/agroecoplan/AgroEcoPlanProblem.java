package org.agroecoplan;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solution;
import org.chocosolver.solver.constraints.Constraint;
import org.chocosolver.solver.constraints.extension.Tuples;
import org.chocosolver.solver.constraints.extension.hybrid.ISupportable;
import org.chocosolver.solver.variables.BoolVar;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.variables.UndirectedGraphVar;
import org.chocosolver.util.iterators.DisposableValueIterator;
import org.chocosolver.util.objects.graphs.GraphFactory;
import org.chocosolver.util.objects.graphs.UndirectedGraph;
import org.chocosolver.util.objects.setDataStructures.ISet;
import org.chocosolver.util.objects.setDataStructures.SetFactory;
import org.chocosolver.util.objects.setDataStructures.SetType;
import org.chocosolver.solver.constraints.extension.hybrid.HybridTuples;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.chocosolver.solver.constraints.extension.hybrid.HybridTuples.*;

/**
 * Agro-ecological crop planning problem instance.
 */
public class AgroEcoPlanProblem {

    public static final int NB_WEEKS_IN_YEAR = 52;

    private Data data;
    private int nbMaxBeds;

    private ISet[] intervalGraphSets;

    int[][] intervalGraph;

    private ISet[] intervalGraphSetsWithRotations;
    int[][] intervalGraphWithRotations;

    private Model model;
    private IntVar[] assignment;
    private IntVar gain;

    private IntVar nbBeds;

    private boolean verbose = false;

    /**
     * In verbose mode, if not null, détails the 01 criteria calculus
     */
    public Function<Solution, String> showO1Details = null;

    /**
     * In verbose mode, if not null, détails the 02 criteria calculus
     */public Function<Solution, String> showO2Details = null;

    public AgroEcoPlanProblem(Data data, boolean includeForbiddenBeds, boolean verbose) {
        this.data = data;
        this.nbMaxBeds = data.NB_BEDS;
        this.verbose = verbose;
        if (verbose) {
            showDataSummary();
        }
        initIntervalGraphs();
        this.model = new Model();
        initBaseModel(includeForbiddenBeds, verbose);
        breakSymmetries();
    }

    private void initIntervalGraphs() {
        intervalGraphSets = new ISet[data.NB_NEEDS];
        intervalGraphSetsWithRotations = new ISet[data.NB_NEEDS];
        intervalGraph = new int[data.NB_NEEDS][];
        intervalGraphWithRotations = new int[data.NB_NEEDS][];
        for (int i = 0; i < data.NB_NEEDS; i++) {
            intervalGraphSets[i] = SetFactory.makeBipartiteSet(0);
            intervalGraphSetsWithRotations[i] = SetFactory.makeBipartiteSet(0);
            for (int j = 0; j < data.NB_NEEDS; j++) {
                if (i != j) {
                    // Effective cultivation period
                    int si = data.NEEDS_BEGIN[i];
                    int sj = data.NEEDS_BEGIN[j];
                    int ei = data.NEEDS_END[i];
                    int ej = data.NEEDS_END[j];
                    // If the intervals are intersecting, there is no need to consider the rotation and cie constraints
                    if (IntervalUtils.intersect(si, ei, sj, ej)) {
                        intervalGraphSets[i].add(j);
                        intervalGraphSetsWithRotations[i].add(j);
                    } else {
                        // TODO LEGACY WITH RETURN DELAY IN NEEDS FILE
                        if (data.DELAYS == null) {
                            if (data.NEEDS_FAMILY[i].equals(data.NEEDS_FAMILY[j])) {
                                ei += data.NEEDS_RETURN_DELAY[i] * NB_WEEKS_IN_YEAR - (ei - si);
                                ej += data.NEEDS_RETURN_DELAY[j] * NB_WEEKS_IN_YEAR - (ej - si);
                                if (IntervalUtils.intersect(si, ei, sj, ej)) {
                                    intervalGraphSetsWithRotations[i].add(j);
                                }
                            }
                        } else {
                            // 1 - Need for compost constraint:
                            //      if both b_i and b_j need compost, we extend their cultivation end to 1 year
                            //      (52 weeks).
                            // TODO
                            // 2 - Turnover constraint:
                            //      when b_i and b_j need a return delay, we use is to extend their
                            //      cultivation end, thus ensure the satisfaction of the return delay in the interval graph
                            //      directly.
                            if (data.DELAYS[data.NEEDS_SPECIES[i]][data.NEEDS_SPECIES[j]] > 0) {
                                ei += data.NEEDS_RETURN_DELAY[i] * NB_WEEKS_IN_YEAR - (ei - si);
                                ej += data.NEEDS_RETURN_DELAY[j] * NB_WEEKS_IN_YEAR - (ej - si);
                                if (IntervalUtils.intersect(si, ei, sj, ej)) {
                                    intervalGraphSetsWithRotations[i].add(j);
                                }
                            }
                        }
                    }
                }
            }
            intervalGraph[i] = intervalGraphSets[i].toArray();
            intervalGraphWithRotations[i] = intervalGraphSetsWithRotations[i].toArray();
        }
    }

    /**
     * 1. Initialize the base variables of the problem, i.e. create assignment variables with a
     * domain consistent with the available (or forbidden) beds described in the data.
     * 2. Post the base constraints of the problem, i.e. ensure that there will be no
     * overlapping crops in a same bed. This is done by identifying maximal cliques in the
     * chordal interval graph and posting allDifferent constraints over these maximal cliques.
     */
    private void initBaseModel(boolean includeForbiddenBeds, boolean verbose) {
        // Init assignment variables
        this.assignment = new IntVar[data.NB_NEEDS];
        for (int i = 0; i < data.NB_NEEDS; i++) {
            int finalI = i;
            int[] domain;
            if (data.NEEDS_FIXED_BED[i] >= 0) {
                domain = new int[] {data.NEEDS_FIXED_BED[i]};
            } else if (includeForbiddenBeds) {
                domain = IntStream.range(1, nbMaxBeds + 1)
                        .filter(v -> !data.NEEDS_FORBIDDEN_BEDS[finalI].contains(v))
                        .toArray();
            } else {
                domain = IntStream.range(1, nbMaxBeds + 1)
                        .toArray();
            }

            //assignment[i] = model.intVar(i+"s"+data.SPECIES[data.NEEDS_SPECIES[i]], domain);
            assignment[i] = model.intVar(i+"s"+data.NEEDS_SPECIES[i], domain);
        }
        // Find maximal cliques
        List<ISet> maximalCliques = ChordalGraphUtils.findMaximalCliques(intervalGraph);
        int cliqueNumber = maximalCliques.stream().mapToInt(s -> s.size()).max().getAsInt();
        if (verbose) {
            System.out.println("CHORDAL WITHOUT ROTATIONS (for debug) ? " + ChordalGraphUtils.isChordal(intervalGraph));
            System.out.println("CHORDAL WITH ROTATIONS ? " + ChordalGraphUtils.isChordal(intervalGraphWithRotations));
            System.out.println("NB MAXIMAL CLIQUES = " + maximalCliques.size());
            System.out.println("CLIQUE NUMBER = " + cliqueNumber);
        }
        // TODO: if the interval graph with rotations is chordal, allDifferent for all maximal cliques,
        //      and separators should be sufficient, but we should prove it to be sure.
        // With difference constraints for every edge

        /* for (int i = 0; i < data.NB_NEEDS; i++) {
             IntVar[] overlapping = IntStream.of(intervalGraph[i])
                     .mapToObj(v -> assignment[v])
                     .toArray(IntVar[]::new);
             for (IntVar iv : overlapping) {
                 model.arithm(assignment[i], "!=", iv).post();
             }
         }*/

        // With allDifferent on every maximal clique
        for (ISet clique : maximalCliques) {
            IntVar[] vars = IntStream.of(clique.toArray()).mapToObj(i -> assignment[i]).toArray(IntVar[]::new);
            model.allDifferent(vars).post();
        }
        //this.nbBeds = model.intVar(cliqueNumber, nbMaxBeds);
        //model.nValues(assignment, nbBeds).post();
    }

    /**
     * Post rotation constraints: any two crops from the same botanical family must respect a rotation delay
     * if they are cultivated in the same bed.
     */
    public void postRotationConstraints() {
        for (int i = 0; i < data.NB_NEEDS; i++) {
            int finalI = i;
            IntVar[] overlapping = IntStream.of(intervalGraphWithRotations[i])
                    .filter(j -> !intervalGraphSets[finalI].contains(j)).mapToObj(j -> assignment[j])
                    .toArray(IntVar[]::new);
            for (IntVar iv : overlapping) {
                model.arithm(assignment[i], "!=", iv).post();
            }
        }
    }

    /**
     * Post constraints such that no two crops with negative interactions are adjacents.
     */
    public void postForbidNegativeInteractionsConstraint() {
        for (int i = 0; i < data.NB_NEEDS; i++) {
            for (int j = i + 1; j < data.NB_NEEDS; j++) {
                if (data.INTERACTIONS[data.NEEDS_SPECIES[i]][data.NEEDS_SPECIES[j]] < 0
                        && intervalGraphSets[i].contains(j)
                        && IntervalUtils.minDistance(assignment[i], assignment[j]) <= 1) {
                    /* Tuples forbidden = new Tuples(false);
                    DisposableValueIterator vit = assignment[i].getValueIterator(true);
                    while (vit.hasNext()) {
                        int a = vit.next();
                        for (int b : data.ADJACENCY[a]) {
                            forbidden.add(a, b);
                        }
                    }
                    model.table(assignment[i], assignment[j], forbidden).post();*/
                    model.distance(assignment[i], assignment[j], ">", 1).post();
                }
            }
        }
    }

    /**
     * Post constraints such that no two crops from the same species are adjacent.
     */
    public void postDiluteSpeciesConstraint() {
        for (int i = 0; i < data.NB_NEEDS; i++) {
            for (int j = i + 1; j < data.NB_NEEDS; j++) {
                if (data.NEEDS_SPECIES[i] == data.NEEDS_SPECIES[j] && intervalGraphSets[i].contains(j)
                        && IntervalUtils.minDistance(assignment[i], assignment[j]) <= 1) {
                    Tuples forbidden = new Tuples(false);
                    DisposableValueIterator vit = assignment[i].getValueIterator(true);
                    while (vit.hasNext()) {
                        int a = vit.next();
                        for (int b : data.ADJACENCY[a]) {
                            forbidden.add(a, b);
                        }
                    }
                    model.table(assignment[i], assignment[j], forbidden).post();
                }
            }
        }
    }

    /**
     * Post constraints such that no two crops from the same family are adjacent.
     */
    public void postDiluteFamilyConstraint() {
        for (int i = 0; i < data.NB_NEEDS; i++) {
            for (int j = i + 1; j < data.NB_NEEDS; j++) {
                if (data.NEEDS_FAMILY[i] == data.NEEDS_FAMILY[j] && intervalGraphSets[i].contains(j)
                        && IntervalUtils.minDistance(assignment[i], assignment[j]) <= 1) {
                    Tuples forbidden = new Tuples(false);
                    DisposableValueIterator vit = assignment[i].getValueIterator(true);
                    while (vit.hasNext()) {
                        int a = vit.next();
                        for (int b : data.ADJACENCY[a]) {
                            forbidden.add(a, b);
                        }
                    }
                    model.table(assignment[i], assignment[j], forbidden).post();
                }
            }
        }
    }

    /**
     * Post constraints such that identical crops (same species and same period) are grouped in a connected set
     * of vegatable beds.
     */
    public void postGroupIdenticalCropsConstraint() {
        for (ISet s : data.GROUPS) {
            IntVar[] group = IntStream.of(s.toArray()).mapToObj(i -> assignment[i]).toArray(IntVar[]::new);
            Tuples allowed = new Tuples(false);
            DisposableValueIterator vit = group[0].getValueIterator(true);
            while (vit.hasNext()) {
                int a = vit.next();
                boolean possible = true;
                for (int i = a + 1; i < a + group.length; i++) {
                    if (!data.ADJACENCY[i - 1].contains(i)) {
                        possible = false;
                        break;
                    }
                }
                if (possible) {
                    allowed.add(IntStream.range(a, a + group.length).toArray());
                }
            }
            model.table(group, allowed).post();
        }
    }

    /**
     * Post interaction constraints and initialize the gain variable.
     * WARNING: Negative interactions are currently forbidden, and the gain variable is created taking
     * advantage of this (no need to subtract negative interactions). We should not forget it if we want to
     * allow negative interactions in the future.
     */
    public IntVar postInteractionConstraints() throws AgroecoplanException {
        List<int[]> positivePairs = new ArrayList<>();
        for (int i = 0; i < data.NB_NEEDS; i++) {
            for (int j = i + 1; j < data.NB_NEEDS; j++) {
                if (data.INTERACTIONS[data.NEEDS_SPECIES[i]][data.NEEDS_SPECIES[j]] == 1
                        && intervalGraphSets[i].contains(j)
                        && IntervalUtils.minDistance(assignment[i], assignment[j]) <= 1) {
                    positivePairs.add(new int[] { i, j });
                }
            }
        }
        return postInteractionReifTable(positivePairs);
        //postInteractionCustomPropBased(positivePairs);
    }

    public void postForbidNegativePrecedencesConstraint() {
        // 1. Construct the sequence of non-overlapping crops.
        //     a. Sort all crops by descending order of crop beginning.
        Integer[] sortedCrops = IntStream.range(0, data.NB_NEEDS).mapToObj(i -> i).toArray(Integer[]::new);
        Arrays.sort(sortedCrops, (i, j) -> data.NEEDS_BEGIN[j] - data.NEEDS_BEGIN[i]);
        //     b. for all crop, construct the non overlapping precedence sequence.
        for (int i = 0; i < sortedCrops.length; i++) {
            int cropA = sortedCrops[i];
            int spA = data.NEEDS_SPECIES[cropA];
            boolean startSmartTable = false;
            for (int j = i + 1; j <sortedCrops.length; j++) {
                int cropB = sortedCrops[j];
                int spB = data.NEEDS_SPECIES[cropB];
                // If cropB can precede cropA, then following sequences need a smart table constraint
                if (data.PRECEDENCES[spA][spB] >= 0) {
                    startSmartTable = true;
                } else if (!startSmartTable) {
                    // If cropB cannot precede cropA and there is no option of intermediate preceding crop
                    // (startRegular = false), then assignment[cropA] != assignment[cropB]
                    model.arithm(assignment[cropA], "!=", assignment[cropB]).post();
                } else {
                    // Reconstruct the assignment sequence, backward from cropA to cropB (inclusive)
                    IntVar[] seq = new IntVar[j - i + 1];
                    for (int k = 0; k < j - i + 1; k++) {
                        seq[k] = assignment[sortedCrops[k + i]];
                    }
                    // Post smart table constraint
                    HybridTuples tuples = new HybridTuples();
                    ISupportable[] t = new ISupportable[seq.length]; // a[cropA] != a[cropB] i.e. t[0]=* and t[j-i+1]=t[0]
                    ISupportable[][] tt = new ISupportable[seq.length - 2][]; // a[cropA] != a[cropB] && one intermediary i.e. tt[m][0]=* and tt[m][j-i+1]=tt[m][0]=tt[m][m+1]
                    for (int l = 0; l < tt.length; l++) {
                        tt[l] = new ISupportable[seq.length];
                        tt[l][0] = any();
                        tt[l][seq.length - 1] = eq(col(0), 0);
                    }
                    t[0] = any();
                    t[seq.length - 1] = ne(col(0));
                    for (int l = 1; l < seq.length - 1; l++) {
                        t[l] = any();
                        for (int m = 0; m < tt.length; m++) {
                            if (m + 1 == l) {
                                tt[m][l] = eq(col(0), 0);
                            } else {
                                tt[m][l] = any();
                            }
                        }
                    }
                    tuples.add(t);
                    for (ISupportable[] a : tt) {
                        tuples.add(a);
                    }
                    model.table(seq, tuples).post();
                }
            }
        }
    }

    public void initNumberOfPositivePrecedences() throws AgroecoplanException {
        // NOT USED (problems with smart tables)
        // 1. Construct the sequence of non-overlapping crops.
        //     a. Sort all crops by descending order of crop beginning.
        Integer[] sortedCrops = IntStream.range(0, data.NB_NEEDS).mapToObj(i -> i).toArray(Integer[]::new);
        Arrays.sort(sortedCrops, (i, j) -> data.NEEDS_BEGIN[j] - data.NEEDS_BEGIN[i]);
        ArrayList<BoolVar> boolVars = new ArrayList<>();
        //     b. for all crop, construct the non overlapping precedence sequence.
        for (int i = 0; i < sortedCrops.length; i++) {
            int cropA = sortedCrops[i];
            int spA = data.NEEDS_SPECIES[cropA];
            for (int j = i + 1; j <sortedCrops.length; j++) {
                int cropB = sortedCrops[j];
                int spB = data.NEEDS_SPECIES[cropB];
                // If cropB can precede cropA, then following sequences need a smart table constraint
                if (data.PRECEDENCES[spA][spB] == 1) {
                    // Reconstruct the assignment sequence, backward from cropA to cropB (inclusive)
                    IntVar[] seq = new IntVar[j - i + 1];
                    for (int k = 0; k < j - i + 1; k++) {
                        seq[k] = assignment[sortedCrops[k + i]];
                    }
                    if (seq.length == 2) {
                        boolVars.add(model.arithm(seq[0], "=", seq[1]).reify());
                    } else {
                        // Post smart table constraint
                        HybridTuples tuples = new HybridTuples();
                        ISupportable[] t = new ISupportable[seq.length];
                        t[0] = any();
                        t[seq.length - 1] = eq(col(0), 0);
                        for (int l = 1; l < seq.length - 1; l++) {
                            t[l] = ne(col(0), 0);
                        }
                        tuples.add(t);
                        boolVars.add(model.table(seq, tuples).reify());
                    }
                }
            }
        }
        IntVar sum = model.intVar(0, boolVars.size());
        BoolVar[] boolVarsA = new BoolVar[boolVars.size()];
        for (int i = 0; i < boolVarsA.length; i++) {
            boolVarsA[i] = boolVars.get(i);
        }
        model.sum(boolVarsA, "=", sum).post();
        if (gain != null) {
            throw new AgroecoplanException("Gain is already defined");
        }
        this.gain = sum;

    }

    public IntVar initNumberOfPositivePrecedencesCountBased() throws AgroecoplanException {
        // 1. Construct the sequence of non-overlapping crops.
        //     a. Sort all crops by descending order of crop beginning.
        Integer[] sortedCrops = IntStream.range(0, data.NB_NEEDS).mapToObj(i -> i).toArray(Integer[]::new);
        Arrays.sort(sortedCrops, (i, j) -> data.NEEDS_BEGIN[j] - data.NEEDS_BEGIN[i]);
        ArrayList<BoolVar> boolVars = new ArrayList<>();
        //     b. for all crop, construct the non overlapping precedence sequence.
        for (int i = 0; i < sortedCrops.length; i++) {
            int cropA = sortedCrops[i];
            int spA = data.NEEDS_SPECIES[cropA];
            for (int j = i + 1; j <sortedCrops.length; j++) {
                int cropB = sortedCrops[j];
                int spB = data.NEEDS_SPECIES[cropB];
                // If cropB can precede cropA, then following sequences need a smart table constraint
                if (data.PRECEDENCES[spA][spB] == 1) {
                    // Reconstruct the assignment sequence, backward from cropA to cropB (inclusive)
                    IntVar[] seq = new IntVar[j - i + 1];
                    for (int k = 0; k < j - i + 1; k++) {
                        seq[k] = assignment[sortedCrops[k + i]];
                    }
                    if (seq.length == 2) {
                        boolVars.add(model.arithm(seq[0], "=", seq[1]).reify());
                    } else {
                        // Post reified count
                        IntVar[] intermediate = new IntVar[seq.length - 2];
                        /*for (int k = 1; k < seq.length - 1; k++) {
                            intermediate[k - 1] = seq[k];
                        }*/
                        System.arraycopy(seq, 1, intermediate, 0, seq.length - 2);
                        BoolVar b1 = model.count(seq[0], intermediate, model.intVar(0)).reify();
                        // Post reified equal
                        BoolVar b2 = model.arithm(seq[0], "=", seq[seq.length - 1]).reify();
                        if (verbose) {
                            BoolVar band = model.boolVar(cropA + "prec" + cropB);
                            model.and(b1, b2).reifyWith(band);
                            boolVars.add(band);
                        }
                        else
                            boolVars.add(model.and(b1, b2).reify());
                    }
                }
            }
        }
        final IntVar sum = model.intVar(0, boolVars.size());
        final BoolVar[] boolVarsA = new BoolVar[boolVars.size()];
        for (int i = 0; i < boolVarsA.length; i++) {
            boolVarsA[i] = boolVars.get(i);
        }
        model.sum(boolVarsA, "=", sum).post();


        if (verbose) {
            showO2Details = sol -> {
                StringBuilder txt = new StringBuilder("Number Of Positive Precedences=" + sol.getIntVal(sum) + "   over " + boolVarsA.length + ": ");
                for (BoolVar integers : boolVarsA) {
                    if (sol.getIntVal(integers) > 0) txt.append(integers.getName()).append(", ");
                }
                return txt.toString();
            };
        }

        return sum;
/*        if (gain != null) {
            throw new AgroecoplanException("Gain is already defined");
        }
        this.gain = sum;*/
    }

    public IntVar postInteractionReifTable(List<int[]> positivePairs) throws AgroecoplanException {
        BoolVar[] positive = new BoolVar[positivePairs.size()];
        for (int i = 0; i < positivePairs.size(); i++) {
            int[] p = positivePairs.get(i);
            Tuples allowed = new Tuples(true);
            DisposableValueIterator vit = assignment[p[0]].getValueIterator(true);
            while (vit.hasNext()) {
                int a = vit.next();
                for (int b : data.ADJACENCY[a]) {
                    allowed.add(a, b);
                }
            }
            if (verbose) {
                BoolVar btable = model.boolVar( assignment[p[0]].getName() + "-" + assignment[p[1]].getName() );
                model.table(assignment[p[0]], assignment[p[1]], allowed).reifyWith(btable);
                positive[i] = btable;
            }
            else
                positive[i] = model.table(assignment[p[0]], assignment[p[1]], allowed).reify();
        }
        /*        if (gain != null) {
            throw new AgroecoplanException("Gain is already defined");
        }*/
        IntVar g = model.intVar(0, positivePairs.size());
        model.sum(positive, "=", g).post();

        if (verbose) {
            showO1Details = sol -> {
                StringBuilder txt = new StringBuilder("Number Of Positive Interactions=" + sol.getIntVal(g) + "   over " + positive.length + ": ");
                for (BoolVar integers : positive) {
                    if (sol.getIntVal(integers) > 0) txt.append(integers.getName()).append(", ");
                }
                return txt.toString();
            };
        }

        return g;
    }

    public void postInteractionReifBased(List<int[]> positivePairs) throws AgroecoplanException {
        BoolVar[] positiveDists = new BoolVar[positivePairs.size()];
        for (int i = 0; i < positivePairs.size(); i++) {
            int[] p = positivePairs.get(i);
            positiveDists[i] = model.distance(assignment[p[0]], assignment[p[1]], "=", 1).reify();
        }
        if (gain != null) {
            throw new AgroecoplanException("Gain is already defined");
        }
        gain = model.intVar(1, positivePairs.size());
        model.sum(positiveDists, "=", gain).post();
    }

    private void postInteractionCountBased(List<int[]> positivePairs) throws AgroecoplanException {
        IntVar[][] positiveAssignments = new IntVar[positivePairs.size()][];
        for (int i = 0; i < positivePairs.size(); i++) {
            int[] p = positivePairs.get(i);
            positiveAssignments[i] = new IntVar[] { assignment[p[0]], assignment[p[1]] };
        }
        IntVar[] positiveDists = new IntVar[positivePairs.size()];
        for (int i = 0; i < positivePairs.size(); i++) {
            int[] p = positivePairs.get(i);
            positiveDists[i] = model.intVar(1, nbMaxBeds);
            model.distance(assignment[p[0]], assignment[p[1]], "=", positiveDists[i]).post();
        }
        if (gain != null) {
            throw new AgroecoplanException("Gain is already defined");
        }
        gain = model.intVar(1, positivePairs.size());
        model.count(1, positiveDists, gain).post();
    }

    private void postInteractionCustomPropBased(List<int[]> positivePairs) throws AgroecoplanException {
        IntVar[][] positiveAssignments = new IntVar[positivePairs.size()][];
        for (int i = 0; i < positivePairs.size(); i++) {
            int[] p = positivePairs.get(i);
            positiveAssignments[i] = new IntVar[] { assignment[p[0]], assignment[p[1]] };
        }
        if (gain != null) {
            throw new AgroecoplanException("Gain is already defined");
        }
        gain = model.intVar(1, positivePairs.size());
        model.post(new Constraint("interactionGain", new PropInteractionGain(gain, positiveAssignments)));
    }

    private void postInteractionGraphBased(List<int[]> positivePairs) {
        UndirectedGraph glb = GraphFactory.makeStoredUndirectedGraph(model, assignment.length, SetType.BITSET, SetType.BIPARTITESET);
        UndirectedGraph gub = GraphFactory.makeStoredUndirectedGraph(model, assignment.length, SetType.BITSET, SetType.BIPARTITESET);
        for (int[] i : positivePairs) {
            gub.addNode(i[0]);
            gub.addNode(i[1]);
            glb.addNode(i[0]);
            glb.addNode(i[1]);
        }
        for (int[] pair : positivePairs) {
            gub.addEdge(pair[0], pair[1]);
        }
        UndirectedGraphVar g = model.graphVar("g", glb, gub);
        model.post(new Constraint("chan", new PropInteractionGainGraph(g, assignment, positivePairs, intervalGraphSetsWithRotations)));
        gain = model.intVar(1, positivePairs.size());
        model.nbEdges(g, gain).post();
    }

    /**
     * Everything related to symmetry-breaking in the base model should be included here.
     */
    private void breakSymmetries() {
        for (ISet s : data.GROUPS) {
            IntVar[] group = IntStream.of(s.toArray()).mapToObj(i -> assignment[i]).toArray(IntVar[]::new);
            model.increasing(group, 1).post();
        }
    }

    public Data getData() {
        return data;
    }

    public int getNbMaxBeds() {
        return nbMaxBeds;
    }

    public int[][] getIntervalGraph() {
        return intervalGraph;
    }

    public int[][] getIntervalGraphWithRotations() {
        return intervalGraphWithRotations;
    }

    public Model getModel() {
        return model;
    }

    public IntVar[] getAssignment() {
        return assignment;
    }

    public IntVar getGain() {
        return gain;
    }

    public void setGain(IntVar gain) {
        this.gain = gain;
    }


    public IntVar getNbBeds() {
        return nbBeds;
    }

    public ISet[] getIntervalGraphSets() {
        return intervalGraphSets;
    }

    public ISet[] getIntervalGraphSetsWithRotations() {
        return intervalGraphSetsWithRotations;
    }

    private void showDataSummary() {
        System.out.println("NEEDS : " + data.NEEDS_FILE);
        System.out.println("INTERACTIONS : " + data.INTERACTIONS_FILE);
        System.out.println("NB SPECIES = " + data.SPECIES.length);
        System.out.println("NB NEEDS = " + data.NB_NEEDS);
        System.out.println("NEEDS SPECIES = " + Arrays.toString(data.NEEDS_SPECIES));
        System.out.println("NEEDS BEGIN = " + Arrays.toString(data.NEEDS_BEGIN));
        System.out.println("NEEDS END = " + Arrays.toString(data.NEEDS_END));
        System.out.println("NEEDS DELAY = " + Arrays.toString(data.NEEDS_RETURN_DELAY));
        System.out.println("NEEDS NB FORBIDDEN BEDS = "
                + Arrays.toString(Arrays.stream(data.NEEDS_FORBIDDEN_BEDS).mapToInt(v -> v.size()).toArray()));
        System.out.println("NB MAX BEDS = " + nbMaxBeds);

    }

    public String[] getReadableSolution(Solution solution) {
        int maxWeek = Arrays.stream(data.NEEDS_END).max().getAsInt();
        String[] sol = new String[nbMaxBeds];
        for (int i = 1; i < nbMaxBeds + 1; i++) {
            int finalI = i;
            Integer[] needs = IntStream.range(0, assignment.length)
                    .filter(v -> solution.getIntVal(assignment[v]) == finalI)
                    .mapToObj(v -> v)
                    .toArray(Integer[]::new);
            Arrays.sort(needs, (a, b) -> data.NEEDS_BEGIN[a] - data.NEEDS_BEGIN[b]);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("Planche " + i + ": ");
            for (int k = 0; k < needs.length; k++) {
                int crop = needs[k];
                if (k > 0) {
                    stringBuilder.append("--");
                }
                stringBuilder.append(" [" + data.SPECIES[data.NEEDS_SPECIES[crop]]);
                stringBuilder.append(": " + data.NEEDS_BEGIN[crop] + " -> " + data.NEEDS_END[crop] + "] ");
            }
            sol[i - 1] = stringBuilder.toString();
        }
        return sol;
    }

    public String[][] getCsvSolution(Solution solution) {
        int maxWeek = Arrays.stream(data.NEEDS_END).max().getAsInt();
        String[][] sol = new String[nbMaxBeds][];
        for (int i = 1; i < nbMaxBeds + 1; i++) {
            int finalI = i;
            int[] needs = IntStream.range(0, assignment.length)
                    .filter(v -> solution.getIntVal(assignment[v]) == finalI)
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
            sol[i - 1] = row;
        }
        return sol;
    }

    public class AgroecoplanException extends Exception {
        public AgroecoplanException(String msg) {
            super(msg);
        }
    }
}
