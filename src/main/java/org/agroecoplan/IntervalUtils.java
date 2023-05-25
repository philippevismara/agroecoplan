package org.agroecoplan;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.variables.IntVar;

import java.util.Arrays;

public class IntervalUtils {

    /**
     * Return true iff the intervals [s1, e1] and [s2, e2] intersect.
     * @param s1 start of interval 1
     * @param e1 end of interval 1
     * @param s2 start of interval 2
     * @param e2 end of interval 2
     * @return true iff intervals 1 and 2 intersect
     */
    public static boolean intersect(int s1, int e1, int s2, int e2) {
        return !(s2 > e1 || s1 > e2);
        //return (s2 >= s1 && s2 <= e1) || (e2 >= s1 && e2 <= e1) || (s1 >= s2 && s1 <= e2) || (e1 >= s2 && e1 <= e2);
    }

    public static int[] rangeIntersection(int s1, int e1, int s2, int e2) {
        if (!IntervalUtils.intersect(s1, e1, s2, e2)) {
            return null;
        }
        return new int[] {Math.max(s1, s2), Math.min(e1, e2)};
    }

    public static int[] rangeIntersection(IntVar v1, IntVar v2) {
        int s1 = v1.getLB();
        int e1 = v1.getUB();
        int s2 = v2.getLB();
        int e2 = v2.getUB();
        return rangeIntersection(s1, e1, s2, e2);
    }

    /**
     * Return the minimum distance between two IntVar domains. If the domains intersect, the distance is 0;
     * If they do not, it is the distance between the smallest upper bound and the largest lower bound.
     * /!\ only checked for bounds.
     * @param v1 IntVar
     * @param v2 IntVar
     * @return The minimum distance betwen v1 and v2.
     */
    public static int minDistance(IntVar v1, IntVar v2) {
        int s1 = v1.getLB();
        int e1 = v1.getUB();
        int s2 = v2.getLB();
        int e2 = v2.getUB();
        if (intersect(s1, e1, s2, e2)) {
            return 0;
        }
        return Math.max(s1, s2) - Math.min(e1, e2);
    }

    public static int maxDistance(IntVar v1, IntVar v2) {
        int d1 = Math.abs(v2.getUB() - v1.getLB());
        int d2 = Math.abs(v1.getUB() - v2.getLB());
        return Math.max(d1, d2);
    }
}
