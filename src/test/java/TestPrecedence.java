import com.opencsv.exceptions.CsvException;
import org.agroecoplan.AgroEcoPlanProblem;
import org.agroecoplan.Data;
import org.chocosolver.solver.Solution;
import org.chocosolver.solver.variables.IntVar;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Arrays;

public class TestPrecedence {

    @Test
    public void testPrecedence() throws IOException, CsvException {
        String needs = getClass().getClassLoader().getResource("testPrecedence/besoins_mini_instance.csv").getPath();
        String beds = getClass().getClassLoader().getResource("testPrecedence/donneesplanches_mini_instance.csv").getPath();
        String interactions = getClass().getClassLoader().getResource("testPrecedence/interactions.csv").getPath();
        String precedences = getClass().getClassLoader().getResource("testPrecedence/interactions_temporelles_precedence_mini.csv").getPath();

        AgroEcoPlanProblem pb = new AgroEcoPlanProblem(
                new Data(needs, interactions, beds, precedences), true, true
        );

        pb.postForbidNegativePrecedence();
        IntVar pos = pb.getNumberOfPositivePrecedences();

        pb.getModel().getSolver().showStatistics();
        pb.getModel().getSolver().limitTime("30s");

        Solution sol = pb.getModel().getSolver().findOptimalSolution(pos, true);
        System.out.println("Nb positive precedences = " + sol.getIntVal(pos));
        String[][] csvSol = pb.getCsvSolution(sol);
        for (String[] s : csvSol) {
            System.out.println(Arrays.toString(s));
        }
    }
}
