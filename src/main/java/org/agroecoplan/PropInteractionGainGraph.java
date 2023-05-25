package org.agroecoplan;

import org.chocosolver.solver.constraints.Propagator;
import org.chocosolver.solver.constraints.PropagatorPriority;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.variables.UndirectedGraphVar;
import org.chocosolver.solver.variables.Variable;
import org.chocosolver.solver.variables.delta.IGraphDeltaMonitor;
import org.chocosolver.solver.variables.events.GraphEventType;
import org.chocosolver.solver.variables.events.IntEventType;
import org.chocosolver.util.ESat;
import org.chocosolver.util.objects.setDataStructures.ISet;
import org.chocosolver.util.objects.setDataStructures.SetFactory;
import org.chocosolver.util.objects.setDataStructures.iterable.IntIterableRangeSet;
import org.chocosolver.util.procedure.PairProcedure;
import org.chocosolver.util.tools.ArrayUtils;

import java.util.List;

public class PropInteractionGainGraph extends Propagator<Variable> {

    private UndirectedGraphVar g;
    private IntVar[] assignments;
    private List<int[]> positivePairs;
    private ISet positive;
    private IGraphDeltaMonitor gdm;
    private PairProcedure edgeEnforced;

    public PropInteractionGainGraph(UndirectedGraphVar g, IntVar[] assignments, List<int[]> positivePairs, ISet[] intervalGraphWithRotations) {
        super(ArrayUtils.append(new Variable[] { g }, assignments), PropagatorPriority.LINEAR, true);
        this.g = g;
        this.assignments = assignments;
        this.positivePairs = positivePairs;
        this.positive = SetFactory.makeBipartiteSet(0);
        for (int[] p : positivePairs) {
            positive.add(p[0]);
            positive.add(p[1]);
        }
        this.gdm = g.monitorDelta(this);
        this.edgeEnforced = (a, b) -> {
            IntVar va = assignments[a];
            IntVar vb = assignments[b];
            IntIterableRangeSet valsA = new IntIterableRangeSet();
            IntIterableRangeSet remA = new IntIterableRangeSet();
            IntIterableRangeSet valsB = new IntIterableRangeSet();
            IntIterableRangeSet keepB = new IntIterableRangeSet();
            valsA.addAll(va);
            valsB.addAll(vb);
            for (int v : valsA) {
                boolean plus1 = valsB.contains(v + 1);
                boolean minus1 = valsB.contains(v - 1);
                if (plus1) {
                    keepB.add(v + 1);
                }
                if (minus1) {
                    keepB.add(v - 1);
                }
                if (!plus1 && !minus1) {
                    remA.add(v);
                }
            }
            va.removeValues(remA, this);
            vb.removeAllValuesBut(keepB, this);
        };
    }

    @Override
    public int getPropagationConditions(int vIdx) {
        if (vIdx == 0) {
            return GraphEventType.REMOVE_EDGE.getMask() + GraphEventType.ADD_EDGE.getMask();
        } else {
            return IntEventType.boundAndInst();
        }
    }

    @Override
    public void propagate(int idxVarInProp, int mask) throws ContradictionException {
        if (idxVarInProp > 0) {
            int a = idxVarInProp - 1;
            int[] neigh = g.getPotentialNeighborsOf(a).toArray();
            IntVar va = assignments[a];
            for (int b : neigh) {
                IntVar vb = assignments[b];
                // From int to graph
                if (IntervalUtils.minDistance(va, vb) > 1) {
                    g.removeEdge(a, b, this);
                } else if (IntervalUtils.maxDistance(va, vb) <= 1) {
                    g.enforceEdge(a, b, this);
                }
            }
        } else {
            // From graph to int
            gdm.forEachEdge(edgeEnforced, GraphEventType.ADD_EDGE);
        }
    }

    @Override
    public void propagate(int i) throws ContradictionException {
        for (int[] p : positivePairs) {
            int a = p[0];
            int b = p[1];
            IntVar va = assignments[a];
            IntVar vb = assignments[b];
            // From int to graph
            if (IntervalUtils.minDistance(va, vb) > 1) {
                g.removeEdge(a, b, this);
            } else if (IntervalUtils.maxDistance(va, vb) <= 1) {
                g.enforceEdge(a, b, this);
            }
            // From graph to int
            if (g.getMandatoryNeighborsOf(a).contains(b)) {
                IntIterableRangeSet valsA = new IntIterableRangeSet();
                IntIterableRangeSet remA = new IntIterableRangeSet();
                IntIterableRangeSet valsB = new IntIterableRangeSet();
                IntIterableRangeSet keepB = new IntIterableRangeSet();
                valsA.addAll(va);
                valsB.addAll(vb);
                for (int v : valsA) {
                    boolean plus1 = valsB.contains(v + 1);
                    boolean minus1 = valsB.contains(v - 1);
                    if (plus1) {
                        keepB.add(v + 1);
                    }
                    if (minus1) {
                        keepB.add(v - 1);
                    }
                    if (!plus1 && !minus1) {
                        remA.add(v);
                    }
                }
                va.removeValues(remA, this);
                vb.removeAllValuesBut(keepB, this);
            } else if (!g.getPotentialNeighborsOf(a).contains(b)) {
                // TODO if necessary
            }
        }
        gdm.startMonitoring();
    }

    @Override
    public ESat isEntailed() {
        if (isCompletelyInstantiated()) {
            for (int[] p : positivePairs) {
                int a = p[0];
                int b = p[1];
                IntVar va = assignments[a];
                IntVar vb = assignments[b];
                int d = Math.abs(va.getValue() - vb.getValue());
                if (d == 1 && !g.getValue().containsEdge(a, b)) {
                    return ESat.FALSE;
                } else if (d > 1 && g.getValue().containsEdge(a, b)) {
                    return ESat.FALSE;
                }
            }
            return ESat.TRUE;
        }
        return ESat.UNDEFINED;
    }
}
