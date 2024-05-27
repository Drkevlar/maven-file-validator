

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.maven.plugins.annotations.LifecyclePhase.VALIDATE;
import static ru.sber.papyrus.commons.plugins.model.Constants.CONFIG_FILE;
import static ru.sber.papyrus.commons.plugins.model.Constants.ERROR_LEVEL;
import static ru.sber.papyrus.commons.plugins.model.Constants.FILE_ERROR;
import static ru.sber.papyrus.commons.plugins.model.Constants.SCHEMA_EXT;
import static ru.sber.papyrus.commons.plugins.model.Constants.WARN_LEVEL;
import static ru.sber.papyrus.commons.plugins.model.Constants.YAML_EXT;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.sbaudoin.yamllint.LintProblem;
import com.github.sbaudoin.yamllint.Linter;
import com.github.sbaudoin.yamllint.YamlLintConfig;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;


@Mojo(name = "validate", defaultPhase = VALIDATE, threadSafe = true)
public class ValidateMojo extends AbstractMojo {

	/**
	 * Поле listOfYamlFiles представляет собой список файлов типа File. Инициализируется пустым списком ArrayList. В данном списке хранятся
	 * файлы YAML.
	 */
	private final List<File> listOfYamlFiles = new ArrayList<>();

	/**
	 * Поле checkFolder представляет собой файл типа File. Используется в качестве параметра для задания базовой папки проекта. Значение по
	 * умолчанию берется из свойства "project.basedir". Обязательный параметр, должен быть указан.
	 */
	@Parameter(defaultValue = "${project.basedir}", property = "checkFolder", required = true)
	private File checkFolder;

	/**
	 * Поле breakOnError представляет собой флаг, указывающий на необходимость прервать выполнение в случае ошибки.
	 * По умолчанию установлено значение "false".
	 */
	@Parameter(defaultValue = "false", property = "breakOnError")
	private boolean breakOnError;

	/**
	 * Метод execute выполняет проверку файлов в указанной папке на соответствие синтаксису и схеме.
	 * Читает конфигурацию из файла "StandardRules.yml" и проверяет каждый файл в папке на валидность.
	 * Если папка не существует, выводит предупреждение.
	 * По завершении проверки выводит сообщение о завершении.
	 * Если установлен флаг breakOnError и конфигурация невалидна, генерирует исключение.
	 * В противном случае выводит сообщение о валидности конфигурации.
	 *
	 * @throws MojoFailureException если возникает ошибка в процессе выполнения метода
	 */
	@Override
	public void execute() throws MojoFailureException {
		Path pathToCheck = checkFolder.toPath().resolve("src");

		getLog().info(String.format("Start validating in %s", pathToCheck));

		if (!pathToCheck.toFile().exists()) {
			getLog().info("Folder not exist");
			return;
		}

		try {
			String ymlConfig = new String(Objects.requireNonNull(getClass().getResourceAsStream(CONFIG_FILE)).readAllBytes(), UTF_8);

			for (final File fileToCheck : getFileList(pathToCheck.toFile())) {
				getLog().info(String.format("Checking file: %s", fileToCheck.getCanonicalPath()));
				validateSyntax(fileToCheck, ymlConfig);
				validateSchema(fileToCheck);
			}
			getLog().info("Checking done");
		} catch (IOException e) {
			ValidateException.of(e);
		}
		if (breakOnError && !Flags.isValid()) {
			throw new MojoFailureException("Configuration is not valid");
		}
		getLog().info("Configuration is valid");
	}

	/**
	 * Метод validateSyntax выполняет проверку синтаксиса файла с использованием заданной конфигурации.
	 * Читает содержимое файла и запускает линтер для проверки синтаксиса.
	 * Если обнаружены проблемы, выводит сообщения об ошибках, предупреждениях или информации.
	 * При обнаружении ошибки помечает конфигурацию как невалидную.
	 *
	 * @param fileToCheck файл, который необходимо проверить на синтаксис
	 * @param ymlConfig конфигурация для использования при проверке синтаксиса
	 */

