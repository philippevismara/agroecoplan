package org.agroecoplan;

import org.chocosolver.solver.constraints.Propagator;
import org.chocosolver.solver.constraints.PropagatorPriority;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.util.ESat;
import org.chocosolver.util.iterators.DisposableValueIterator;
import org.chocosolver.util.objects.setDataStructures.iterable.IntIterableRangeSet;
import org.chocosolver.util.tools.ArrayUtils;

public class PropInteractionGain extends Propagator<IntVar> {

    private IntVar gain;
    private IntVar[][] positiveAssignments;

    public PropInteractionGain(IntVar gain, IntVar[][] positiveAssignments) {
        super(ArrayUtils.append(new IntVar[] { gain }, ArrayUtils.flatten(positiveAssignments)), PropagatorPriority.LINEAR, false);
        this.gain = gain;
        this.positiveAssignments = positiveAssignments;
    }

    @Override
    public void propagate(int i) throws ContradictionException {
        int[] bounds = getBounds();
        gain.updateBounds(bounds[0], bounds[1], this);
        if (gain.getLB() > bounds[0] && gain.getLB() == bounds[1]) {
            for (IntVar[] pair : positiveAssignments) {
                IntVar p1 = pair[0];
                IntVar p2 = pair[1];
                if (IntervalUtils.minDistance(p1, p2) <= 1) {
                    IntVar x;
                    IntVar y;
                    if (p1.getDomainSize() <= p2.getDomainSize()) {
                        x = p1;
                        y = p2;
                    } else {
                        x = p2;
                        y = p1;
                    }
                    IntIterableRangeSet remX = new IntIterableRangeSet();
                    IntIterableRangeSet keepY = new IntIterableRangeSet();
                    DisposableValueIterator vit = x.getValueIterator(true);
                    while (vit.hasNext()) {
                        int v = vit.next();
                        boolean plus1 = y.contains(v + 1);
                        boolean minus1 = y.contains(v - 1);
                        if (plus1) {
                            keepY.add(v + 1);
                        }
                        if (minus1) {
                            keepY.add(v - 1);
                        }
                        if (!plus1 && !minus1) {
                            remX.add(v);
                        }
                    }
                    vit.dispose();
                    x.removeValues(remX, this);
                    y.removeAllValuesBut(keepY, this);
                }
            }
            bounds = getBounds();
            gain.updateBounds(bounds[0], bounds[1], this);
        }
    }

    @Override
    public ESat isEntailed() {
        int lb = 0;
        for (IntVar[] pair : positiveAssignments) {
            IntVar p1 = pair[0];
            IntVar p2 = pair[1];
            if (p1.isInstantiated() && p2.isInstantiated()) {
                if (Math.abs(p1.getValue() - p2.getValue()) == 1) {
                    lb++;
                }
            }
        }
        if (isCompletelyInstantiated()) {
            if (gain.getValue() == lb) {
                return ESat.TRUE;
            } else {
                return ESat.FALSE;
            }
        } else {
            return ESat.UNDEFINED;
        }
    }

    private int[] getBounds() {
        int lb = 0;
        int ub = 0;
        for (IntVar[] pair : positiveAssignments) {
            IntVar p1 = pair[0];
            IntVar p2 = pair[1];
            // LB: The pair is guaranteed to be included iff the maximum possible distance between
            //      assignments is <= 1.
            if (IntervalUtils.maxDistance(p1, p2) <= 1) {
                lb++;
                ub++;
            } else if (IntervalUtils.minDistance(p1, p2) <= 1) {
                // UB: A necessary condition is that the minimum achievable distance between assignments is <= 1.
                // If so, further conditions must be tested, as the domains are enumerated.
                // 1- If the domains are not intersecting, the distance is necessarily between extremal possible
                //    values, i.e. domain bounds. So if domains do not intersect, increment upper bound.
                if (IntervalUtils.rangeIntersection(p1, p2) == null) {
                    ub++;
                } else {
                    // 2- If the domains intersect, a sufficient condition is that, given one of the two variables
                    //    (for efficiency we chose the one with the smallest domain) x, it exists a value v from the domain
                    //    of x such that y has either v + 1 or v - 1 in its domain.
                    IntVar x;
                    IntVar y;
                    if (p1.getDomainSize() <= p2.getDomainSize()) {
                        x = p1;
                        y = p2;
                    } else {
                        x = p2;
                        y = p1;
                    }
                    DisposableValueIterator vit = x.getValueIterator(true);
                    while (vit.hasNext()) {
                        int v = vit.next();
                        if (y.contains(v + 1) || y.contains(v - 1)) {
                            ub++;
                            break;
                        }
                    }
                    vit.dispose();
                }
            }
        }
        return new int[] {lb, ub};
    }
}
