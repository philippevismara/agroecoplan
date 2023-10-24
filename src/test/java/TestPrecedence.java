import com.opencsv.exceptions.CsvException;
import org.agroecoplan.AgroEcoPlanProblem;
import org.agroecoplan.Data;
import org.chocosolver.solver.Solution;
import org.testng.annotations.Test;

import java.io.IOException;

public class TestPrecedence {

    @Test
    public void testPrecedence() throws IOException, CsvException, AgroEcoPlanProblem.AgroecoplanException {
        String needs = getClass().getClassLoader().getResource("testPrecedence/besoins_mini_instance.csv").getPath();
        String beds = getClass().getClassLoader().getResource("testPrecedence/donneesplanches_mini_instance.csv").getPath();
        String interactions = getClass().getClassLoader().getResource("testPrecedence/interactions.csv").getPath();
        String precedences = getClass().getClassLoader().getResource("testPrecedence/interactions_temporelles_precedence_mini.csv").getPath();
        String delays = getClass().getClassLoader().getResource("testPrecedence/interactions_temporelles_delais_mini.csv").getPath();

        AgroEcoPlanProblem pb = new AgroEcoPlanProblem(
                new Data(needs, interactions, beds, precedences, delays), true, false
        );

        pb.postForbidNegativePrecedencesConstraint();
        pb.initNumberOfPositivePrecedencesCountBased();

        pb.getModel().getSolver().limitTime("30s");

        Solution sol = pb.getModel().getSolver().findOptimalSolution(pb.getGain(), true);
        System.out.println("Nb positive precedences = " + sol.getIntVal(pb.getGain()));
        String[] printSol = pb.getReadableSolution(sol);
        for (String s : printSol) {
            System.out.println(s);
        }
    }
}