	private void validateSyntax(final File fileToCheck, final String ymlConfig) throws IOException {
		try (InputStream targetStream = new FileInputStream(fileToCheck)) {
			getLog().debug(ymlConfig);
			List<LintProblem> problems = Linter.run(targetStream, new YamlLintConfig(ymlConfig));

			if (!problems.isEmpty()) {
				for (LintProblem problem : problems) {
					String level = problem.getLevel();
					if (ERROR_LEVEL.equals(level)) {
						getLog().error(String.format(FILE_ERROR, fileToCheck.getCanonicalPath()));
						getLog().error(problem.toString());
						Flags.notValid();
					} else if (WARN_LEVEL.equals(level)) {
						getLog().warn(problem.toString());
					} else {
						getLog().debug(problem.toString());
					}
				}
			}
		} catch (final Exception e) {
			Flags.notValid();
			getLog().error(String.format(FILE_ERROR, fileToCheck.getCanonicalPath()));
			getLog().error(e.getMessage());
		}
	}

	/**
	 * Метод validateSchema выполняет проверку схемы файла.
	 * Создает файл схемы на основе файла для проверки.
	 * Если файл схемы не найден, выводит предупреждение.
	 * Затем загружает схему и выполняет проверку файла по этой схеме.
	 * При возникновении исключения ValidationException помечает конфигурацию как невалидную
	 * и выводит сообщения об ошибках валидации.
	 *
	 * @param fileToCheck файл, для которого необходимо проверить схему
	 */
	private void validateSchema(final File fileToCheck) throws IOException {
		try {
			File schemaFile = new File(fileToCheck.getCanonicalPath().concat(SCHEMA_EXT));

			if (!schemaFile.exists()) {
				getLog().info(String.format("Schema for file '%s' not found", fileToCheck.getCanonicalPath()));
				return;
			}

			String schemaString = getJsonStringFromFile(schemaFile);
			String json = getJsonStringFromFile(fileToCheck);
			getLog().debug(String.format("Json string: %s", json));
			getLog().debug(String.format("Schema string: %s", schemaString));
			JSONObject rawSchema = new JSONObject(schemaString);
			Schema schema = SchemaLoader.load(rawSchema);
			schema.validate(new JSONObject(json));
		} catch (final ValidationException e) {
			Flags.notValid();
			getLog().error(String.format(FILE_ERROR, fileToCheck.getCanonicalPath()));
			printErrors(e);
		}
	}

	/**
	 * Метод для вывода ошибок валидации.
	 * Перебирает все исключения в объекте ValidationException и выводит их сообщения об ошибке.
	 * Если вложенное исключение также содержит другие исключения, метод вызывает сам себя для их обработки.
	 */
	private void printErrors(final ValidationException e) {
		Optional.of(e.getCausingExceptions())
				.filter(f -> !f.isEmpty())
				.ifPresentOrElse(f -> f.forEach(this::printErrors),
								() -> getLog().error(e.getMessage()));
	}

	/**
	 * Метод для получения списка файлов из указанной директории и всех ее поддиректорий.
	 *
	 * @param dir
	 * 		директория, из которой необходимо получить список файлов
	 *
	 * @return список файлов в формате List<File>
	 */
	private List<File> getFileList(final File dir) throws IOException {
		if (dir != null) {
			getLog().debug(dir.getCanonicalPath());
			for (final File file : Objects.requireNonNull(dir.listFiles())) {
				if (file.isDirectory()) {
					getFileList(file);
				} else {
					if (file.getName().endsWith(YAML_EXT)) {
						getLog().debug(String.format("Founded yaml file: %s", file.getCanonicalPath()));
						listOfYamlFiles.add(file);
					} else {
						getLog().debug(String.format("Skipped file: %s", file.getCanonicalPath()));
					}
				}
			}
		}
		return listOfYamlFiles;
	}

	/**
	 * Метод получает содержимое файла в формате YAML и преобразует его в JSON-строку.
	 *
	 * @param inFile
	 * 		Файл, содержащий данные в формате YAML.
	 *
	 * @return JSON-строка, содержащая данные из файла в формате JSON. Если произошла ошибка в процессе чтения файла или преобразования
	 * формата, метод возвращает пустую строку.
	 */
	private String getJsonStringFromFile(final File inFile) throws IOException {
		String yamlString = Files.readString(inFile.toPath());
		ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());

		Object obj = yamlReader.readValue(yamlString, Object.class);
		ObjectMapper jsonWriter = new ObjectMapper();
		return jsonWriter.writeValueAsString(obj);
	}
}
