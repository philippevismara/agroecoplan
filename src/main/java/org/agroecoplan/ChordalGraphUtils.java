package org.agroecoplan;

import java.util.ArrayList;
import java.util.List;

import org.chocosolver.util.objects.setDataStructures.ISet;
import org.chocosolver.util.objects.setDataStructures.SetFactory;

public class ChordalGraphUtils {

    /**
     * Compute a perfect elimination ordering from the adjacency list of a chordal graph.
     * Use a maximum cardinality search (see Tarjan & Yannakakis 1984 https://doi.org/10.1137/0213035).
     * @param adjacencyList
     * @return
     */
    public static int[] perfectEliminationOrdering(int[][] adjacencyList) {
        int N = adjacencyList.length;
        ISet unordered = SetFactory.makeBipartiteSet(0);
        int[] weight = new int[N];
        for (int i = 0; i < N; i++) {
            unordered.add(i);
            weight[i] = 0;
        }
        int[] perfectOrdering = new int[N];
        for (int i = N - 1; i >= 0; i--) {
            int v = getMaxCardVertex(unordered, weight);
            for (int u : adjacencyList[v]) {
                if (unordered.contains(u)) {
                    weight[u] += 1;
                }
            }
            unordered.remove(v);
            perfectOrdering[i] = v;
        }
        return perfectOrdering;
    }

    private static int getMaxCardVertex(ISet unordered, int[] weights) {
        int maxWeight = -1;
        int v = -1;
        for (int i : unordered) {
            if (weights[i] > maxWeight) {
                maxWeight = weights[i];
                v = i;
            }
        }
        return v;
    }

    /**
     * Find maximal cliques of a chordal graph.
     * Based on Minseps-Maxcliques algorithm with a Maximal Cardinality Search
     * Tarjan & Yannakakis (1984), Berry & Pogorelcnic (2011).
     * @param adjacencyList
     * @return
     */
    public static List<ISet> findMaximalCliques(int[][] adjacencyList) {
        int n = adjacencyList.length;
        List<ISet> maximalCliques = new ArrayList<>();
        List<ISet> minSep = new ArrayList<>();
        ISet minSepGenerators = SetFactory.makeBipartiteSet(0);
        ISet maxCliquesGenerators = SetFactory.makeBipartiteSet(0);
        int[] peo = new int[n];
        ISet elim = SetFactory.makeBipartiteSet(0);
        ISet num = SetFactory.makeBipartiteSet(0);
        int[] label = new int[n];
        for (int i = 0; i < n; i++) {
            elim.add(i);
            label[i] = 0;
        }
        int lambda = 0;
        for (int i = n - 1; i >= 0; i--) {
            int x = getMaxCardVertex(elim, label);
            peo[i] = x;
            if (i != n - 1 && label[x] <= lambda) {
                minSepGenerators.add(x);
                ISet sep = SetFactory.makeBipartiteSet(0);
                for (int j : adjacencyList[x]) {
                    if (num.contains(j)) {
                        sep.add(j);
                    }
                }
                minSep.add(sep);
                maxCliquesGenerators.add(peo[i + 1]);
                ISet maxClique = SetFactory.makeBipartiteSet(0);
                maxClique.add(peo[i + 1]);
                for (int j : adjacencyList[peo[i + 1]]) {
                    if (num.contains(j)) {
                        maxClique.add(j);
                    }
                }
                maximalCliques.add(maxClique);
            }
            lambda = label[x];
            for (int j : adjacencyList[x]) {
                if (elim.contains(j)) {
                    label[j] += 1;
                }
            }
            num.add(x);
            elim.remove(x);
        }
        maxCliquesGenerators.add(peo[0]);
        ISet maxClique = SetFactory.makeBipartiteSet(0);
        maxClique.add(peo[0]);
        for (int j : adjacencyList[peo[0]]) {
            maxClique.add(j);
        }
        maximalCliques.add(maxClique);
        return maximalCliques;
    }

    /**
     * Test whether a graph is chordal or not.
     * Uses the zero fill-in detection algorithm described in https://doi.org/10.1137/0213035
     * (Tarjan & Yannakakis, 1984).
     * @param adjacencyList
     * @return
     */
    public static boolean isChordal(int[][] adjacencyList) {
        int[] peo = perfectEliminationOrdering(adjacencyList);
        int n = peo.length;
        int[] f = new int[n];
        int[] index = new int[n];
        for (int i = 0; i < n; i++) {
            int w = peo[i];
            f[w] = w;
            index[w] = i;
            ISet neigh = SetFactory.makeConstantSet(adjacencyList[w]);
            for (int j = 0; j < i; j++) {
                int v = peo[j];
                if (neigh.contains(v)) {
                    index[v] = i;
                    if (f[v] == v) {
                        f[v] = w;
                    }
                }
            }
            for (int j = 0; j < i; j++) {
                int v = peo[j];
                if (neigh.contains(v)) {
                    if (index[f[v]] < i) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public static List<ISet> getCliques(int[][] adjacencyList) {
        int[] peo = perfectEliminationOrdering(adjacencyList);
        List<ISet> cliques = new ArrayList<>();
        ISet[] neighbors = new ISet[peo.length];
        for (int i = 0; i < peo.length; i++) {
            neighbors[i] = SetFactory.makeConstantSet(adjacencyList[i]);
        }
        for (int i = 0; i < peo.length; i++) {
            ISet clique = SetFactory.makeBipartiteSet(0);
            clique.add(peo[i]);
            for (int j = i + 1; j < peo.length; j++) {
                if (neighbors[peo[i]].contains(peo[j])) {
                    clique.add(peo[j]);
                }
            }
            if (clique.size() >= 2) {
                if (isMaximalClique(clique, adjacencyList)) {
                    cliques.add(clique);
                }
            }
        }
        return cliques;
    }

    public static boolean isMaximalClique(ISet clique, int[][] adjacencyList) {
        for (int node : clique) {
            for (int i : adjacencyList[node]) {
                if (!clique.contains(i)) {
                    boolean b = true;
                    for (int j : clique) {
                        ISet neigh = SetFactory.makeConstantSet(adjacencyList[j]);
                        b &= neigh.contains(i);
                    }
                    if (b) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
