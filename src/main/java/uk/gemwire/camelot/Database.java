package uk.gemwire.camelot;

import org.flywaydb.core.Flyway;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteDataSource;
import uk.gemwire.camelot.configuration.Common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The class where the bot databases are stored.
 */
public class Database {
    public static final Logger LOGGER = LoggerFactory.getLogger(Common.NAME + " database");

    /**
     * Static JDBI main instance. Can be accessed via {@link #main()}.
     */
    public static Jdbi main;

    /**
     * {@return the static main JDBI instance}
     */
    public static Jdbi main() {
        return main;
    }

    /**
     * Initialises the databases.
     */
    static void init() throws IOException {
        final Path dir = Path.of("data");
        final Path mainDb = dir.resolve("main.db");

        {
            final Path oldDb = Path.of("data.db");
            if (Files.exists(oldDb)) {
                Files.copy(oldDb, mainDb);
                Files.delete(oldDb);
            }
        }

        main = createDatabaseConnection(mainDb, "main");
    }

    /**
     * Sets up a connection to the SQLite database located at the {@code dbPath}, migrating it, if necessary.
     *
     * @return a JDBI connection to the database
     */
    public static Jdbi createDatabaseConnection(Path dbPath, String flywayLocation) {
        dbPath = dbPath.toAbsolutePath();
        if (!Files.exists(dbPath)) {
            try {
                Files.createFile(dbPath);
            } catch (IOException e) {
                throw new RuntimeException("Exception creating database!", e);
            }
        }
        final String url = "jdbc:sqlite:" + dbPath;
        final SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl(url);
        dataSource.setEncoding("UTF-8");
        dataSource.setDatabaseName("Camelot DB");
        dataSource.setEnforceForeignKeys(true);
        dataSource.setCaseSensitiveLike(false);
        LOGGER.info("Initiating SQLite database connection at {}.", url);

        final var flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/" + flywayLocation)
                .load();
        flyway.migrate();

        return Jdbi.create(dataSource)
                .installPlugin(new SqlObjectPlugin());
    }

}
