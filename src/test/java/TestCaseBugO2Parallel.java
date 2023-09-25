import com.opencsv.exceptions.CsvException;

import org.agroecoplan.AgroEcoPlanProblem;
import org.agroecoplan.Data;
import org.agroecoplan.Main;
import org.testng.annotations.Test;
import picocli.CommandLine;

import java.io.IOException;

public class TestCaseBugO2Parallel {

    @Test
    public void testCaseBugO2Parallel() throws IOException, CsvException, AgroEcoPlanProblem.AgroecoplanException {
        String needs = getClass().getClassLoader().getResource("testPrecedence/besoins_mini_instance.csv").getPath();
        String beds = getClass().getClassLoader().getResource("testPrecedence/donneesplanches_mini_instance.csv").getPath();
        String interactions = getClass().getClassLoader().getResource("testPrecedence/interactions.csv").getPath();
        String precedences = getClass().getClassLoader().getResource("testPrecedence/interactions_temporelles_precedence_mini.csv").getPath();
        String delays = getClass().getClassLoader().getResource("testPrecedence/interactions_temporelles_delais_mini.csv").getPath();
        String[] args = new String[] {"-p", "-c", "2", "-cst", "C1,C2,C4,C6", "-opt", "O2", "-t", "-30s", needs, beds, interactions, precedences, delays, "null"};
        Main main = new Main();
        new CommandLine(main).parseArgs(args);
        main.run();
    }
}
