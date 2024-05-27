
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import java.io.File;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.testing.MojoRule;
import org.junit.Rule;
import org.junit.Test;

public class ValidateMojoTest {
	@Rule
	public MojoRule rule = new MojoRule() {
		@Override
		protected void before() {
		}

		@Override
		protected void after() {
		}
	};
	@Test
	public void validatorTest() throws Exception {
		File pom = new File("target/test-classes/project-to-test/");
		assertNotNull(pom);
		assertTrue(pom.exists());

		ValidateMojo validateMojo = (ValidateMojo) rule.lookupConfiguredMojo(pom, "validate");
		assertNotNull(validateMojo);
		assertThrows(MojoFailureException.class, validateMojo::execute);

		File checkFolder = (File) rule.getVariableValueFromObject(validateMojo, "checkFolder");
		assertNotNull(checkFolder);
		assertTrue(checkFolder.exists());

		boolean breakOnError = (boolean) rule.getVariableValueFromObject(validateMojo, "breakOnError");
		assertTrue(breakOnError);

	}

	@Test
	public void validatorTestNegative() throws Exception {
		File pom = new File("target/test-classes/project-to-test_neg/");
		assertNotNull(pom);

		ValidateMojo validateMojo = (ValidateMojo) rule.lookupConfiguredMojo(pom, "validate");
		assertNotNull(validateMojo);
		validateMojo.execute();

		File checkFolder = (File) rule.getVariableValueFromObject(validateMojo, "checkFolder");
		assertNotNull(checkFolder);
		assertTrue(checkFolder.exists());

		boolean breakOnError = (boolean) rule.getVariableValueFromObject(validateMojo, "breakOnError");
		assertTrue(breakOnError);

	}
}
